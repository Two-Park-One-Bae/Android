package app.nursemate.core.vision.mark

import android.graphics.Bitmap
import app.nursemate.core.vision.GpuPrecision
import app.nursemate.core.vision.LiteRtModel
import app.nursemate.core.vision.Placement
import app.nursemate.core.vision.RunPlan
import app.nursemate.core.vision.VisionBackend
import app.nursemate.core.vision.nchwToNhwc
import java.io.Closeable
import java.io.File
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat

/**
 * 알약 한 면의 **마크**를 읽는다 — ConvNeXt-Tiny species 모델 온디바이스(NM-515).
 *
 * 기준은 ML 레포 `models/mark/20260925-convnext-species/mark_species_infer.py` 다. 전처리는
 * [MarkPreprocess], 유무를 계약값으로 접는 규칙은 [MarkPresence] 에 있고 여기서는 둘을 잇고
 * 추론만 한다.
 *
 * ## 한 번에 8장을 돌린다
 * 45° 간격 8방향이다. 각인(24방향 × 6배율 = 144장)과 달리 배율은 쓰지 않는다 — 이 모델이
 * 그렇게 학습됐다.
 *
 * ## 답 셋이 한 추론에서 나온다
 * | | 쓰는 곳 |
 * |---|---|
 * | 유무 `1 − P(없음)` | 서버 후보를 좁히는 `hasMark`. [MarkPresence] 가 접는다 |
 * | 종 번호 | 사람에게 보여 줄 식약처 마크 그림 |
 * | 임베딩 8×768 | 서버가 카탈로그와 코사인으로 후보를 **재정렬**한다 |
 *
 * 임베딩이 셋 중 제일 예민하다. 전처리가 한 획이라도 다르면 예외가 아니라 **후보 순서로만**
 * 드러난다 — 그래서 [MarkPreprocessParityTest] 가 단계마다 화소를 맞춘다.
 *
 * ## 확률은 평균, 임베딩은 8개를 그대로
 * 정본이 그렇다. 유무·종은 8방향 소프트맥스를 평균해 한 값으로 접지만, 임베딩은 서버가
 * 8개 각각을 카탈로그와 대조해 **최댓값**을 쓰므로 접지 않고 그대로 보낸다.
 *
 * ## L2 정규화를 다시 하지 않는다
 * 모델이 이미 한 값이다(`torch.nn.functional.normalize`). 앱에서 또 걸면 값이 달라진다.
 *
 * ## LiteRT · M4 (NM-628)
 * 파일은 `mark_m4_f16w.tflite`(가중치 fp16 저장). 입력이 **NHWC** `[8,224,224,3]` 이라 전처리가 쓴 NCHW 를
 * 넣기 직전에 돌린다. M4 는 Xclipse(Exynos GPU)가 틀리는 7×7 depthwise 를 피한 판이고, 공식 세트 564면에서
 * 유무 · 종 100% · 임베딩 cos 최소 1.0000 이라 **임베딩 공간이 같다** — 서버에 보내는 [MODEL_VERSION] 은 그대로다.
 *
 * 실행은 GPU **FP32**(ML 계약). 8 Elite 에서 FP16 이면 회전 13~19% 의 임베딩이 실행마다 틀어졌다(cos 0.20~0.46).
 */
class MarkReader(modelFile: File) : Closeable {

    init {
        // 네이티브 로딩은 여기서 한 번만. 앱 시작에 걸면 마크를 쓰지 않는 사용자도 비용을 낸다.
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
    }

    private val model = LiteRtModel.open(modelFile, PLAN)

    private val input = FloatArray(MarkPreprocess.BATCH)
    private val nhwc = FloatArray(MarkPreprocess.BATCH)

    /** [input] 을 공유하므로 동시 호출을 직렬화한다. */
    private val lock = Mutex()

    /**
     * @param crop 마스크로 오려낸 낱알 한 면. 배경이 투명한 `ARGB_8888` 을 받는다
     *             (`PillDetector` 의 `DetectedPill.crop`).
     */
    suspend fun read(crop: Bitmap): Result = lock.withLock {
        withContext(Dispatchers.Default) {
            val gray = MarkPreprocess.toGrayOnWhite(crop)
            readGray(gray).also { gray.release() }
        }
    }

    /**
     * 회색조 한 장에서 바로 읽는다 — 비트맵 디코딩을 건너뛰는 자리.
     *
     * 대조 테스트가 쓴다. 픽스처를 PNG 로 두면 파이썬과 Android 의 **디코더 차이가 섞여**
     * 전처리가 아닌 것을 재게 되므로, 기준값은 숫자 배열로 두고 여기로 들어온다.
     */
    internal fun readGray(gray: Mat): Result {
        val t0 = System.nanoTime()
        val square = MarkPreprocess.toSquare(gray)
        for (i in 0 until MarkPreprocess.ROTATIONS) {
            val rotated = MarkPreprocess.rotate(square, i * 360.0 / MarkPreprocess.ROTATIONS)
            val clahe = MarkPreprocess.applyClahe(rotated)
            MarkPreprocess.writeNormalized(clahe, input, i * MarkPreprocess.PLANE)
            rotated.release()
            clahe.release()
        }
        square.release()

        val t1 = System.nanoTime()
        val (logits, embedding) = runSession()

        val t2 = System.nanoTime()
        val mean = meanProbability(logits)
        // 0번은 「없음」이다. 종은 나머지에서 고른다.
        var species = 1
        for (i in 2 until mean.size) if (mean[i] > mean[species]) species = i
        val t3 = System.nanoTime()

        return Result(
            presence = 1f - mean[NONE_CLASS],
            species = species,
            speciesProbability = mean[species],
            embedding = embedding,
            timings = Timings(
                preprocessMs = (t1 - t0) / NANOS_PER_MS,
                inferenceMs = (t2 - t1) / NANOS_PER_MS,
                decodeMs = (t3 - t2) / NANOS_PER_MS
            )
        )
    }

    /** @return `logits` [8][104] 와 `embedding` 을 8×768 로 평탄화한 것(회전이 바깥). */
    private fun runSession(): Pair<Array<FloatArray>, FloatArray> {
        val n = MarkPreprocess.ROTATIONS
        val side = MarkPreprocess.SIDE
        val outputs = VisionBackend.whileOnGpu(model.name, model.onGpu) {
            model.run(nchwToNhwc(input, nhwc, n = n, c = 3, plane = side * side))
        }
        // 출력은 크기로 가른다 — 8×104 와 8×768 이라 겹치지 않는다.
        val flat = outputs.first { it.size != n * EMBEDDING_DIM }
        val embedding = outputs.first { it.size == n * EMBEDDING_DIM }
        val classes = flat.size / n
        val logits = Array(n) { flat.copyOfRange(it * classes, (it + 1) * classes) }
        return logits to embedding
    }

    /**
     * `lo.softmax(-1).numpy().mean(0)` — 방향마다 소프트맥스를 걸고 평균한다.
     *
     * ⚠️ **평균 뒤에 소프트맥스를 걸면 안 된다.** 순서가 바뀌면 값이 달라진다.
     */
    private fun meanProbability(logits: Array<FloatArray>): FloatArray {
        val out = FloatArray(logits[0].size)
        for (row in logits) {
            val max = row.max()
            var sum = 0.0
            val exp = FloatArray(row.size) { exp((row[it] - max).toDouble()).toFloat() }
            for (v in exp) sum += v
            for (j in out.indices) out[j] += (exp[j] / sum).toFloat()
        }
        for (j in out.indices) out[j] /= logits.size
        return out
    }

    override fun close() {
        model.close()
    }

    /**
     * 돌고 있는 판독이 끝난 뒤 닫는다 — 잠금을 잡고 닫으므로 네이티브 실행 한가운데서 모델이 닫히지 않는다.
     * 화면을 떠나 코루틴이 취소돼도 블로킹 실행은 멈추지 않는데, 그 중에 닫으면 SIGSEGV 고 GPU 크래시로 잘못 판정된다.
     */
    suspend fun closeWhenIdle() = lock.withLock { close() }

    /**
     * 판독 결과.
     *
     * @param presence `1 − P(없음)`. **이 값을 그대로 서버에 보내지 않는다** — [MarkPresence] 가
     *                 같은 면의 각인 유무를 보고 `hasMark` 로 접는다
     * @param species 종 번호(1..103). 103 은 「기타 마크」다. `species_map.json` 이 식약처 그림 id 로 옮긴다
     * @param embedding 8 × 768 을 회전 바깥으로 평탄화한 것. 모델이 L2 정규화한 값이라 **다시 걸지 않는다**
     */
    data class Result(
        val presence: Float,
        val species: Int,
        val speciesProbability: Float,
        val embedding: FloatArray,
        val timings: Timings = Timings()
    ) {
        // FloatArray 가 있어 data class 의 기본 equals 가 참조 비교라 쓸모없다. 직접 맞춘다.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Result) return false
            return presence == other.presence &&
                species == other.species &&
                speciesProbability == other.speciesProbability &&
                embedding.contentEquals(other.embedding) &&
                timings == other.timings
        }

        override fun hashCode(): Int {
            var result = presence.hashCode()
            result = 31 * result + species
            result = 31 * result + speciesProbability.hashCode()
            result = 31 * result + embedding.contentHashCode()
            result = 31 * result + timings.hashCode()
            return result
        }
    }

    /** 단계별 소요. 어디가 비싼지 알아야 줄일 곳을 정한다. 세션 생성은 여기 없다 — 한 번만 드는 비용이다. */
    data class Timings(val preprocessMs: Long = 0, val inferenceMs: Long = 0, val decodeMs: Long = 0) {
        val totalMs: Long get() = preprocessMs + inferenceMs + decodeMs
    }

    companion object {
        /** assets · DVC 에서 이 이름으로 온다. 가중치 fp16 저장 · 57MB. 목록과 해시는 `app/models.md5`. */
        const val MODEL_FILE_NAME = "mark_m4_f16w.tflite"

        /** ML 실행 정밀도 계약 — GPU FP32. 검출과 같이 GPU+CPU 로 연다(ML 벤치가 그렇게 쟀다 · `PillDetector.PLAN`). */
        private val PLAN = RunPlan(Placement.GPU_CPU, GpuPrecision.FP32)

        /**
         * 임베딩을 뽑은 모델 버전 — **ML 레포 모델 폴더 이름 그대로**다.
         *
         * 후보 조회 요청의 `markEmbeddingModel` 로 나가고 서버가 카탈로그 임베딩과 판을
         * 맞춘다. 버전이 다른 임베딩끼리는 코사인이 성립하지 않는데 **에러 없이 순서만**
         * 틀어지기 때문이다(NM-533).
         *
         * ⚠️ **모델 파일을 바꾸면 이 값도 같이 바꾼다.** 파일 이름에는 날짜가 없어 둘이
         * 조용히 갈릴 수 있다 — 갈리면 서버가 엉뚱한 카탈로그와 견준다.
         *
         * LiteRT M4(NM-628)로 파일은 바뀌었지만 **값은 그대로 둔다.** 같은 학습 가중치를 옮긴 것이고 공식
         * 세트 564면 임베딩 cos 최소 1.0000 이라 카탈로그와 같은 공간이다. 재학습이면 그때 바꾼다.
         */
        const val MODEL_VERSION = "20260925-convnext-species"

        /** 임베딩 차원. */
        const val EMBEDDING_DIM = 768

        /** 클래스 0 = 「마크 없음」. `species_map.json` 의 `none`. */
        private const val NONE_CLASS = 0

        private const val NANOS_PER_MS = 1_000_000L
    }
}
