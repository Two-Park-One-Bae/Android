package app.nursemate.core.vision.imprint

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * 각인 판독 소요 측정 — NM-485 「저사양 기기에서 촬영 후 대기 시간」.
 *
 * ## 무엇을 가르나
 * 합계만 보면 줄일 곳을 못 정한다. 세 가지를 따로 잰다.
 *
 * | 구간 | 성격 |
 * |---|---|
 * | 세션 생성 | 21MB 모델을 여는 비용. **한 번만** 든다 |
 * | 전처리 | TTA 144장. 지배적이면 장수를 줄이는 것이 답 |
 * | 추론 | 144장 한 배치. 지배적이면 스레드·배치를 손봐야 한다 |
 *
 * ## 실측 (Galaxy S24 · SM-S921N · Exynos 2400 · 4스레드 · TTA 144장)
 * ```
 * 세션 생성    75 ~ 151 ms   (한 번만)
 * 전처리          225 ms     (장당 약 1.2 ms — 장수에 비례한다)
 * 추론      3683 ~ 3836 ms
 * 디코딩            2 ms
 * ───────────────────────────
 * 한 면        약 4.0 s  ·  3정 약 12 s
 * ```
 * **추론이 93%다.** 전처리를 아무리 깎아도 의미가 없고, 줄이려면 TTA 장수를 줄이거나
 * 실행 백엔드를 바꿔야 한다. 연산량은 한 면에 약 **360 GFLOP**(Conv 330)이고, 그중 99%가
 * 「한 면을 144번 읽는다」는 구조에서 나온다.
 *
 * ⚠️ **에뮬레이터 값은 믿지 말 것.** 에뮬레이터는 데스크톱 CPU 위에서 돌아 실기기보다
 * 빠르다. 위 숫자가 실기기 값이고, **저사양 기기는 이보다 느리다** — DoD 가 요구하는 그
 * 값은 아직 없다(armeabi-v7a 기기에서 세션 생성이 `SIGBUS` 로 죽는 건이 먼저다.
 * [OrtSessionOpenTest] 참조).
 *
 * ## 모델이 없으면 건너뛴다
 * ```
 * adb push reader_ep60_s1.onnx /sdcard/Android/data/app.nursemate.core.vision.test/files/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ImprintReaderBenchmark {

    @Test
    fun `한_면_판독_소요를_단계별로_잰다`() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }

        val context = InstrumentationRegistry.getInstrumentation().context
        val model = File(context.getExternalFilesDir(null), ImprintReader.MODEL_FILE_NAME)
        assumeTrue("모델이 없어 건너뛴다", model.isFile && model.length() > 0)

        val bytes = context.assets.open("imprint/input_gray.bin").use { it.readBytes() }
        val gray = Mat(ImprintPreprocess.SIDE, ImprintPreprocess.SIDE, CvType.CV_8UC1)
            .also { it.put(0, 0, bytes) }

        val openStart = System.nanoTime()
        val reader = ImprintReader(model)
        val openMs = (System.nanoTime() - openStart) / 1_000_000

        try {
            // 첫 회는 네이티브 준비·JIT 가 섞여 대표성이 없다. 재지 않고 버린다.
            reader.readGray(gray)

            val runs = (1..ROUNDS).map { reader.readGray(gray).timings }
            val pre = runs.map { it.preprocessMs }
            val inf = runs.map { it.inferenceMs }
            val dec = runs.map { it.decodeMs }
            val tot = runs.map { it.totalMs }

            Log.i(TAG, "세션 생성 ${openMs}ms (한 번만)")
            Log.i(TAG, "전처리  중앙값 ${pre.median()}ms  최소 ${pre.min()}  최대 ${pre.max()}")
            Log.i(TAG, "추론    중앙값 ${inf.median()}ms  최소 ${inf.min()}  최대 ${inf.max()}")
            Log.i(TAG, "디코딩  중앙값 ${dec.median()}ms  최소 ${dec.min()}  최대 ${dec.max()}")
            Log.i(TAG, "한 면 합계 중앙값 ${tot.median()}ms · 알약 3개면 ${tot.median() * 3}ms")
        } finally {
            reader.close()
            gray.release()
        }
    }

    /**
     * 스레드 수를 쓸어 본다.
     *
     * Mac(ORT CPU)에서 1→2→4→8 이 3212 → 1757 → 963 → 648ms 로 **거의 선형**이라, 기기에서도
     * 스레드를 늘리면 줄어들 것으로 봤다. **실기기는 달랐다.**
     *
     * ```
     * Galaxy S24 추론 중앙값   1스레드 13345 · 2스레드 6765 · 4스레드 4124 · 6스레드 6939 · 8스레드 8059 ms
     * ```
     * 4에서 최소이고 6·8 에서 **되레 느려진다.** big.LITTLE 이라 스레드를 늘리면 느린 코어가
     * 섞여 배리어를 끌고, Mac 의 동질 코어에서 본 선형은 그대로 옮겨오지 않는다.
     *
     * ⚠️ 기본 4 는 원래 **검출 모델 기준**이었는데(`PillDetector.DEFAULT_THREADS` — seg 도 6·8 에서
     * 느려졌다), 각인은 CRNN+CTC 로 모양이 다르므로 물려받을 근거가 없어 따로 쟀다. **같은 4가 답이다.**
     */
    @Test
    fun `스레드_수를_쓸어_본다`() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        val context = InstrumentationRegistry.getInstrumentation().context
        val model = File(context.getExternalFilesDir(null), ImprintReader.MODEL_FILE_NAME)
        assumeTrue("모델이 없어 건너뛴다", model.isFile && model.length() > 0)

        val bytes = context.assets.open("imprint/input_gray.bin").use { it.readBytes() }
        val gray = Mat(ImprintPreprocess.SIDE, ImprintPreprocess.SIDE, CvType.CV_8UC1)
            .also { it.put(0, 0, bytes) }

        Log.i(TAG, "코어 ${Runtime.getRuntime().availableProcessors()}개")
        for (threads in intArrayOf(1, 2, 4, 6, 8)) {
            ImprintReader(model, threads).use { reader ->
                reader.readGray(gray)
                val runs = (1..SWEEP_ROUNDS).map { reader.readGray(gray).inferenceMsOf() }
                Log.i(TAG, "${threads}스레드 추론 중앙값 ${runs.median()}ms  최소 ${runs.min()} 최대 ${runs.max()}")
            }
        }
        gray.release()
    }

    private fun ImprintReader.Result.inferenceMsOf() = timings.inferenceMs

    private fun List<Long>.median(): Long = sorted().let {
        if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2
    }

    private companion object {
        const val TAG = "NM485"
        const val ROUNDS = 7
        const val SWEEP_ROUNDS = 5
    }
}
