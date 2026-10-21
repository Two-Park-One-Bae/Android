package app.nursemate.core.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
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
import kotlinx.coroutines.runBlocking
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
 * GPU 번역 디스크 캐시(`VisionBackend.gpuCache`, NM-636)로 다시 연 모델이 **처음 연 모델과 같은 답**을 내는지 본다.
 *
 * 캐시는 기기 드라이버가 만든 프로그램을 그대로 저장했다가 읽는다. 드라이버마다 저장 형식이 달라, 읽기가 조용히 틀어지면
 * 예외 없이 다른 답이 나올 수 있다 — 그래서 대표 기기마다 맞대 본다(Firebase Test Lab).
 *
 * 모델마다 빈 캐시로 한 번(번역 + 저장), 같은 캐시로 한 번(읽기) 열어 답과 열기 시간을 비교한다. 시간은 logcat
 * 「GpuCache」에 남긴다 — 다시 열기가 빨라졌는지는 기기별 표로 본다(실패로 두지 않는다, 기기마다 폭이 다르다).
 *
 * 모델 · 사진은 다른 대조 테스트와 같은 자리에 밀어 넣는다(`DetectorParityTest` · `MarkReaderParityTest` ·
 * `ImprintReaderParityTest`). 없으면 건너뛴다.
 */
@RunWith(AndroidJUnit4::class)
class GpuCacheTest {

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val dir: File get() = requireNotNull(context.getExternalFilesDir(null))
    private lateinit var cache: File

    @Before
    fun setUp() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        assumeTrue("CPU 강제(nm_gpu=false) — GPU 캐시를 볼 것이 없다", GpuOverride.gpu)
        VisionBackend.allowGpu = { true }
        cache = File(context.cacheDir, "gpu_cache_test").apply {
            deleteRecursively()
            mkdirs()
        }
        VisionBackend.gpuCache = GpuCache(cache, "test")
    }

    @After
    fun restore() {
        VisionBackend.gpuCache = null
        VisionBackend.allowGpu = { true }
        cache.deleteRecursively()
    }

    @Test
    fun `검출기를_캐시로_다시_열어도_답이_같다`() {
        val model = File(dir, PillDetector.MODEL_FILE_NAME)
        val photo = File(dir, PHOTO)
        assumeTrue("모델 · 사진이 없어 건너뛴다", model.isFile && photo.isFile)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(photo.path)) { "사진을 못 읽었다" }

        val (cold, warm) = twice("seg") {
            PillDetector(model).use { d ->
                assumeTrue("GPU 로 열리지 않았다(대체됨)", d.onGpu)
                runBlocking { d.detect(bitmap) }.also { assumeTrue("실행 중 CPU 로 대체됐다", d.onGpu) }
            }
        }
        assertTrue("검출이 없다", cold.pills.isNotEmpty())
        assertEquals("검출 개수", cold.pills.size, warm.pills.size)
        for (c in cold.pills) {
            val iou = warm.pills.maxOf { it.detection.iou(c.detection) }
            assertTrue("q${c.detection.query} IoU $iou", iou >= MIN_IOU)
        }
    }

    @Test
    fun `마크를_캐시로_다시_열어도_답이_같다`() {
        val model = File(dir, MarkReader.MODEL_FILE_NAME)
        assumeTrue("모델이 없어 건너뛴다", model.isFile && model.length() > 0)
        val cases = JSONObject(context.assets.open("mark/fixtures.json").use { it.readBytes().decodeToString() })
            .getJSONObject("cases")
        val name = cases.keys().next()
        val case = cases.getJSONObject(name)

        val (cold, warm) = twice("mark") {
            MarkReader(model).use { r ->
                assumeTrue("GPU 로 열리지 않았다(대체됨)", r.onGpu)
                val gray = MarkPreprocess.toGrayOnWhite(markCrop(name, case))
                r.readGray(gray).also { gray.release() }
            }
        }
        assertEquals("$name 종", cold.species, warm.species)
        assertTrue("$name 유무 ${cold.presence} / ${warm.presence}", abs(cold.presence - warm.presence) < MARK_TOLERANCE)
    }

    @Test
    fun `각인을_캐시로_다시_열어도_답이_같다`() {
        val parts = ImprintReader.PART_FILE_NAMES.map { File(dir, it) }
        assumeTrue("조각이 없어 건너뛴다", parts.all { it.isFile && it.length() > 0 })
        val bytes = context.assets.open("imprint/input_gray.bin").use { it.readBytes() }

        val (cold, warm) = twice("imprint") {
            ImprintReader(parts).use { r ->
                assumeTrue("GPU 로 연 조각이 없다(대체됨)", r.gpuParts.isNotEmpty())
                val gray = Mat(ImprintPreprocess.SIDE, ImprintPreprocess.SIDE, CvType.CV_8UC1).also {
                    it.put(0, 0, bytes)
                }
                r.readGray(gray).also { gray.release() }
            }
        }
        assertEquals("확신 글자", cold.imprint, warm.imprint)
        assertEquals("best_pair", cold.pair, warm.pair)
        assertEquals("best_pair 점수", cold.score, warm.score, IMPRINT_TOLERANCE)
    }

    /** 빈 캐시로 한 번, 그 캐시로 한 번 연다. 처음 열기가 캐시에 무엇이든 썼는지도 본다 — 안 썼으면 둘째도 번역이라 맞댈 것이 없다. */
    private fun <T> twice(label: String, open: () -> T): Pair<T, T> {
        val coldStart = SystemClock.elapsedRealtime()
        val cold = open()
        val coldMs = SystemClock.elapsedRealtime() - coldStart
        val written = cache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        // 저장을 못 하는 드라이버는 캐시 없이 돌 뿐이다(느릴 뿐 답은 같다) — 실패가 아니라 건너뜀으로 남긴다.
        assumeTrue("$label 캐시에 쓴 것이 없다 — 이 기기 드라이버가 저장을 지원하지 않는다", written > 0)

        val warmStart = SystemClock.elapsedRealtime()
        val warm = open()
        val warmMs = SystemClock.elapsedRealtime() - warmStart
        Log.i(TAG, "$label 처음 ${coldMs}ms · 캐시로 ${warmMs}ms · 캐시 ${written / MB}MB")
        return cold to warm
    }

    /** `*.rgba` 원시 바이트 → 곱하지 않은 `ARGB_8888` 비트맵(`MarkReaderParityTest` 와 같다). */
    private fun markCrop(name: String, case: JSONObject): Bitmap {
        val bytes = context.assets.open("mark/$name.rgba").use { it.readBytes() }
        return Bitmap.createBitmap(case.getInt("width"), case.getInt("height"), Bitmap.Config.ARGB_8888).apply {
            setPremultiplied(false)
            copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        }
    }

    private companion object {
        const val TAG = "GpuCache"
        const val PHOTO = "sample_pouch.jpg"
        const val MB = 1024 * 1024

        /** 같은 프로그램을 다시 읽었으니 같아야 한다. GPU 는 실행마다 조금 흔들리는 기기가 있어 아주 좁게만 둔다. */
        const val MIN_IOU = 0.99f
        const val MARK_TOLERANCE = 1e-3f
        const val IMPRINT_TOLERANCE = 1e-3f
    }
}
