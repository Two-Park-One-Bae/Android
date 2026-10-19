package app.nursemate.core.vision.imprint

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.vision.GpuOverride
import app.nursemate.core.vision.VisionBackend
import java.io.File
import org.junit.After
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
 * | 모델 열기 | 조각 다섯을 여는 비용(GPU 셰이더 준비 포함). **한 번만** 든다 |
 * | 전처리 | TTA 144장. 지배적이면 장수를 줄이는 것이 답 |
 * | 추론 | 144장 한 배치. 지배적이면 스레드·배치를 손봐야 한다 |
 *
 * ## LiteRT G5 (NM-628)
 * 추론은 조각 다섯이 묶음 6개를 파이프라인으로 흘린다. ML 벤치 실측은 면당 0.4~1.2초(Exynos 2600 ~
 * SD 8 Gen 1, `_handoff/litert-ondevice-migration.md`). 이 테스트로 **앱 코드가 그 수준인지** 본다.
 *
 * ## 옛 실측 — ORT (Galaxy S24 · SM-S921N · Exynos 2400 · 4스레드 · TTA 144장)
 *
 * NM-534 로 [ImprintReader] 가 WebGPU + `cpu(/rnn/)` 로 열린다. 그 전후를 같이 적는다.
 * ```
 *                  ORT 1.22 · CPU      ORT 1.30 · WebGPU
 * 세션 생성         75 ~ 151 ms              223 ms   (한 번만)
 * 전처리               225 ms               225 ms   (장당 약 1.2 ms — 장수에 비례)
 * 추론           3683 ~ 3836 ms              1874 ms
 * 디코딩                 2 ms                 2 ms
 * ──────────────────────────────────────────────────
 * 한 면              약 4.0 s             약 2.1 s
 * 3정                약 12 s              약 6.3 s
 * ```
 * 답은 안 바뀐다 — 픽스처가 양쪽 다 `YS 0.969888` 이다.
 *
 * **추론이 여전히 지배적이다.** 전처리를 아무리 깎아도 의미가 없다. 연산량은 한 면에 약
 * **360 GFLOP**(Conv 330)이고, 그중 99%가 「한 면을 144번 읽는다」는 구조에서 나온다.
 *
 * 남은 지렛대는 **TTA 장수뿐**이다 — 24~144장이 크롭당 12.4 ms 로 완전 선형이라 배치 효율로
 * 깎을 여지가 없고, `rnn` 은 1796 ms 중 135 ms 라 손볼 대상이 아니다. 장수는 iOS·서버와 답을
 * 맞추는 문제라 144 로 두고 서버 연동 때 다시 본다(NM-528 결정 1).
 *
 * ⚠️ **에뮬레이터 값은 믿지 말 것.** 에뮬레이터는 데스크톱 CPU 위에서 돌아 실기기와 다르고 GPU(OpenCL)가 없다.
 *
 * ## 모델이 없으면 건너뛴다
 * ```
 * adb push app/src/main/assets/p?_*.tflite /sdcard/Android/data/app.nursemate.core.vision.test/files/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ImprintReaderBenchmark {

    /** 바꾼 전역 GPU 허용을 되돌린다 — 같은 계측 프로세스의 다음 테스트가 순서에 따라 CPU 로 돌지 않게. */
    @After
    fun restore() {
        VisionBackend.allowGpu = { true }
    }

    @Test
    fun `한_면_판독_소요를_단계별로_잰다`() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        // nm_gpu=false 면 CPU 로 잰다 — 저가 기기에서 GPU 가 CPU 보다 느린지 가리려고(2026-10-19 Test Lab).
        Log.i(TAG, "경로 ${GpuOverride.apply()}")

        val context = InstrumentationRegistry.getInstrumentation().context
        val parts = ImprintReader.PART_FILE_NAMES.map { File(context.getExternalFilesDir(null), it) }
        assumeTrue("조각이 없어 건너뛴다", parts.all { it.isFile && it.length() > 0 })

        val bytes = context.assets.open("imprint/input_gray.bin").use { it.readBytes() }
        val gray = Mat(ImprintPreprocess.SIDE, ImprintPreprocess.SIDE, CvType.CV_8UC1)
            .also { it.put(0, 0, bytes) }

        val openStart = System.nanoTime()
        val reader = ImprintReader(parts)
        val openMs = (System.nanoTime() - openStart) / 1_000_000

        try {
            // 첫 회는 네이티브 준비·JIT 가 섞여 대표성이 없다. 재지 않고 버린다.
            reader.readGray(gray)

            val runs = (1..ROUNDS).map { reader.readGray(gray).timings }
            val pre = runs.map { it.preprocessMs }
            val inf = runs.map { it.inferenceMs }
            val dec = runs.map { it.decodeMs }
            val tot = runs.map { it.totalMs }

            // 요청이 아니라 **잰 뒤의 실제 경로**를 남긴다 — 열기 · 실행 중 CPU 로 대체된 조각은 빠진다.
            Log.i(TAG, "실제 GPU 조각 ${reader.gpuParts.ifEmpty { listOf("없음") }.joinToString()}")
            Log.i(TAG, "모델 열기 ${openMs}ms (한 번만)")
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
