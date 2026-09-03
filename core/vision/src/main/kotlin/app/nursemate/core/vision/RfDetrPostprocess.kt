package app.nursemate.core.vision

import kotlin.math.exp

/**
 * 후처리 — raw 출력 → [Detection] 목록, 그리고 마스크 해석.
 *
 * 정본(iOS `RFDetrSegmentor.run`)과 맞춘 규칙. 하나라도 어기면 iOS와 결과가 달라진다:
 *  - score = `sigmoid(logits[q*2])`. **softmax·argmax 아님**, `logits[..,1]`은 쓰지 않는다
 *  - **NMS 없음.** DETR 헝가리안 매칭이 중복을 억제하는 전제라 NMS를 넣으면 결과가 바뀐다
 *  - 정렬은 **box.minX 오름차순** (score 순 아님). UI 번호가 이 순서를 따른다
 *  - box는 cxcywh 정규화 → x1y1x2y2, **y-flip 없음** (전처리에서 뒤집지 않았으므로)
 */
object RfDetrPostprocess {

    /** iOS와 동일하게 [-88, 88]로 클램프한 뒤 계산한다. */
    fun sigmoid(x: Float): Float = 1f / (1f + exp(-x.coerceIn(-88f, 88f)))

    /**
     * @param logits `[1,100,2]` 평탄화
     * @param boxes  `[1,100,4]` 평탄화 (cx, cy, w, h 정규화)
     */
    fun decode(logits: FloatArray, boxes: FloatArray, threshold: Float = RfDetrSpec.SCORE_THRESHOLD): List<Detection> {
        val n = RfDetrSpec.QUERY_COUNT
        require(logits.size >= n * 2) { "logits 크기 부족: ${logits.size}" }
        require(boxes.size >= n * 4) { "boxes 크기 부족: ${boxes.size}" }

        val out = ArrayList<Detection>()
        for (q in 0 until n) {
            val score = sigmoid(logits[q * 2])
            if (score < threshold) continue

            val cx = boxes[q * 4]
            val cy = boxes[q * 4 + 1]
            val w = boxes[q * 4 + 2]
            val h = boxes[q * 4 + 3]
            val x1 = (cx - w / 2f).coerceIn(0f, 1f)
            val x2 = (cx + w / 2f).coerceIn(0f, 1f)
            // iOS는 여기서 1-(cy±h/2) 로 뒤집는다. 입력 미러링을 되돌리는 보정이므로 우리는 하지 않는다.
            val y1 = (cy - h / 2f).coerceIn(0f, 1f)
            val y2 = (cy + h / 2f).coerceIn(0f, 1f)

            out += Detection(
                query = q,
                score = score,
                x = x1,
                y = y1,
                width = maxOf(0f, x2 - x1),
                height = maxOf(0f, y2 - y1)
            )
        }
        out.sortBy { it.x }
        return out
    }

    /**
     * 마스크(logit > 0)의 외접 범위를 구한다. 마스크 격자 인덱스 기준 `[x0, y0, x1, y1]` **포함** 범위.
     *
     * ⚠️ 크롭은 `pred_boxes`가 아니라 **마스크에서** 잘라야 한다(iOS `cropWithMask`).
     *    `pred_boxes`로 자르면 iOS와 크롭 크기가 달라진다.
     *
     * @param mask 쿼리 하나의 `[144,144]` 마스크 로짓 (평탄화)
     * @return 마스크가 비어 있으면 null
     */
    fun maskBounds(mask: FloatArray): IntArray? {
        val ms = RfDetrSpec.MASK_SIZE
        require(mask.size >= ms * ms) { "mask 크기 부족: ${mask.size}" }

        var x0 = ms
        var x1 = -1
        var y0 = ms
        var y1 = -1
        for (my in 0 until ms) {
            val extent = rowExtent(mask, my * ms, ms)
            if (extent < 0) continue

            val first = extent ushr SHIFT
            val last = extent and MASK_LOW
            if (first < x0) x0 = first
            if (last > x1) x1 = last
            // iOS는 dy = ms-1-my 로 뒤집는다. 우리는 뒤집지 않는다.
            if (my < y0) y0 = my
            if (my > y1) y1 = my
        }
        return if (x1 >= x0 && y1 >= y0) intArrayOf(x0, y0, x1, y1) else null
    }

    /**
     * 한 행에서 마스크가 켜진 **첫·마지막 열**을 Int 하나에 담는다(`(첫 shl 16) or 마지막`).
     * 켜진 칸이 없으면 -1.
     *
     * Pair 로 돌려주면 행마다 객체가 하나씩 생긴다 — 쿼리당 144행이라 검출 한 번에 수백 개다.
     * 마스크 격자가 144라 16비트에 넉넉히 들어간다.
     */
    private fun rowExtent(mask: FloatArray, row: Int, ms: Int): Int {
        var first = -1
        var last = -1
        for (mx in 0 until ms) {
            if (mask[row + mx] > 0f) {
                if (first < 0) first = mx
                last = mx
            }
        }
        return if (first < 0) -1 else (first shl SHIFT) or last
    }

    /**
     * 정규화 좌표 `(u, v)` 위치의 마스크 확률.
     *
     * ⚠️ **로짓을 먼저 보간하고 그다음 sigmoid를 적용한다.** 순서를 바꾸면(확률을 보간하면)
     * 경계값이 달라진다. iOS·Python 파이프라인 모두 이 순서다.
     *
     * 격자 매핑은 `align_corners = false` 규약이다 — 픽셀 중심이 셀 중심에 오도록
     * `u * MASK_SIZE - 0.5` 로 옮긴다.
     */
    fun maskProbabilityAt(mask: FloatArray, u: Float, v: Float): Float =
        sigmoid(sampleLogit(mask, u * RfDetrSpec.MASK_SIZE - 0.5f, v * RfDetrSpec.MASK_SIZE - 0.5f))

    /** 마스크 격자 좌표 `(mxf, myf)`에서 로짓을 이중선형 보간한다. 격자 밖은 가장자리 값으로 확장. */
    private fun sampleLogit(mask: FloatArray, mxf: Float, myf: Float): Float {
        val ms = RfDetrSpec.MASK_SIZE
        val last = ms - 1

        val cx = mxf.coerceIn(0f, last.toFloat())
        val cy = myf.coerceIn(0f, last.toFloat())
        val x0 = cx.toInt()
        val y0 = cy.toInt()
        val x1 = minOf(x0 + 1, last)
        val y1 = minOf(y0 + 1, last)
        val fx = cx - x0
        val fy = cy - y0

        val top = mask[y0 * ms + x0] * (1f - fx) + mask[y0 * ms + x1] * fx
        val bottom = mask[y1 * ms + x0] * (1f - fx) + mask[y1 * ms + x1] * fx
        return top * (1f - fy) + bottom * fy
    }

    /** [rowExtent] 가 첫·마지막 열을 하나의 Int 에 담을 때 쓰는 비트 폭. */
    private const val SHIFT = 16
    private const val MASK_LOW = 0xFFFF
}
