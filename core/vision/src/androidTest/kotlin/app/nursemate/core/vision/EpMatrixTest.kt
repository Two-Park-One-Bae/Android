package app.nursemate.core.vision

import ai.onnxruntime.OrtEnvironment
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.vision.imprint.ImprintPreprocess
import app.nursemate.core.vision.imprint.ImprintReader
import app.nursemate.core.vision.mark.MarkPreprocess
import app.nursemate.core.vision.mark.MarkReader
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * **EP × 모델을 이 기기에서 전부 재고, 답이 맞는지까지 본다** — NM-577.
 *
 * ## 왜
 * 지금의 EP 비교표(`OrtBackend` KDoc)가 **Galaxy S24(Exynos 2400 · Xclipse 940) 한 대**에서
 * 나왔다. 그 한 대로 「NNAPI 실패」·「XNNPACK 더 느림」을 정하고 WebGPU 를 골랐는데,
 * 2026-10-13 Adreno 에서 각인이 깨지면서 그 표의 적용 범위가 드러났다. 퀄컴 NNAPI 는
 * Hexagon NPU 로 내려가는 **다른 구현**이고, 재 본 적이 없다.
 *
 * ## 메서드를 EP × 모델로 가른다
 * 한 메서드에서 순회하면 하나가 세그폴트할 때 나머지를 못 본다. Test Lab `--use-orchestrator`
 * 가 **메서드마다 프로세스를 새로 띄우므로** 갈라 두면 죽어도 끝까지 돈다 — 기기당 1실행으로
 * 전수 조사가 된다(실기기 할당량이 하루 5회뿐이다).
 *
 * ## ⚠️ 속도만 보지 않는다
 * 빠른 EP 를 찾는 것이 아니라 **빠르면서 답이 맞는** EP 를 찾는 것이다. NNAPI 는 fp16 으로
 * 떨어뜨리는 경우가 많고, 각인은 0.949 채택 임계가 있어 확률이 조금만 흔들려도 **글자가
 * 뒤집힌다.** 그래서 기준값 대조를 그대로 건다.
 *
 * 결과는 logcat `NM-EPMATRIX` 한 줄씩이다.
 */
@RunWith(AndroidJUnit4::class)
class EpMatrixTest {

    private lateinit var imprintModel: File
    private lateinit var markModel: File
    private lateinit var gray: Mat
    private lateinit var fixtures: JSONObject
    private lateinit var answers: JSONObject

    @Before
    fun setUp() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        val context = InstrumentationRegistry.getInstrumentation().context
        val dir = context.getExternalFilesDir(null)

        imprintModel = File(dir, ImprintReader.MODEL_FILE_NAME)
        markModel = File(dir, MarkReader.MODEL_FILE_NAME)
        assumeTrue("모델이 없어 건너뛴다", imprintModel.isFile && markModel.isFile)

        val bytes = context.assets.open("imprint/input_gray.bin").use { it.readBytes() }
        gray = Mat(ImprintPreprocess.SIDE, ImprintPreprocess.SIDE, CvType.CV_8UC1)
            .also { it.put(0, 0, bytes) }

        fixtures = JSONObject(context.assets.open("mark/fixtures.json").use { it.readBytes().decodeToString() })
        answers = JSONObject(context.assets.open("mark/answers.json").use { it.readBytes().decodeToString() })

        val eps = runCatching { OrtEnvironment.getAvailableProviders().joinToString() }.getOrElse { "조회 실패" }
        Log.i(TAG, "기기 ${Build.MODEL} · SoC ${soc()} · 가용 EP $eps")
    }

    @After
    fun tearDown() {
        OrtBackend.forcedEp = null
        if (::gray.isInitialized) gray.release()
    }

    // ── 각인 ─────────────────────────────────────────────────────────

    @Test fun `각인_CPU`() = imprint(Ep.CPU)

    @Test fun `각인_WEBGPU`() = imprint(Ep.WEBGPU)

    @Test fun `각인_NNAPI`() = imprint(Ep.NNAPI)

    @Test fun `각인_XNNPACK`() = imprint(Ep.XNNPACK)

    // ── 마크 ─────────────────────────────────────────────────────────

    @Test fun `마크_CPU`() = mark(Ep.CPU)

    @Test fun `마크_WEBGPU`() = mark(Ep.WEBGPU)

    @Test fun `마크_NNAPI`() = mark(Ep.NNAPI)

    @Test fun `마크_XNNPACK`() = mark(Ep.XNNPACK)

    // ── 거들기 ───────────────────────────────────────────────────────

    /** 기준값은 `ImprintReaderParityTest` 와 같다 — `sample_crop` → `YS`, 0.969883. */
    private fun imprint(ep: Ep) {
        OrtBackend.forcedEp = ep
        val opened = System.nanoTime()
        ImprintReader(imprintModel).use { reader ->
            val ready = System.nanoTime()
            // ⚠️ **첫 실행은 버린다.** WebGPU 가 여기서 셰이더를 컴파일한다 — 포함해 재면
            // 몇 배 느리게 나온다(S24 각인: 기록 1796ms 대 예열 없는 측정 5812ms).
            reader.readGray(gray)
            val result = reader.readGray(gray)
            report("각인", ep, openMs = ms(opened, ready), runMs = result.timings.totalMs)

            assertEquals("[$ep] 각인", "YS", result.imprint)
            assertEquals("[$ep] best_pair", "YS", result.pair)
            assertEquals("[$ep] 점수", 0.969883f, result.score, 1e-4f)
        }
    }

    /** 기준값은 `MarkReaderParityTest` 와 같다 — 종 일치, 유무 ±5e-3. */
    private fun mark(ep: Ep) {
        OrtBackend.forcedEp = ep
        val opened = System.nanoTime()
        MarkReader(markModel).use { reader ->
            val ready = System.nanoTime()
            // 각인과 같은 이유로 예열한다. 마크는 케이스가 여섯이라 영향이 작지만 기준을 맞춘다.
            MarkPreprocess.toGrayOnWhite(
                loadCrop(WARMUP_CASE, fixtures.getJSONObject("cases").getJSONObject(WARMUP_CASE))
            )
                .also {
                    reader.readGray(it)
                    it.release()
                }
            var total = 0L
            val cases = fixtures.getJSONObject("cases")
            for (name in cases.keys()) {
                val case = cases.getJSONObject(name)
                val crop = MarkPreprocess.toGrayOnWhite(loadCrop(name, case))
                val actual = reader.readGray(crop).also { crop.release() }
                total += actual.timings.totalMs

                val expected = answers.getJSONObject(name)
                assertEquals("[$ep] $name 종", expected.getInt("species"), actual.species)
                assertTrue(
                    "[$ep] $name 유무 기준 ${expected.getDouble("presence")} · 실제 ${actual.presence}",
                    abs(expected.getDouble("presence").toFloat() - actual.presence) < PRESENCE_TOLERANCE
                )
            }
            report("마크", ep, openMs = ms(opened, ready), runMs = total)
        }
    }

    private fun loadCrop(name: String, case: JSONObject): Bitmap {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("mark/$name.rgba").use { it.readBytes() }
        return Bitmap.createBitmap(case.getInt("width"), case.getInt("height"), Bitmap.Config.ARGB_8888)
            .apply {
                setPremultiplied(false)
                copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            }
    }

    /** 세션 생성과 추론을 **갈라서** 적는다. 모델을 여는 값은 한 번만 드는데 섞으면 알약마다 든다고 잘못 읽힌다. */
    private fun report(model: String, ep: Ep, openMs: Long, runMs: Long) =
        Log.i(TAG, "$model | $ep | 세션생성 ${openMs}ms | 추론 ${runMs}ms | ${Build.MODEL} ${soc()}")

    private fun ms(from: Long, to: Long) = (to - from) / 1_000_000

    private fun soc(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
    } else {
        Build.HARDWARE
    }

    private companion object {
        const val TAG = "NM-EPMATRIX"

        /** 예열에 쓸 케이스. 어느 것이든 되지만 고정해야 측정이 재현된다. */
        const val WARMUP_CASE = "mark_set-084_p1_back"

        /** `MarkReaderParityTest` 와 같은 값. 실측 최대 1.29e-3 에 4배 여유다. */
        const val PRESENCE_TOLERANCE = 5e-3f
    }
}
