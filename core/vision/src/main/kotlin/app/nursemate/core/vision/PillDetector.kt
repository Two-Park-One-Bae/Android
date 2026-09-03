package app.nursemate.core.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
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
    /** 출력 읽기 + 디코딩 + 크롭. ONNX 출력을 JVM으로 가져오는 비용이 여기 포함된다. */
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
 * RF-DETR-Seg 온디바이스 검출기 (ONNX Runtime).
 *
 * 모델은 iOS가 쓰는 CoreML(`.mlpackage`)을 만든 **바로 그 원본 그래프**다. 입출력 이름이
 * CoreML과 동일하고(`image` / `pred_logits` / `pred_boxes` / `pred_masks`),
 * CoreML 결과와 박스 IoU 1.0000으로 일치한다.
 *
 * 세션 로드가 수백 ms 걸리므로 한 번만 만들고 재사용한다.
 *
 * ## 실행 백엔드는 CPU만 쓴다
 * NM-396에서 실측한 결과다(Galaxy S24 / Exynos 2400, fp32):
 * ```
 * CPU      1550 ms
 * XNNPACK  3410 ms   (오히려 느림)
 * NNAPI    실패      (Android 15에서 deprecated된 API이기도 하다)
 * WebGPU    845 ms   (1.75배 — 단, 프리빌트 배포본에는 EP가 없어 소스 빌드가 필요)
 * ```
 * WebGPU는 11 MB AAR을 저장소에 넣고 minSdk를 28로 올려야 해서 채택하지 않았다.
 * 이 모델이 Conv 1.4% · MatMul 13%인 Transformer라 가속기 이득이 구조적으로 제한된다.
 */
class PillDetector(modelFile: File, threads: Int = DEFAULT_THREADS) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private val session: OrtSession = env.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // ⚠️ VERBOSE는 디스패치마다 로그를 찍어 성능을 크게 왜곡한다. 측정할 일이 있어
            //    노드 배정을 봐야 한다면 그때만 올리고, 그때 잰 시간은 믿지 말 것.
            setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING)
        }
    )

    private val inputName: String = session.inputNames.first()

    /** 전처리 출력 버퍼(4 MB). 매 추론마다 재할당하지 않는다. */
    private val inputBuffer = FloatArray(3 * RfDetrSpec.INPUT_SIZE * RfDetrSpec.INPUT_SIZE)

    /** [inputBuffer]를 공유하므로 동시 호출을 직렬화한다. */
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
        RfDetrPreprocess.toInputTensor(bitmap.toRgb888(), width, height, inputBuffer)
        val t1 = System.nanoTime()

        val shape = longArrayOf(
            1,
            3,
            RfDetrSpec.INPUT_SIZE.toLong(),
            RfDetrSpec.INPUT_SIZE.toLong()
        )

        var inferenceEnd: Long
        val pills: List<DetectedPill>
        OnnxTensor.createTensor(env, FloatBuffer.wrap(inputBuffer), shape).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                inferenceEnd = System.nanoTime()

                val logits = result.readFloats(RfDetrSpec.OUTPUT_LOGITS, RfDetrSpec.QUERY_COUNT * 2)
                val boxes = result.readFloats(RfDetrSpec.OUTPUT_BOXES, RfDetrSpec.QUERY_COUNT * 4)
                val detections = RfDetrPostprocess.decode(logits, boxes)

                pills = cropDetections(bitmap, detections, result)
            }
        }
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
     * 검출별로 마스크를 꺼내 낱알을 오려낸다.
     *
     * ⚠️ **`pred_masks`는 반드시 [OnnxTensor.getFloatBuffer]로 읽어야 한다.**
     * `[1,100,144,144]` = 207만 float인데, `OnnxValue.getValue()`로 꺼내면 중첩 배열로 박싱되어
     * **1.2초**가 든다(스파이크에서 이걸 추론 시간으로 오인한 적이 있다). 버퍼로 읽고
     * **임계값을 통과한 쿼리의 슬라이스만** 복사하면 검출 개수에 비례하는 비용만 낸다.
     */
    private fun cropDetections(
        source: Bitmap,
        detections: List<Detection>,
        result: OrtSession.Result
    ): List<DetectedPill> {
        if (detections.isEmpty()) return emptyList()

        val masks = result.tensor(RfDetrSpec.OUTPUT_MASKS).floatBuffer
        val plane = RfDetrSpec.MASK_SIZE * RfDetrSpec.MASK_SIZE
        val slice = FloatArray(plane)

        return detections.mapNotNull { detection ->
            masks.position(detection.query * plane)
            masks.get(slice)
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
        session.close()
    }

    companion object {
        /**
         * 실측에서 4스레드가 최적이었다 — 1스레드 6968 ms · 2스레드 3615 ms · 4스레드 2083 ms.
         * 6·8스레드는 오히려 느려졌다(big.LITTLE에서 리틀코어로 넘치는 비용).
         */
        const val DEFAULT_THREADS = 4

        /** fp32. 119 MB라 APK에 넣지 않는다 — 배포 방식은 별도 결정 사항이다. */
        const val MODEL_FILE_NAME = "rfdetr_seg_small.onnx"
    }
}

private fun OrtSession.Result.tensor(name: String): OnnxTensor =
    get(name).orElseThrow { IllegalStateException("모델 출력 '$name' 을 찾지 못했습니다") } as OnnxTensor

private fun OrtSession.Result.readFloats(name: String, size: Int): FloatArray {
    val out = FloatArray(size)
    tensor(name).floatBuffer.get(out)
    return out
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
