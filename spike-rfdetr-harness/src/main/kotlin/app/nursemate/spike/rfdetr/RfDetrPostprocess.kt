package app.nursemate.spike.rfdetr

import kotlin.math.exp

/**
 * 후처리 — raw 출력 → [Detection] 목록.
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
     * @param logits [1,100,2] 평탄화
     * @param boxes  [1,100,4] 평탄화 (cx, cy, w, h 정규화)
     */
    fun decode(
        logits: FloatArray,
        boxes: FloatArray,
        threshold: Float = RfDetrSpec.SCORE_THRESHOLD,
    ): List<Detection> {
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
                height = maxOf(0f, y2 - y1),
            )
        }
        out.sortBy { it.x }
        return out
    }

    /**
     * 마스크(logit > 0)에서 크롭 bbox를 재도출한다. 마스크 격자 인덱스 기준 [x0, y0, x1, y1] 포함 범위.
     *
     * ⚠️ 크롭은 `pred_boxes`가 아니라 **마스크에서** 잘라야 한다(iOS `cropWithMask`).
     *    pred_boxes로 자르면 iOS와 크롭 크기가 달라진다.
     *
     * @param masks 해당 쿼리의 [144,144] 마스크 로짓 (평탄화)
     * @return 마스크가 비어 있으면 null
     */
    fun maskBounds(masks: FloatArray, queryOffset: Int): IntArray? {
        val ms = RfDetrSpec.MASK_SIZE
        var x0 = ms
        var x1 = -1
        var y0 = ms
        var y1 = -1
        for (my in 0 until ms) {
            val row = queryOffset + my * ms
            for (mx in 0 until ms) {
                if (masks[row + mx] > 0f) {
                    if (mx < x0) x0 = mx
                    if (mx > x1) x1 = mx
                    // iOS는 dy = ms-1-my 로 뒤집는다. 우리는 뒤집지 않는다.
                    if (my < y0) y0 = my
                    if (my > y1) y1 = my
                }
            }
        }
        return if (x1 >= x0 && y1 >= y0) intArrayOf(x0, y0, x1, y1) else null
    }
}
