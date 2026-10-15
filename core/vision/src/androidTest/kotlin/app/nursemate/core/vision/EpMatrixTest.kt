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

        // EPContext 캐시는 **앱이 쓸 수 있는 곳**이어야 한다. 모델이 놓인 외부 저장소는
        // adb 로 밀어 넣은 자리라 쓰기 권한이 없을 수 있다.
        OrtBackend.contextCacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        // HTP 가 DSP skel 을 찾는 자리. 여기를 안 주면 EP 가 노드를 하나도 안 가져가고
        // **CPU 로 돌면서 성공한다** — [OrtBackend.qnnNativeLibDir] KDoc 참조.
        OrtBackend.qnnNativeLibDir =
            File(InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.nativeLibraryDir)

        val eps = runCatching { OrtEnvironment.getAvailableProviders().joinToString() }.getOrElse { "조회 실패" }
        Log.i(TAG, "기기 ${Build.MODEL} · SoC ${soc()} · 가용 EP $eps")
    }

    @After
    fun tearDown() {
        OrtBackend.forcedEp = null
        OrtBackend.contextCacheDir = null
        OrtBackend.qnnNativeLibDir = null
        if (::gray.isInitialized) gray.release()
    }

    // ── 각인 ─────────────────────────────────────────────────────────

    @Test fun `각인_CPU`() = imprint(Ep.CPU)

    @Test fun `각인_WEBGPU`() = imprint(Ep.WEBGPU)

    @Test fun `각인_NNAPI`() = imprint(Ep.NNAPI)

    @Test fun `각인_XNNPACK`() = imprint(Ep.XNNPACK)

    @Test fun `각인_NNAPI_NO_CPU`() = imprint(Ep.NNAPI_NO_CPU)

    @Test fun `각인_NNAPI_SPLIT`() = imprint(Ep.NNAPI_SPLIT)

    @Test fun `각인_XNNPACK_TUNED`() = imprint(Ep.XNNPACK_TUNED)

    @Test fun `각인_QNN`() = imprint(Ep.QNN)

    @Test fun `각인_QNN_FP32`() = imprint(Ep.QNN_FP32)

    @Test fun `각인_QNN_FP16`() = imprint(Ep.QNN_FP16)

    @Test fun `각인_QNN_RNN_CPU`() = imprint(Ep.QNN_RNN_CPU)

    @Test fun `각인_QNN_STN_CPU`() = imprint(Ep.QNN_STN_CPU)

    /*
     * **TPS 격자만 fp32 로 남긴 변형** — 각인이 QNN 에서 채택 임계를 못 넘는 것을 겨눈다.
     *
     * 기준 모델은 QNN 에서 pair 를 YS 로 맞게 고르고도 점수가 **0.9436** 으로
     * [ImprintDecoding.ADOPT_THRESHOLD] 0.949 에 **0.0054 모자랐다**. AI Hub 가 같은 모델을
     * 87.3% 일치로 쟀고, 이 변형은 **99.6%** 였다 — 격자가 저정밀도에 민감했다는 뜻이다.
     *
     * 변형은 드롭인이다: 입출력 이름·dtype 과 심볼릭 `batch` 가 같고 가중치만 fp16 47개 +
     * **fp32 2개**(격자)다. 그래서 [ImprintReader] 를 그대로 쓴다.
     *
     * CPU 도 같이 잰다 — 변형 자체가 기준보다 점수가 낮을 수도 있어서, **그 기기의 CPU 값**이
     * 있어야 「QNN 때문에 떨어진 것」과 「변형 때문에 떨어진 것」을 가린다.
     */
    @Test fun `각인변형_CPU`() = imprintVariant(Ep.CPU)

    @Test fun `각인변형_QNN`() = imprintVariant(Ep.QNN)

    private fun imprintVariant(ep: Ep) =
        imprint(ep, model = File(imprintModel.parentFile, VARIANT_TPS32), label = "각인변형", expectedScore = null)

    @Test fun `각인_QNN_CACHED`() = imprint(Ep.QNN_CACHED)

    // ── 마크 ─────────────────────────────────────────────────────────

    @Test fun `마크_CPU`() = mark(Ep.CPU)

    @Test fun `마크_WEBGPU`() = mark(Ep.WEBGPU)

    @Test fun `마크_NNAPI`() = mark(Ep.NNAPI)

    @Test fun `마크_XNNPACK`() = mark(Ep.XNNPACK)

    @Test fun `마크_NNAPI_NO_CPU`() = mark(Ep.NNAPI_NO_CPU)

    @Test fun `마크_NNAPI_SPLIT`() = mark(Ep.NNAPI_SPLIT)

    @Test fun `마크_XNNPACK_TUNED`() = mark(Ep.XNNPACK_TUNED)

    @Test fun `마크_QNN`() = mark(Ep.QNN)

    @Test fun `마크_QNN_FP32`() = mark(Ep.QNN_FP32)

    @Test fun `마크_QNN_FP16`() = mark(Ep.QNN_FP16)

    @Test fun `마크_QNN_RNN_CPU`() = mark(Ep.QNN_RNN_CPU)

    @Test fun `마크_QNN_CACHED`() = mark(Ep.QNN_CACHED)

    // ── 거들기 ───────────────────────────────────────────────────────

    /**
     * 기준값은 `ImprintReaderParityTest` 와 같다 — `sample_crop` → `YS`, 0.969883.
     *
     * @param model 돌릴 각인 모델. 기본은 운영과 같은 것이고, 변형을 댈 때만 바꾼다.
     * @param expectedScore 기준 모델의 기록값. 변형은 수치가 달라도 되므로 `null` 로 끈다 —
     *                      그때 보는 것은 **점수가 [ImprintDecoding.ADOPT_THRESHOLD] 를 넘는지**다.
     */
    private fun imprint(ep: Ep, model: File = imprintModel, label: String = "각인", expectedScore: Float? = 0.969883f) {
        assumeTrue("${model.name} 이 없어 건너뛴다", model.isFile && model.length() > 0)
        OrtBackend.forcedEp = ep
        warmCache(ep) { ImprintReader(model).close() }
        val opened = System.nanoTime()
        ImprintReader(model).use { reader ->
            val ready = System.nanoTime()
            // ⚠️ **첫 실행은 버린다.** WebGPU 가 여기서 셰이더를 컴파일한다 — 포함해 재면
            // 몇 배 느리게 나온다(S24 각인: 기록 1796ms 대 예열 없는 측정 5812ms).
            reader.readGray(gray)
            val result = reader.readGray(gray)
            // ⚠️ **단정보다 먼저 찍는다.** 읽은 답까지 같이 — 틀렸을 때 「null」만 남으면
            //    얼마나 틀렸는지 모른다. QNN 이 각인을 잃은 것을 이 줄 없이 쫓느라 한 번 더 돌렸다.
            report(
                label,
                ep,
                openMs = ms(opened, ready),
                runMs = result.timings.totalMs,
                answer = "${result.imprint} · pair ${result.pair} · 점수 ${"%.4f".format(result.score)}"
            )

            // best_pair 를 먼저 본다 — **읽기가 맞았는지**와 **채택이 됐는지**는 다른 문제다.
            // QNN 이 pair YS 를 맞게 고르고도 점수 0.9436 으로 채택에서 떨어진 자리다.
            assertEquals("[$ep] $label best_pair", "YS", result.pair)
            assertEquals("[$ep] $label", "YS", result.imprint)
            expectedScore?.let { assertEquals("[$ep] $label 점수", it, result.score, 1e-4f) }
        }
    }

    /**
     * 기준값은 `MarkReaderParityTest` 와 같다 — 종 일치, 유무 ±5e-3. */
    private fun mark(ep: Ep) {
        OrtBackend.forcedEp = ep
        warmCache(ep) { MarkReader(markModel).close() }
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
            // 케이스를 다 돌고 **보고한 뒤** 단정한다 — 한 케이스가 틀리면 나머지 시간도 같이
            // 날아가던 자리다.
            val wrong = mutableListOf<String>()
            val seen = mutableListOf<String>()
            for (name in cases.keys()) {
                val case = cases.getJSONObject(name)
                val crop = MarkPreprocess.toGrayOnWhite(loadCrop(name, case))
                val actual = reader.readGray(crop).also { crop.release() }
                total += actual.timings.totalMs
                seen += "$name=${actual.species}/${"%.3f".format(actual.presence)}"

                val expected = answers.getJSONObject(name)
                if (expected.getInt("species") != actual.species) {
                    wrong += "$name 종 기준 ${expected.getInt("species")} · 실제 ${actual.species}"
                }
                if (abs(expected.getDouble("presence").toFloat() - actual.presence) >= PRESENCE_TOLERANCE) {
                    wrong += "$name 유무 기준 ${expected.getDouble("presence")} · 실제 ${actual.presence}"
                }
            }
            report("마크", ep, openMs = ms(opened, ready), runMs = total, answer = seen.joinToString(" "))
            assertTrue("[$ep] ${wrong.joinToString(" | ")}", wrong.isEmpty())
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

    /** 세션 생성과 추론을 **갈라서** 적는다. 모델을 여는 값은 한 번만 드는데 섞으면 알약마다 든다고 잘못 읽힌다.
     *
     * [Ep.QNN_CACHED] 일 때만 **세션을 한 번 버리고 연다** — 그래야 캐시 적중을 재는 것이 된다.
     *
     * `ep.context_enable` 은 첫 세션에서 컴파일 결과를 쓴다. 한 번만 열면 재는 값이 **쓰기
     * 경로**라 컴파일 시간이 그대로 들어간다 — 2026-10-13 Adreno 740 에서 각인 세션 생성이
     * **84초**였다. 알고 싶은 것은 그다음, 즉 「파일이 있을 때 얼마나 걸리나」다.
     *
     * 버리는 쪽 시간도 남긴다 — 운영에서 **첫 실행 한 번은 그 값을 치러야** 하므로 그것도 수치다.
     */
    private fun warmCache(ep: Ep, open: () -> Unit) {
        if (ep != Ep.QNN_CACHED) return
        val started = System.nanoTime()
        open()
        // 디렉터리를 그대로 찍는다 — ORT 가 쓴 **파일 이름이 기대와 다르면** 다음 열기가
        // 적중하지 않고, 그 차이를 이 줄 없이는 못 본다.
        val made = OrtBackend.contextCacheDir?.listFiles()?.joinToString { "${it.name}(${it.length() / 1024}KB)" }
        Log.i(TAG, "캐시 생성 | $ep | ${ms(started, System.nanoTime())}ms | 생긴 파일 $made | ${Build.MODEL} ${soc()}")
    }

    private fun report(model: String, ep: Ep, openMs: Long, runMs: Long, answer: String) =
        Log.i(TAG, "$model | $ep | 세션생성 ${openMs}ms | 추론 ${runMs}ms | 답 $answer | ${Build.MODEL} ${soc()}")

    private fun ms(from: Long, to: Long) = (to - from) / 1_000_000

    private fun soc(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
    } else {
        Build.HARDWARE
    }

    private companion object {
        const val TAG = "NM-EPMATRIX"

        /** TPS 격자만 fp32 로 남긴 각인 변형. 저장소에 없다 — 기기로 밀어 넣는다. */
        const val VARIANT_TPS32 = "imprint_fp16_tps32.onnx"

        /** 예열에 쓸 케이스. 어느 것이든 되지만 고정해야 측정이 재현된다. */
        const val WARMUP_CASE = "mark_set-084_p1_back"

        /** `MarkReaderParityTest` 와 같은 값. 실측 최대 1.29e-3 에 4배 여유다. */
        const val PRESENCE_TOLERANCE = 5e-3f
    }
}
