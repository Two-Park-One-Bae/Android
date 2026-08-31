package app.nursemate.spike.rfdetr

/**
 * RF-DETR-Seg-Small 입출력 규약. iOS `RFDetrSegmentor.swift`가 정본이다.
 *
 * 여기 값들은 mlpackage 스펙에서 실측 확인했다:
 *   입력  image [1,3,576,576] fp32 NCHW RGB
 *   출력  pred_logits [1,100,2] · pred_boxes [1,100,4] · pred_masks [1,100,144,144]
 */
object RfDetrSpec {
    const val INPUT_SIZE = 576

    /** 576 / 4. 마스크는 입력의 1/4 해상도다. */
    const val MASK_SIZE = 144

    const val QUERY_COUNT = 100

    /** iOS ViewModel의 scoreThreshold. */
    const val SCORE_THRESHOLD = 0.5f

    /**
     * iOS CameraPicker가 진입 시 1회 적용하는 최장변 상한.
     * 크롭 해상도가 여기에 직접 비례하므로 서버로 가는 PNG 크기에도 영향을 준다.
     * (12MP 원본이 파이프라인에 상주해 Jetsam OOM을 유발한 뒤 도입됨)
     */
    const val MAX_DIMENSION = 2048

    val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
}

/** 정규화 좌표(0~1, 좌상단 원점) 기준 검출 하나. */
data class Detection(
    val query: Int,
    val score: Float,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    fun iou(other: Detection): Float {
        val ix = minOf(x + width, other.x + other.width) - maxOf(x, other.x)
        val iy = minOf(y + height, other.y + other.height) - maxOf(y, other.y)
        if (ix <= 0f || iy <= 0f) return 0f
        val inter = ix * iy
        val union = width * height + other.width * other.height - inter
        return if (union > 0f) inter / union else 0f
    }
}
