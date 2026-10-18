package app.nursemate.core.vision

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 한 번의 검출에서 걸린 시간(ms). 어디가 병목인지 보려고 단계별로 나눈다. */
data class Timings(
    val decodeImageMs: Long,
    val preprocessMs: Long,
    val inferenceMs: Long,
    /** 디코딩 + 크롭. */
    val postprocessMs: Long
) {
    val totalMs: Long get() = decodeImageMs + preprocessMs + inferenceMs + postprocessMs
}

/** 검출된 알약 하나 — 위치와, 마스크로 오려낸 이미지. */
data class DetectedPill(
    val detection: Detection,
    /**
     * 마스크를 알파로 넣어 **배경이 투명한** 낱알 이미지.
     * 서버 `POST /pill-attributes` 가 받는 크롭이자 화면 썸네일이다. PNG로 인코딩해야 알파가 산다.
     */
    val crop: Bitmap
)

data class DetectionResult(
    val pills: List<DetectedPill>,
    val imageWidth: Int,
    val imageHeight: Int,
    val timings: Timings
)

/**
 * RF-DETR-Seg 온디바이스 검출기 (LiteRT, NM-628).
 *
 * 모델은 iOS 가 쓰는 CoreML(`.mlpackage`)을 만든 원본 그래프를 LiteRT 로 옮긴 것이다(가중치 fp16 저장 ·
 * `rfdetr_seg_small_f16w.tflite`). ONNX 시절과 입출력 **배치가 다르다**.
 *
 * | | ONNX(이전) | LiteRT |
 * |---|---|---|
 * | 입력 | [1,3,576,576] NCHW | [1,576,576,3] **NHWC** |
 * | 마스크 | [1,100,144,144] 쿼리가 앞 | [1,144,144,100] **쿼리가 마지막 축** |
 *
 * 전처리는 NCHW 로 써 두고([RfDetrPreprocess] · 그 단위 테스트 그대로) 넣기 직전에 돌린다.
 *
 * ## 실행: GPU FP32, 안 되면 CPU
 * ML 계약. FP16 은 Exynos 2500 에서 개수 일치 93.3% 로 탈락했고 2400 · 2600 도 여유가 1~2장뿐이었다.
 * FP32 로 공식 세트 60장 개수 일치 **98.3%**(가중치 fp16 저장 탓 1장), 466~942ms/장(Exynos 2600 ~ SD 8 Gen 1).
 *
 * 모델 열기가 수백 ms~수 초 걸리므로 한 번만 열고 재사용한다.
 */
class PillDetector(modelFile: File) : Closeable {

    private val model = LiteRtModel.open(modelFile, PLAN)

    /** 전처리 출력(NCHW) · 모델 입력(NHWC). 4 MB 씩, 매 추론마다 재할당하지 않는다. */
    private val nchw = FloatArray(3 * RfDetrSpec.INPUT_SIZE * RfDetrSpec.INPUT_SIZE)
    private val nhwc = FloatArray(nchw.size)

    /** 버퍼와 모델을 공유하므로 동시 호출을 직렬화한다. */
    private val lock = Mutex()

    /**
     * @param bitmap [ImageLoader.load]가 돌려준 비트맵 (EXIF 적용 + 최장변 2048 이하)
     * @param decodeImageMs 호출자가 잰 이미지 로딩 시간 (측정 표시용, 계산에는 쓰지 않는다)
     */
    suspend fun detect(bitmap: Bitmap, decodeImageMs: Long = 0L): DetectionResult = lock.withLock {
        withContext(Dispatchers.Default) { runDetection(bitmap, decodeImageMs) }
    }

    private fun runDetection(bitmap: Bitmap, decodeImageMs: Long): DetectionResult {
        val width = bitmap.width
        val height = bitmap.height

        val t0 = System.nanoTime()
        RfDetrPreprocess.toInputTensor(bitmap.toRgb888(), width, height, nchw)
        val side = RfDetrSpec.INPUT_SIZE
        nchwToNhwc(nchw, nhwc, n = 1, c = 3, plane = side * side)
        val t1 = System.nanoTime()

        val outputs = model.run(nhwc)
        val inferenceEnd = System.nanoTime()

        // 출력은 크기로 가른다 — 셋이 서로 다르고, 이름보다 변환 도구에 덜 흔들린다.
        val logits = outputs.first { it.size == RfDetrSpec.QUERY_COUNT * 2 }
        val boxes = outputs.first { it.size == RfDetrSpec.QUERY_COUNT * 4 }
        val masks = outputs.first { it.size == RfDetrSpec.QUERY_COUNT * MASK_PLANE }
        val pills = cropDetections(bitmap, RfDetrPostprocess.decode(logits, boxes), masks)
        val postprocessEnd = System.nanoTime()

        return DetectionResult(
            pills = pills,
            imageWidth = width,
            imageHeight = height,
            timings = Timings(
                decodeImageMs = decodeImageMs,
                preprocessMs = (t1 - t0) / 1_000_000,
                inferenceMs = (inferenceEnd - t1) / 1_000_000,
                postprocessMs = (postprocessEnd - inferenceEnd) / 1_000_000
            )
        )
    }

    /**
     * 검출별로 마스크를 꺼내 낱알을 오려낸다. 마스크는 `[144,144,100]` — 한 화소에 쿼리 100개가 붙어 있어
     * 쿼리 q 의 평면은 `(y·144 + x)·100 + q` 를 건너뛰며 모은다. 검출된 쿼리만 모으므로 비용은 검출 개수에 비례한다.
     */
    private fun cropDetections(source: Bitmap, detections: List<Detection>, masks: FloatArray): List<DetectedPill> {
        if (detections.isEmpty()) return emptyList()
        val slice = FloatArray(MASK_PLANE)
        return detections.mapNotNull { detection ->
            querySlice(masks, detection.query, slice)
            val bounds = RfDetrPostprocess.maskBounds(slice) ?: return@mapNotNull null
            val crop = cropWithMask(source, slice, bounds) ?: return@mapNotNull null
            DetectedPill(detection, crop)
        }
    }

    /**
     * 마스크 외접 범위로 자르고, **마스크를 알파 채널에 넣어** 배경이 비치는 낱알 이미지를 만든다.
     *
     * ⚠️ **배경을 검정으로 합성하면 안 된다.** iOS는 크롭을 `pngData()` 로 인코딩해 보내고
     * (`DrugIdentificationViewModel.swift:119`), 마스크 확률을 그대로 알파에 쓴다
     * (`RFDetrSegmentor.swift:113-122`). 서버가 받는 것도 **투명 배경 PNG**다.
     * 검정으로 채워 보내면 배경이 낱알의 일부처럼 보여 색·모양 분류가 달라진다.
     * (openapi 의 `mimeType: image/jpeg` 는 스키마 **예시**일 뿐 강제가 아니다.)
     *
     * 알파는 **곱하지 않은(straight) 값**으로 둔다. Android 는 `Bitmap.createBitmap(IntArray, …)`
     * 입력을 비프리멀티플라이로 보고 내부에서 곱하므로, 미리 곱해 넣으면 두 번 곱해져 어두워진다.
     */
    private fun cropWithMask(source: Bitmap, mask: FloatArray, bounds: IntArray): Bitmap? {
        val ms = RfDetrSpec.MASK_SIZE
        val width = source.width
        val height = source.height

        // 격자 셀 mx가 덮는 정규화 구간은 [mx/ms, (mx+1)/ms] 이므로, 포함 범위의 오른쪽·아래는 +1 한다.
        val left = floor(bounds[0].toFloat() / ms * width).toInt().coerceIn(0, width - 1)
        val top = floor(bounds[1].toFloat() / ms * height).toInt().coerceIn(0, height - 1)
        val right = ceil((bounds[2] + 1).toFloat() / ms * width).toInt().coerceIn(left + 1, width)
        val bottom = ceil((bounds[3] + 1).toFloat() / ms * height).toInt().coerceIn(top + 1, height)

        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth <= 0 || cropHeight <= 0) return null

        val pixels = IntArray(cropWidth * cropHeight)
        source.getPixels(pixels, 0, cropWidth, left, top, cropWidth, cropHeight)

        for (py in 0 until cropHeight) {
            val v = (top + py + 0.5f) / height
            for (px in 0 until cropWidth) {
                val u = (left + px + 0.5f) / width
                val alpha = (RfDetrPostprocess.maskProbabilityAt(mask, u, v) * 255f).roundToInt()
                val index = py * cropWidth + px
                pixels[index] = if (alpha == 0) {
                    // 완전히 투명한 자리는 RGB도 비운다(iOS와 동일). 압축에도 유리하다.
                    0
                } else {
                    (alpha shl 24) or (pixels[index] and 0x00FFFFFF)
                }
            }
        }
        return Bitmap.createBitmap(pixels, cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
    }

    override fun close() {
        model.close()
    }

    companion object {
        /** 가중치 fp16 저장 · 63MB. 목록과 해시는 `app/models.md5`. */
        const val MODEL_FILE_NAME = "rfdetr_seg_small_f16w.tflite"

        /** ML 실행 정밀도 계약 — GPU FP32. FP16 은 Exynos 2500 에서 탈락했다. */
        private val PLAN = RunPlan(Placement.GPU, GpuPrecision.FP32)

        private const val MASK_PLANE = RfDetrSpec.MASK_SIZE * RfDetrSpec.MASK_SIZE
    }
}

/** `[H·W, Q]` 로 놓인 마스크에서 쿼리 [query] 의 평면 `[H·W]` 를 [out] 에 모은다. */
internal fun querySlice(masks: FloatArray, query: Int, out: FloatArray) {
    val queries = RfDetrSpec.QUERY_COUNT
    var i = query
    for (p in out.indices) {
        out[p] = masks[i]
        i += queries
    }
}

/** ARGB_8888 비트맵 → RGB888 연속 바이트. [RfDetrPreprocess]가 요구하는 형식. */
private fun Bitmap.toRgb888(): ByteArray {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val out = ByteArray(width * height * 3)
    var i = 0
    for (pixel in pixels) {
        out[i++] = (pixel shr 16).toByte() // R
        out[i++] = (pixel shr 8).toByte() // G
        out[i++] = pixel.toByte() // B
    }
    return out
}
