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
 * ⚠️ **이 숫자는 상한이 아니라 하한이다.** 이 테스트가 도는 곳은 보통 개발용 에뮬레이터이고,
 * 그건 데스크톱 CPU 위에서 돈다. **저사양 실기기는 훨씬 느리다** — DoD 가 요구하는 것은
 * 실기기 값이고, 여기 숫자는 그 전에 「어디가 비싼가」를 보려는 것이다.
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

    private fun List<Long>.median(): Long = sorted().let {
        if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2
    }

    private companion object {
        const val TAG = "NM485"
        const val ROUNDS = 7
    }
}
