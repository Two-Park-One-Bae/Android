package app.nursemate.core.vision.imprint

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.vision.OrtBackend
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * **각인이 Adreno 에서 왜 실패하는지**를 가린다 — NM-577.
 *
 * 실패는 계산이 틀린 것이 아니라 ORT 내부에서 없는 키를 찾는 것이다:
 *
 * ```
 * Non-zero status code returned while running Conv node.
 * Name:'/cnn/cnn.1/cnn.1.0/Conv_token_37'
 * Status Message: absl::container_internal::raw_hash_map<>::at
 * ```
 *
 * `absl::flat_hash_map::at()` 은 **키가 없을 때** 터진다. 그리고 `_token_37` 은 모델에 없는
 * 이름이다(`strings` 로 확인) — ORT 가 **그래프를 쪼개면서** 붙인 것이다.
 *
 * ## 각인만 다른 점 셋
 * 마크는 같은 WebGPU 로 Adreno 740 · 750 양쪽에서 **답까지 정확히** 나온다. 각인만 깨지므로
 * 둘의 차이에 답이 있다.
 *
 * | | 각인 | 마크 |
 * |---|---|---|
 * | 그래프 분할 | **`cpu(/rnn/)`** | 없음 |
 * | 배치 | **144** | 8 |
 * | 입력 채널 | **1** (회색조) | 3 |
 *
 * ## 변형을 메서드로 가른다
 * 한 메서드 안에서 순회하면 하나가 세그폴트할 때 나머지를 못 본다. Test Lab 은
 * `--use-orchestrator` 로 **메서드마다 프로세스를 새로 띄우므로**, 갈라 두면 죽어도 나머지가 돈다.
 *
 * ⚠️ **답을 같이 본다.** 안 죽는 설정을 찾는 것이 목적이 아니라, **안 죽으면서 답이 맞는**
 * 설정을 찾는 것이다. 각인은 0.949 채택 임계가 있어 확률이 조금만 흔들려도 글자가 뒤집힌다.
 */
@RunWith(AndroidJUnit4::class)
class ImprintVariantTest {

    private lateinit var model: File
    private lateinit var gray: Mat

    @Before
    fun setUp() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        val context = InstrumentationRegistry.getInstrumentation().context
        model = File(context.getExternalFilesDir(null), ImprintReader.MODEL_FILE_NAME)
        assumeTrue("모델이 없어 건너뛴다", model.isFile && model.length() > 0)

        val bytes = context.assets.open("imprint/input_gray.bin").use { it.readBytes() }
        gray = Mat(ImprintPreprocess.SIDE, ImprintPreprocess.SIDE, CvType.CV_8UC1)
            .also { it.put(0, 0, bytes) }
    }

    /** 기준선. 여기서 틀리면 기기나 픽스처가 문제지 EP 문제가 아니다. */
    @Test
    fun `A_CPU`() {
        OrtBackend.webGpuEnabled = false
        read("CPU")
    }

    /** 지금 운영 설정. Adreno 740·750 에서 이것이 깨진다. */
    @Test
    fun `B_WebGPU_현재설정`() {
        OrtBackend.webGpuEnabled = true
        read("WebGPU · cpu(/rnn/)")
    }

    /**
     * 분할을 빼 본다 — `_token_` 이 분할의 표식이므로 제일 유력한 가설이다.
     *
     * 통과해도 **그대로 쓸 수는 없다**: 분할 없는 각인은 S24 에서 9943 ms 로 CPU(3472 ms)보다
     * 느리다. 원인을 좁히는 데 쓴다.
     */
    @Test
    fun `C_WebGPU_분할없이`() {
        OrtBackend.webGpuEnabled = true
        read("WebGPU · 분할 없음", cpuNodes = null)
    }

    /**
     * WebGPU EP 는 기본이 NHWC 다. 채널이 1개뿐인 입력에서 레이아웃 관련 Conv 버그가 보고된
     * 적이 있고(`preferredLayout=NCHW` 로 피한 사례), 각인 입력이 정확히 1채널이다.
     */
    @Test
    fun `D_WebGPU_NCHW`() {
        OrtBackend.webGpuEnabled = true
        read("WebGPU · NCHW", options = mapOf("preferredLayout" to "NCHW"))
    }

    /** 둘 다 걸어 본다. 하나로 안 되면 조합이 답일 수 있다. */
    @Test
    fun `E_WebGPU_NCHW_분할없이`() {
        OrtBackend.webGpuEnabled = true
        read("WebGPU · NCHW · 분할 없음", cpuNodes = null, options = mapOf("preferredLayout" to "NCHW"))
    }

    /**
     * **배치를 쪼갠다** — 엑시노스 2600 에서 나온 빈 답을 겨눈다 (2026-10-14).
     *
     * `s5e9965` WebGPU 가 39회 중 3회 **답 null · 점수 0.0000** 을 냈다. 예외도 크래시도 로그도
     * 없다. 실패한 세 번은 전부 추론이 가장 느린 구간(3804·3884·3888 ms)이었는데, 같은 느린
     * 구간의 3412~3590 ms 는 통과했으므로 **느린 것 자체가 원인은 아니다.**
     *
     * 각인만 깨지고 마크·검출은 한 번도 안 깨진 것이 단서다.
     *
     * | | 검출 | 마크 | 각인 |
     * |---|---|---|---|
     * | 배치 | 1 | 8 | **144** |
     * | 실패 | 0/6 | 0/39 | **3/39** |
     *
     * 144 는 `ROTATIONS(24) × ZOOMS(6)` TTA 다. 쪼개도 **후보 144개를 다 쓰므로 답이 같아야
     * 한다** — 그래서 다른 변형과 똑같이 0.969883 을 단정한다. 빈 답이 사라지면 배치가 원인이다.
     *
     * ⚠️ **이 테스트는 고쳐졌는지를 증명하지 않는다** — 한 번 읽고 끝이다. 실패가 확률적이라
     *    (배치 144 에서 225회 중 30회) 한 번 통과는 아무것도 말하지 않는다. 여기서 보는 것은
     *    **쪼개 돌린 뒤 이어 붙인 답이 같은가**뿐이다. 빈 답이 사라졌다는 근거는 반복 측정이다 —
     *    배치 72 를 718회, 36 을 675회 돌려 실패 0 ([ImprintReader] 의 `batch` KDoc).
     */
    @Test
    fun `F_WebGPU_배치36`() {
        OrtBackend.webGpuEnabled = true
        read("WebGPU · 배치 36", batch = 36)
    }

    /** 36 과 144 사이. 실패율이 배치에 비례하는지 본다 — 가르는 선이 어디인지도 알고 싶다. */
    @Test
    fun `G_WebGPU_배치72`() {
        OrtBackend.webGpuEnabled = true
        read("WebGPU · 배치 72", batch = 72)
    }

    /** 쪼개기 자체가 답을 바꾸지 않는다는 대조 — 여기서 틀리면 병합 코드가 틀린 것이다. */
    @Test
    fun `H_CPU_배치36`() {
        OrtBackend.webGpuEnabled = false
        read("CPU · 배치 36", batch = 36)
    }

    private fun read(
        label: String,
        cpuNodes: String? = ImprintReader.RNN_ON_CPU,
        options: Map<String, String> = emptyMap(),
        // 운영값으로 잰다. 144 는 엑시노스 2600 WebGPU 에서 빈 답을 내는 설정이다(NM-602).
        batch: Int = ImprintReader.DEFAULT_BATCH
    ) {
        val started = System.nanoTime()
        val result = ImprintReader(model, cpuNodes = cpuNodes, webGpuOptions = options, batch = batch)
            .use { it.readGray(gray) }
        val elapsed = (System.nanoTime() - started) / 1_000_000

        Log.i(TAG, "[$label] 각인=${result.imprint} pair=${result.pair} 점수=${result.score} ${elapsed}ms")

        assertEquals("[$label] 확신 글자", "YS", result.imprint)
        assertEquals("[$label] best_pair", "YS", result.pair)
        assertEquals("[$label] best_pair 점수", 0.969883f, result.score, 1e-4f)
    }

    private companion object {
        const val TAG = "NM-VARIANT"
    }
}
