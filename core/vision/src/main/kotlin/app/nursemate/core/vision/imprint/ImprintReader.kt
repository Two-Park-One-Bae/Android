package app.nursemate.core.vision.imprint

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * 알약 한 면의 각인을 읽는다 — CRNN + CTC 온디바이스(NM-485).
 *
 * 기준은 ML 레포 `models/imprint/20260907-crnn-ep60-s1/infer.py` 다. **같은 크롭에 같은 답**이
 * 나와야 한다(DoD). 전처리는 [ImprintPreprocess], 디코딩은 [ImprintDecoding] 에 있고 여기서는
 * 둘을 잇고 추론만 한다.
 *
 * ## 한 번에 144장을 돌린다
 * 회전 24 × 배율 6 이다. 방향 추정기는 기각됐다 — 여러 방향을 읽고 모델이 스스로 매긴 확률로
 * 고르는 편이 낫고, 자체 모델이라 그 확률이 보정돼 있다. 회전을 한 배치로 묶으므로 방향 수를
 * 늘려도 비용이 거의 안 는다.
 *
 * ⚠️ **알약 하나당 144회다.** 셋을 찍으면 432회이고 저사양 기기의 대기 시간은 재봐야 한다
 * (NM-485 「정할 것」 1번).
 *
 * ## 못 읽으면 null 이다
 * 빈 문자열이 아니다. 계약에서 `imprint: ""` 는 「각인이 없는 알약만」이라는 하드 조건이라,
 * 못 읽은 것을 그렇게 보내면 각인이 있는 정답이 전부 탈락한다.
 */
class ImprintReader(modelFile: File, threads: Int = DEFAULT_THREADS) : Closeable {

    init {
        // 네이티브 로딩은 여기서 한 번만 한다. 앱 시작에 걸면 각인을 쓰지 않는 사용자도 비용을 낸다.
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private val session: OrtSession = env.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
    )

    /** 144장 × 1채널 × 128 × 128. 매 호출 재할당하지 않는다(9.4MB). */
    private val input = FloatArray(ImprintPreprocess.SAMPLES * PLANE)

    /** [input] 을 공유하므로 동시 호출을 직렬화한다. */
    private val lock = Mutex()

    /**
     * @param crop 마스크로 오려낸 낱알 한 면. 배경이 투명한 `ARGB_8888` 을 받는다
     *             (`PillDetector` 의 `DetectedPill.crop`).
     * @return 확신 글자, 또는 채택 임계에 못 미치면 null
     */
    suspend fun read(crop: Bitmap): Result = lock.withLock {
        withContext(Dispatchers.Default) { readBlocking(crop) }
    }

    private fun readBlocking(crop: Bitmap): Result {
        val gray = crop.toGrayOnWhite()
        return readGray(gray).also { gray.release() }
    }

    /**
     * 회색조 한 장에서 바로 읽는다 — 비트맵 디코딩을 건너뛰는 자리.
     *
     * 대조 테스트가 쓴다. 픽스처를 PNG 로 두면 파이썬과 Android 의 **디코더 차이가 섞여**
     * 전처리가 아닌 것을 재게 되므로, 기준값은 숫자 배열로 두고 여기로 들어온다.
     */
    internal fun readGray(gray: Mat): Result {
        val t0 = System.nanoTime()
        val squared = ImprintPreprocess.toSquare(gray)

        var offset = 0
        for (zoom in ImprintPreprocess.ZOOMS) {
            val framed = ImprintPreprocess.zoomFrame(squared, zoom)
            for (i in 0 until ImprintPreprocess.ROTATIONS) {
                val degrees = i * 360.0 / ImprintPreprocess.ROTATIONS
                val rotated = ImprintPreprocess.rotate(framed, degrees)
                val prepped = ImprintPreprocess.applyClahe(rotated)
                rotated.release()
                prepped.writeNormalized(input, offset)
                prepped.release()
                offset += PLANE
            }
            framed.release()
        }
        squared.release()

        val t1 = System.nanoTime()
        val logits = runSession()
        val t2 = System.nanoTime()
        val result = decode(logits)
        val t3 = System.nanoTime()

        return result.copy(
            timings = Timings(
                preprocessMs = (t1 - t0) / NANOS_PER_MS,
                inferenceMs = (t2 - t1) / NANOS_PER_MS,
                decodeMs = (t3 - t2) / NANOS_PER_MS
            )
        )
    }

    /** @return 로짓 [T][N][C] — 시간축이 먼저다(모델 출력 그대로). */
    private fun runSession(): Array<Array<FloatArray>> {
        val shape = longArrayOf(ImprintPreprocess.SAMPLES.toLong(), 1, SIDE_L, SIDE_L)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(INPUT_NAME to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return (result[0].value as Array<Array<FloatArray>>)
            }
        }
    }

    /**
     * 후보 144개를 각각 풀어 `best_pair` 점수가 가장 높은 것을 고른다.
     *
     * ⚠️ **고르는 기준과 답이 다르다.** 고르는 것은 `best_pair` 이고, 답은 그 후보의 확신
     * 글자다. 사정은 [ImprintDecoding].
     */
    private fun decode(logits: Array<Array<FloatArray>>): Result {
        val steps = logits.size
        var bestScore = -1f
        var bestChars: List<ImprintDecoding.Char> = emptyList()
        var bestPair = ""

        val candidate = Array(steps) { FloatArray(0) }
        for (n in 0 until ImprintPreprocess.SAMPLES) {
            for (t in 0 until steps) candidate[t] = logits[t][n]
            val chars = ImprintDecoding.decodeConfident(candidate)
            val (pair, score) = ImprintDecoding.bestPair(chars)
            if (score > bestScore) {
                bestScore = score
                bestChars = chars
                bestPair = pair
            }
        }
        return Result(
            imprint = ImprintDecoding.adopt(bestChars, bestScore),
            pair = bestPair,
            score = bestScore,
            timings = Timings()
        )
    }

    override fun close() {
        session.close()
    }

    /**
     * 판독 결과.
     *
     * @param imprint 후보 좁히기에 쓸 값. **못 읽었으면 null 이다** — 빈 문자열이 아니다
     * @param pair 고르는 데 쓴 두 글자. 진단용이고 서버로 보내지 않는다
     * @param score 그 두 글자의 확신도. [ImprintDecoding.ADOPT_THRESHOLD] 와 비교된 값이다
     */
    data class Result(val imprint: String?, val pair: String, val score: Float, val timings: Timings = Timings())

    /**
     * 단계별 소요. **어디가 비싼지 알아야 줄일 곳을 정한다.**
     *
     * 전처리가 지배적이면 TTA 장수를 줄이는 것이 답이고, 추론이 지배적이면 스레드나 배치를
     * 손봐야 한다. 합계만 보면 둘을 구분할 수 없다.
     *
     * ⚠️ **세션 생성은 여기 없다.** 21MB 모델을 여는 비용은 한 번만 드는데 여기 섞으면
     * 알약마다 그만큼 든다고 잘못 읽힌다. 그건 호출부가 따로 잰다.
     */
    data class Timings(val preprocessMs: Long = 0, val inferenceMs: Long = 0, val decodeMs: Long = 0) {
        val totalMs: Long get() = preprocessMs + inferenceMs + decodeMs
    }

    companion object {
        /** 검출기와 같은 값. 실측 근거는 `PillDetector.DEFAULT_THREADS`. */
        const val DEFAULT_THREADS = 4

        /** CRNN + CTC, 5.97M 파라미터. 21MB. */
        const val MODEL_FILE_NAME = "reader_ep60_s1.onnx"

        private const val INPUT_NAME = "image"
        private const val PLANE = ImprintPreprocess.SIDE * ImprintPreprocess.SIDE
        private const val SIDE_L = ImprintPreprocess.SIDE.toLong()
        private const val NANOS_PER_MS = 1_000_000L
    }
}

/**
 * 투명 배경 크롭 → 흰 배경 흑백.
 *
 * 기준 구현의 `load_crop` 과 같다 — 알파를 흰색 위에 합성한 뒤 흑백으로 만든다. 알파를 무시하고
 * 바로 흑백으로 가면 잘려 나간 자리가 검게 남아 알약 마스크가 통째로 어긋난다.
 */
private fun Bitmap.toGrayOnWhite(): Mat {
    val rgba = Mat()
    Utils.bitmapToMat(this, rgba)

    val channels = ArrayList<Mat>(4)
    Core.split(rgba, channels)
    val alpha = channels[3]
    val gray = Mat()
    Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

    // out = gray * a + 255 * (1 - a) 를 **255 + a * (gray - 255)** 로 푼다.
    // 같은 식이고, OpenCV Java 에 있는 (Mat, Scalar) 오버로드만 쓴다.
    val a = Mat()
    alpha.convertTo(a, CvType.CV_32F, 1.0 / 255.0)
    val g = Mat()
    gray.convertTo(g, CvType.CV_32F)
    Core.subtract(g, Scalar(255.0), g)
    Core.multiply(g, a, g)
    Core.add(g, Scalar(255.0), g)

    val out = Mat()
    g.convertTo(out, CvType.CV_8U)

    channels.forEach { it.release() }
    rgba.release()
    gray.release()
    a.release()
    g.release()
    return out
}

/** `to_tensor` — `/255 - 0.5` 후 `/0.5`. 결과는 [-1,1]. */
private fun Mat.writeNormalized(out: FloatArray, offset: Int) {
    val cols = cols()
    val row = ByteArray(cols)
    var i = offset
    for (y in 0 until rows()) {
        get(y, 0, row)
        for (x in 0 until cols) {
            val v = (row[x].toInt() and 0xFF) / 255f
            out[i++] = (v - 0.5f) / 0.5f
        }
    }
}
