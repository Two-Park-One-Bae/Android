package app.nursemate.core.vision

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **검출 모델(RF-DETR-Seg)을 EP 별로 잰다** — NM-577.
 *
 * ## 왜 필요한가
 * 각인은 전 기기에서 CPU 가 최선이고 마크는 Adreno 에서 WebGPU 가 2배 빠른데, **검출만 EP 를
 * 재 본 적이 없다.** 기록에 남은 것은 Xclipse 한 대의 `CPU 1572 → WebGPU 813 ms` 뿐이다.
 * 검출은 식별할 때마다 반드시 도는 모델이라 여기서 EP 가 갈리면 영향이 가장 크다.
 *
 * ## 사진은 저장소에 없다
 * 약포 사진은 PII 라 커밋하지 않는다. 기기에 밀어 넣은 것을 읽고, 없으면 **건너뛴다**
 * (`assumeTrue` — 통과가 아니라 skipped 로 남는다).
 *
 * ```
 * adb push pills-1.jpg /sdcard/Android/data/app.nursemate.core.vision.test/files/
 * ```
 *
 * ⚠️ **하위 디렉터리를 쓰지 않는다.** `adb shell mkdir` 로 만든 디렉터리는 `shell` 소유에
 * `drwxrws---` 라 앱이 목록을 못 읽는다 — 테스트가 조용히 skipped 로 넘어간다(2026-10-13 에
 * 실제로 겪었다). `files/` 바로 아래 파일은 `-rw-rw-rw-` 로 들어와 읽힌다.
 *
 * ## CPU 를 기준으로 **같은 프로세스에서** 맞댄다
 * 기기가 다르면 CPU 끼리도 미세하게 갈려 EP 차이와 섞인다. 한 테스트 안에서 CPU 를 먼저
 * 돌려 기준을 만들고, 그다음 재려는 EP 로 같은 사진을 돌린다.
 *
 * ⚠️ **개수만 보지 않는다.** 임계가 0.5 라 점수 0.51 이 0.49 로 밀리면 개수가 바뀌지만,
 * 0.51 이 0.50 으로 밀린 것은 개수로 안 보이면서 **다음 사진에서 뒤집힐** 상태다. 그래서
 * 점수와 박스를 원소별로 맞댄다.
 *
 * 결과는 logcat `NM-DETEP`.
 */
@RunWith(AndroidJUnit4::class)
class DetectorEpTest {

    private lateinit var model: File
    private lateinit var photos: List<File>

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val dir = context.getExternalFilesDir(null)
        model = File(dir, PillDetector.MODEL_FILE_NAME)
        assumeTrue("검출 모델이 없어 건너뛴다", model.isFile && model.length() > 0)

        photos = dir?.listFiles()
            ?.filter { it.name.startsWith(PHOTO_PREFIX) && it.extension.lowercase() in IMAGES }
            ?.sortedBy { it.name }.orEmpty()
        assumeTrue("검출용 사진이 없어 건너뛴다 — files/ 에 $PHOTO_PREFIX*.jpg 를 밀어 넣어야 한다", photos.isNotEmpty())

        // [Ep.QNN_CACHED] 가 컴파일 결과를 쓸 곳. 모델이 놓인 자리는 adb 가 밀어 넣은 데라
        // 앱에 쓰기 권한이 없을 수 있어 앱 캐시로 보낸다.
        OrtBackend.contextCacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        // HTP 가 DSP skel 을 찾는 자리. 여기를 안 주면 EP 가 노드를 하나도 안 가져가고
        // **CPU 로 돌면서 성공한다** — [OrtBackend.qnnNativeLibDir] KDoc 참조.
        OrtBackend.qnnNativeLibDir =
            File(InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.nativeLibraryDir)
    }

    @After
    fun tearDown() {
        OrtBackend.forcedEp = null
        OrtBackend.contextCacheDir = null
        OrtBackend.qnnNativeLibDir = null
    }

    @Test fun `검출_CPU`() = measure(Ep.CPU)

    @Test fun `검출_WEBGPU`() = measure(Ep.WEBGPU)

    @Test fun `검출_NNAPI`() = measure(Ep.NNAPI)

    @Test fun `검출_XNNPACK`() = measure(Ep.XNNPACK)

    @Test fun `검출_QNN`() = measure(Ep.QNN)

    @Test fun `검출_QNN_FP32`() = measure(Ep.QNN_FP32)

    @Test fun `검출_QNN_CACHED`() = measure(Ep.QNN_CACHED)

    private fun measure(ep: Ep) = runBlocking {
        val bitmaps = photos.map { file ->
            val context = InstrumentationRegistry.getInstrumentation().context
            file.name to ImageLoader.load(context, Uri.fromFile(file))
        }

        val reference = if (ep == Ep.CPU) null else detectAll(Ep.CPU, bitmaps)
        val actual = detectAll(ep, bitmaps)

        actual.forEach { (name, pills) ->
            val scores = pills.joinToString { "%.3f".format(it.score) }
            Log.i(TAG, "검출 | $ep | $name | ${pills.size}개 | $scores | ${Build.MODEL} ${soc()}")
        }

        if (reference == null) return@runBlocking

        for ((name, expected) in reference) {
            val got = actual.getValue(name)
            assertEquals("[$ep] $name 검출 개수", expected.size, got.size)

            var worstScore = 0f
            var worstBox = 0f
            for (i in expected.indices) {
                worstScore = maxOf(worstScore, abs(expected[i].score - got[i].score))
                worstBox = maxOf(
                    worstBox,
                    maxOf(
                        abs(expected[i].x - got[i].x),
                        abs(expected[i].y - got[i].y),
                        abs(expected[i].width - got[i].width),
                        abs(expected[i].height - got[i].height)
                    )
                )
            }
            Log.i(TAG, "검출 | $ep | $name | CPU 대비 점수차 $worstScore · 박스차 $worstBox")

            assertTrue("[$ep] $name 점수가 CPU 와 $worstScore 벌어졌다", worstScore < SCORE_TOLERANCE)
            assertTrue("[$ep] $name 박스가 CPU 와 $worstBox 벌어졌다", worstBox < BOX_TOLERANCE)
        }
    }

    private suspend fun detectAll(ep: Ep, bitmaps: List<Pair<String, android.graphics.Bitmap>>) = buildMap {
        OrtBackend.forcedEp = ep
        PillDetector(model).use { detector ->
            // ⚠️ **첫 실행은 버린다.** WebGPU 는 여기서 셰이더를 컴파일하는데, 그걸 포함해 재면
            // 실제보다 몇 배 느리게 나온다 — S24 에서 각인이 기록 1796ms 대 측정 5812ms 로
            // 벌어진 것이 이 때문이었다(2026-10-13). CPU 는 예열 비용이 거의 없어, 예열 없이
            // 재면 WebGPU 만 일방적으로 손해를 본다.
            bitmaps.firstOrNull()?.let { (_, warmup) -> detector.detect(warmup) }

            for ((name, bitmap) in bitmaps) {
                val result = detector.detect(bitmap)
                Log.i(TAG, "검출 | $ep | $name | 추론 ${result.timings.inferenceMs}ms")
                put(name, result.pills.map { it.detection }.sortedBy { it.query })
            }
        }
    }

    private fun soc(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
    } else {
        Build.HARDWARE
    }

    private companion object {
        const val TAG = "NM-DETEP"
        const val PHOTO_PREFIX = "pills-"
        val IMAGES = setOf("jpg", "jpeg", "png")

        /** 임계가 0.5 다. 0.02 면 그 경계에서 뒤집히기 한참 전에 걸린다. */
        const val SCORE_TOLERANCE = 2e-2f

        /** 0~1 정규화 좌표. 5e-3 이면 2048px 기준 10px 로, 크롭 테두리가 눈에 띄게 어긋나기 전이다. */
        const val BOX_TOLERANCE = 5e-3f
    }
}
