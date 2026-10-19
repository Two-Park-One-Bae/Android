package app.nursemate.core.vision.imprint

import android.graphics.Bitmap
import app.nursemate.core.vision.GpuPrecision
import app.nursemate.core.vision.LiteRtModel
import app.nursemate.core.vision.Placement
import app.nursemate.core.vision.RunPlan
import app.nursemate.core.vision.VisionBackend
import java.io.Closeable
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
 * ## 후보 144장을 읽는다
 * 회전 24 × 배율 6 이다. 방향 추정기는 기각됐다 — 여러 방향을 읽고 모델이 스스로 매긴 확률로
 * 고르는 편이 낫고, 자체 모델이라 그 확률이 보정돼 있다.
 *
 * ## 모델이 다섯 조각이다 — G5 (LiteRT, NM-628)
 * 한 판(ONNX `reader_ep60_s1.onnx`)을 ML 이 다섯 조각으로 나눴다. 조각마다 잘 도는 가속기가 달라서다.
 *
 * | 조각 | 하는 일 | 실행(ML 계약) | 왜 |
 * |---|---|---|---|
 * | `p1_loc` | TPS 제어점 추정 | GPU FP16 | |
 * | `p2_tps` | 제어점대로 크롭을 편다 | **CPU** | GPU 판보다 CPU 가 빨랐다 |
 * | `p3_trunk` | CNN 몸통 | GPU **FP32** | 8 Elite 에서 FP16 이면 실행마다 다르게 틀렸다 |
 * | `p4_frm` | 특징 재배열 | **GPU+CPU** FP16 | GPU 만으로 열면 Exynos 에서 컴파일 실패 |
 * | `p5_lstm_head` | 양방향 LSTM + 글자 확률 | **CPU** | GPU 판보다 CPU 가 빨랐다 |
 *
 * 공식 세트 550면에서 **전 기기 응답 · 정답 · 오답이 운영 fp32 와 같다**(233/207/26). 면당 0.4~1.2초
 * (ONNX 시절 1.4~5.0초).
 *
 * ## 묶음이 조각을 차례로 흐른다
 * 144장을 **24장 묶음 6개**로 나눈다. 조각마다 **전용 스레드 하나**를 두고 묶음이 p1 → p5 를 흘러가게 해서,
 * 묶음 k 가 p3 에 있을 때 묶음 k+1 이 p1 을 돈다. 순차로 돌리면 약 2배 느리다(Exynos 2600 715 → 377ms).
 * 조각 하나를 두 스레드가 동시에 부르지 않는다 — [LiteRtModel] 이 버퍼를 공유하기 때문이다.
 *
 * ## 못 읽으면 null 이다
 * 빈 문자열이 아니다. 계약에서 `imprint: ""` 는 「각인이 없는 알약만」이라는 하드 조건이라,
 * 못 읽은 것을 그렇게 보내면 각인이 있는 정답이 전부 탈락한다.
 *
 * @param parts [PART_FILE_NAMES] 순서의 조각 파일 다섯
 */
class ImprintReader(parts: List<File>) : Closeable {

    init {
        // 모델을 열기 **전에** 거른다 — 연 뒤에 던지면 생성자가 끝나지 않아 아무도 close() 를 못 부른다.
        require(parts.map { it.name } == PART_FILE_NAMES) {
            "각인 조각은 ${PART_FILE_NAMES.joinToString()} 순서여야 한다 — 받은 것 ${parts.map { it.name }}"
        }
        // 네이티브 로딩은 여기서 한 번만 한다. 앱 시작에 걸면 각인을 쓰지 않는 사용자도 비용을 낸다.
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
    }

    private val models: List<LiteRtModel> = openAll(parts)

    /** 지금 GPU 로 도는 조각 이름 — 대체됐으면 빠진다. 벤치가 실제 경로를 남기려고 본다. */
    internal val gpuParts: List<String> get() = models.filter { it.onGpu }.map { it.name }

    /** 조각마다 전용 스레드 — 한 조각을 두 스레드가 부르지 않게 한다. */
    private val lanes: List<ExecutorService> = models.map { model ->
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "imprint-${model.name}").apply {
                isDaemon =
                    true
            }
        }
    }

    private val input = FloatArray(ImprintPreprocess.SAMPLES * PLANE)

    /** [input] 과 조각들을 공유하므로 동시 호출을 직렬화한다. */
    private val lock = Mutex()

    /** @param crop 검출기가 오려낸 투명 배경 낱알 이미지 */
    suspend fun read(crop: Bitmap): Result = lock.withLock {
        withContext(Dispatchers.Default) { readBlocking(crop) }
    }

    private fun readBlocking(crop: Bitmap): Result {
        val gray = crop.toGrayOnWhite()
        return readGray(gray).also { gray.release() }
    }

    /**
     * 흑백 크롭에서 바로 읽는다. 대조 테스트가 기준 구현과 **같은 입력**을 넣으려고 쓴다 — 비트맵을
     * 거치면 알파 합성 · 흑백 변환이 끼어 정본 `.npy` 와 화소 단위로 맞출 수 없다.
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
        val logits = runPipeline()
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

    /**
     * 묶음 6개를 p1 → p5 로 흘린다. 채널이 1 이라 NCHW 와 NHWC 가 같은 배열이다 — 그대로 넣는다.
     *
     * @return `[시간][후보][글자]` — ONNX 시절과 같은 시간축 먼저. p5 출력 `[T,24,37]` 을 후보 자리에 꽂는다.
     */
    private fun runPipeline(): Array<Array<FloatArray>> {
        val gpu = models.firstOrNull { it.onGpu }
        val outs = if (gpu != null) VisionBackend.whileOnGpu(gpu.name, gpu = true) { runOnLanes() } else runInOrder()
        val steps = outs[0].size / (CHUNK * CLASSES)
        return Array(steps) { t ->
            Array(ImprintPreprocess.SAMPLES) { n ->
                val from = (t * CHUNK + n % CHUNK) * CLASSES
                outs[n / CHUNK].copyOfRange(from, from + CLASSES)
            }
        }
    }

    /** 묶음이 조각 전용 스레드를 차례로 흐른다(클래스 KDoc). p2 만 입력이 둘(크롭 · 제어점)이다. */
    private fun runOnLanes(): List<FloatArray> {
        val chunks = (0 until ImprintPreprocess.SAMPLES / CHUNK).map { c ->
            val image = chunkOf(c)
            var flow = CompletableFuture.supplyAsync({ models[0].run(image)[0] }, lanes[0])
                .thenApplyAsync({ points -> models[1].run(image, points)[0] }, lanes[1])
            for (i in 2 until models.size) {
                flow = flow.thenApplyAsync({ x -> models[i].run(x)[0] }, lanes[i])
            }
            flow
        }
        // ⚠️ 하나가 실패해도 **나머지가 다 끝날 때까지** 기다린 뒤 던진다. 먼저 던지면 남은 묶음이 조각 스레드에서
        //    계속 돌아, 다음 판독 · close() 와 겹쳐 같은 모델을 건드린다.
        chunks.forEach { it.handle { _, _ -> }.join() }
        return chunks.map { it.awaitUnwrapped() }
    }

    /**
     * 다섯 조각이 **모두 CPU** 면 겹쳐 돌리지 않고 이 스레드에서 차례로 돈다 — 조각마다 4스레드라 겹치면 최대 20개가
     * 코어를 나눠 써 리틀코어로 넘친다(GPU 가 막힌 기기의 경로다).
     */
    private fun runInOrder(): List<FloatArray> = (0 until ImprintPreprocess.SAMPLES / CHUNK).map { c ->
        val image = chunkOf(c)
        var x = models[1].run(image, models[0].run(image)[0])[0]
        for (i in 2 until models.size) x = models[i].run(x)[0]
        x
    }

    private fun chunkOf(c: Int): FloatArray = input.copyOfRange(c * CHUNK * PLANE, (c + 1) * CHUNK * PLANE)

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

    /**
     * 조각 스레드가 멈출 때까지 기다린 뒤 모델을 닫는다 — 아직 도는 묶음이 닫힌 네이티브 모델을 건드리면 SIGSEGV 고,
     * 그 프로세스는 GPU 를 열었으니 GPU 크래시로 잘못 판정된다.
     *
     * 기다림은 **별도 스레드**에서 한다. 이 메서드는 화면을 떠날 때 메인 스레드(`onCleared`)에서 불려, 판독 중이면
     * 몇 초를 막아 ANR 이 날 수 있다.
     */
    override fun close() {
        lanes.forEach { it.shutdown() }
        Thread({
            lanes.forEach { it.awaitTermination(CLOSE_TIMEOUT_S, TimeUnit.SECONDS) }
            models.forEach { it.close() }
        }, "imprint-close").apply { isDaemon = true }.start()
    }

    /**
     * 돌고 있는 판독이 끝난 뒤 닫는다(조각을 순서대로 도는 CPU 경로까지) — 잠금을 잡고 닫으므로 네이티브 실행 한가운데서 모델이 닫히지 않는다.
     * 화면을 떠나 코루틴이 취소돼도 블로킹 실행은 멈추지 않는데, 그 중에 닫으면 SIGSEGV 고 GPU 크래시로 잘못 판정된다.
     */
    suspend fun closeWhenIdle() = lock.withLock { close() }

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
     * 전처리가 지배적이면 TTA 장수를 줄이는 것이 답이고, 추론이 지배적이면 실행 계획을 손봐야 한다.
     * 합계만 보면 둘을 구분할 수 없다.
     *
     * ⚠️ **모델 열기는 여기 없다.** 한 번만 드는 비용인데 여기 섞으면 알약마다 그만큼 든다고 잘못 읽힌다.
     */
    data class Timings(val preprocessMs: Long = 0, val inferenceMs: Long = 0, val decodeMs: Long = 0) {
        val totalMs: Long get() = preprocessMs + inferenceMs + decodeMs
    }

    companion object {
        /** 조각 파일 — 이 순서로 흐른다. 목록과 해시는 `app/models.md5`. */
        val PART_FILE_NAMES = listOf(
            "p1_loc.tflite",
            "p2_tps.tflite",
            "p3_trunk.tflite",
            "p4_frm.tflite",
            "p5_lstm_head.tflite"
        )

        /** [PART_FILE_NAMES] 와 같은 순서의 ML 실행 정밀도 계약. 바꾸면 정확도 보장이 없다(클래스 KDoc 표). */
        private val PLANS = listOf(
            RunPlan(Placement.GPU, GpuPrecision.FP16),
            RunPlan(Placement.CPU),
            RunPlan(Placement.GPU, GpuPrecision.FP32),
            RunPlan(Placement.GPU_CPU, GpuPrecision.FP16),
            RunPlan(Placement.CPU)
        )

        /** 조각 모델의 묶음 크기 — 변환 때 박혔다. 144 의 약수여야 한다. */
        private const val CHUNK = 24

        /** 글자 37종(CTC 빈칸 포함). */
        private const val CLASSES = 37

        private const val PLANE = ImprintPreprocess.SIDE * ImprintPreprocess.SIDE
        private const val NANOS_PER_MS = 1_000_000L

        /** 한 면 판독이 CPU 로도 5초 안팎이다. 그보다 넉넉히 기다린다. */
        private const val CLOSE_TIMEOUT_S = 15L

        /** 하나라도 못 열면 이미 연 것을 닫고 던진다 — 반쯤 열린 채 새지 않게. */
        private fun openAll(parts: List<File>): List<LiteRtModel> {
            val opened = ArrayList<LiteRtModel>(parts.size)
            var done = false
            try {
                parts.zip(PLANS).forEach { (file, plan) -> opened += LiteRtModel.open(file, plan) }
                done = true
            } finally {
                if (!done) opened.forEach { it.close() }
            }
            return opened
        }
    }
}

/** 조각 실패는 [ExecutionException] 으로 싸여 온다 — 원래 예외를 꺼내 던진다. */
private fun <T> CompletableFuture<T>.awaitUnwrapped(): T = try {
    get()
} catch (e: ExecutionException) {
    throw e.cause ?: e
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
