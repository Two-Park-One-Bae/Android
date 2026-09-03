package app.nursemate.core.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 후처리는 iOS와의 결과 일치를 지키는 계약이다. 여기 규칙이 하나라도 깨지면
 * 같은 사진에서 iOS와 다른 결과가 나온다.
 */
class RfDetrPostprocessTest {

    /**
     * 미사용 쿼리는 확실히 임계값 아래로 깔아둔다.
     *
     * ⚠️ 0으로 두면 안 된다 — `sigmoid(0) = 0.5`이고 임계값 비교가 `score < threshold`라
     * **경계값이 통과**해서 100개 쿼리가 전부 검출로 잡힌다.
     */
    private fun logits() = FloatArray(RfDetrSpec.QUERY_COUNT * 2) { -10f }
    private fun boxes() = FloatArray(RfDetrSpec.QUERY_COUNT * 4)

    private fun FloatArray.putBox(query: Int, cx: Float, cy: Float, w: Float, h: Float) {
        this[query * 4] = cx
        this[query * 4 + 1] = cy
        this[query * 4 + 2] = w
        this[query * 4 + 3] = h
    }

    @Test
    fun `임계값을 넘은 쿼리만 남고 minX 순으로 정렬된다`() {
        val logits = logits()
        val boxes = boxes()

        // 오른쪽에 있는 강한 검출
        logits[5 * 2] = 2f // sigmoid ≈ 0.881
        boxes.putBox(5, cx = 0.8f, cy = 0.5f, w = 0.2f, h = 0.2f)
        // 왼쪽에 있는 더 강한 검출
        logits[2 * 2] = 3f // sigmoid ≈ 0.953
        boxes.putBox(2, cx = 0.3f, cy = 0.5f, w = 0.2f, h = 0.2f)
        // 임계값 아래
        logits[9 * 2] = -1f // sigmoid ≈ 0.269
        boxes.putBox(9, cx = 0.5f, cy = 0.5f, w = 0.2f, h = 0.2f)

        val result = RfDetrPostprocess.decode(logits, boxes)

        assertEquals("임계값 미달 쿼리가 남았다", 2, result.size)
        // score(0.953)가 더 높은 query 2가 앞에 오는 게 아니라, x가 작은 쪽이 앞이다.
        assertEquals("정렬은 score가 아니라 box.minX 오름차순이어야 한다", 2, result[0].query)
        assertEquals(5, result[1].query)
        assertEquals(0.2f, result[0].x, 1e-5f)
        assertEquals(0.7f, result[1].x, 1e-5f)
    }

    @Test
    fun `score는 logits의 0번 채널만 쓰고 sigmoid로 계산한다`() {
        val logits = logits()
        val boxes = boxes()
        logits[7 * 2] = 0f // sigmoid(0) = 0.5 — 임계값과 같으므로 통과해야 한다
        logits[7 * 2 + 1] = 99f // 1번 채널은 무시되어야 한다 (softmax·argmax 아님)
        boxes.putBox(7, cx = 0.5f, cy = 0.5f, w = 0.1f, h = 0.1f)

        val result = RfDetrPostprocess.decode(logits, boxes)

        assertEquals(1, result.size)
        assertEquals(0.5f, result[0].score, 1e-6f)
    }

    @Test
    fun `겹치는 검출을 억제하지 않는다 - NMS 없음`() {
        val logits = logits()
        val boxes = boxes()
        // 완전히 같은 위치의 두 검출. NMS가 있으면 하나가 사라진다.
        for (query in listOf(1, 4)) {
            logits[query * 2] = 4f
            boxes.putBox(query, cx = 0.5f, cy = 0.5f, w = 0.3f, h = 0.3f)
        }

        val result = RfDetrPostprocess.decode(logits, boxes)

        assertEquals("DETR은 헝가리안 매칭이 중복을 억제하는 전제라 NMS를 넣으면 안 된다", 2, result.size)
        assertEquals("완전히 겹치는데도 둘 다 남아야 한다", 1f, result[0].iou(result[1]), 1e-5f)
    }

    @Test
    fun `이미지 밖으로 나간 박스는 0과 1 사이로 잘린다`() {
        val logits = logits()
        val boxes = boxes()
        logits[3 * 2] = 5f
        // cx - w/2 = -0.15 (왼쪽 밖), cx + w/2 = 0.25
        boxes.putBox(3, cx = 0.05f, cy = 0.5f, w = 0.4f, h = 0.2f)

        val result = RfDetrPostprocess.decode(logits, boxes)

        assertEquals(1, result.size)
        assertEquals(0f, result[0].x, 1e-6f)
        assertEquals(0.25f, result[0].width, 1e-5f)
    }

    @Test
    fun `y좌표를 뒤집지 않는다`() {
        val logits = logits()
        val boxes = boxes()
        logits[0] = 5f
        // 이미지 위쪽(cy = 0.2)에 있는 검출
        boxes.putBox(0, cx = 0.5f, cy = 0.2f, w = 0.1f, h = 0.1f)

        val result = RfDetrPostprocess.decode(logits, boxes)

        // iOS는 여기서 1 - (cy ± h/2) 로 뒤집는다(입력 미러링 보정). 우리는 뒤집지 않으므로
        // 위쪽 검출은 y가 작아야 한다. 0.75가 나오면 y-flip이 들어간 것이다.
        assertEquals(0.15f, result[0].y, 1e-5f)
    }

    @Test
    fun `빈 마스크는 null을 돌려준다`() {
        val mask = FloatArray(RfDetrSpec.MASK_SIZE * RfDetrSpec.MASK_SIZE) { -5f }
        assertNull(RfDetrPostprocess.maskBounds(mask))
    }

    @Test
    fun `마스크 외접 범위는 로짓이 양수인 셀을 포함한다`() {
        val ms = RfDetrSpec.MASK_SIZE
        val mask = FloatArray(ms * ms) { -5f }
        // (mx, my) = (10, 20) 과 (13, 25) 를 켠다
        mask[20 * ms + 10] = 1f
        mask[25 * ms + 13] = 2f

        val bounds = requireNotNull(RfDetrPostprocess.maskBounds(mask))

        assertEquals("x0", 10, bounds[0])
        assertEquals("y0", 20, bounds[1])
        assertEquals("x1 (포함)", 13, bounds[2])
        assertEquals("y1 (포함)", 25, bounds[3])
    }

    @Test
    fun `마스크 샘플링은 셀 중심에서 그 셀의 값을 낸다`() {
        val ms = RfDetrSpec.MASK_SIZE
        val mask = FloatArray(ms * ms) { -10f }
        mask[0] = 10f // (0,0) 셀만 켠다

        // align_corners=false 규약: 셀 k의 중심은 정규화 좌표 (k + 0.5) / ms 다.
        val centerOfCell0 = 0.5f / ms
        val centerOfCell1 = 1.5f / ms

        assertTrue(
            "켜진 셀 중심은 확률이 1에 가까워야 한다",
            RfDetrPostprocess.maskProbabilityAt(mask, centerOfCell0, centerOfCell0) > 0.99f
        )
        assertTrue(
            "꺼진 이웃 셀 중심은 확률이 0에 가까워야 한다",
            RfDetrPostprocess.maskProbabilityAt(mask, centerOfCell1, centerOfCell0) < 0.01f
        )
    }

    @Test
    fun `마스크 샘플링은 확률이 아니라 로짓을 보간한다`() {
        val ms = RfDetrSpec.MASK_SIZE
        val mask = FloatArray(ms * ms) { 0f }
        mask[0] = 4f
        // 이웃 셀들은 0 — 비대칭이라 보간 순서에 따라 값이 갈린다.

        // 셀 0과 셀 1의 정확히 중간 지점.
        val probability = RfDetrPostprocess.maskProbabilityAt(mask, 1.0f / ms, 0.5f / ms)

        // 로짓 먼저 보간:   sigmoid((4 + 0) / 2)          = sigmoid(2)  ≈ 0.8808
        // 확률 먼저 보간:   (sigmoid(4) + sigmoid(0)) / 2 ≈ 0.7410
        // iOS·Python 모두 전자다. 순서를 바꾸면 여기서 걸린다.
        assertEquals(0.8808f, probability, 1e-3f)
    }
}
