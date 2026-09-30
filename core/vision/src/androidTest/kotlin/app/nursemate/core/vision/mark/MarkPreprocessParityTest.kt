package app.nursemate.core.vision.mark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * 마크 전처리가 **기준 구현과 화소 하나까지 같은지** 단계마다 대조한다 (NM-515 DoD).
 *
 * ## 여기가 이 티켓에서 제일 위험한 자리다
 * 임베딩은 서버 카탈로그와 코사인으로 대조하는 값이라, 전처리가 한 획이라도 다르면 **예외가
 * 아니라 후보 순서로만** 나타난다. iOS 가 먼저 겪었다 — *「PoC 전처리는 이 중 여러 곳이
 * 정본과 어긋나 있었다」*(iOS#178).
 *
 * ## 왜 단계마다인가
 * 마지막 텐서만 보면 갈렸다는 것은 알아도 **어디서** 갈렸는지 모른다. 합성 → 흑백 → 정사각 →
 * 회전 → CLAHE → 정규화 순으로 끊으면 틀어진 지점이 바로 나온다.
 *
 * ## 기준값은 어떻게 만들었나
 * ML 레포 `mark_species_infer.py` 에서 필요한 함수를 **원문 그대로 꺼내** 실행하고 중간
 * 산출물의 sha1 을 떴다. 손으로 베끼면 베낀 것 자체가 오차원이 된다.
 *
 * **OpenCV 4.11 과 4.12 가 이 경로에서 바이트 단위로 같은 것을 먼저 확인했다** — 예제 6장 ×
 * 8방향 × 224² = 240만 화소에서 최대 차 0. 그래서 픽스처는 4.12 로 떴다.
 *
 * ## 입력이 이미지가 아니라 숫자다
 * `*.rgba` 는 크롭의 **원시 RGBA 바이트**다. PNG 로 두면 파이썬과 Android 의 디코더 차이가
 * 섞여 전처리가 아닌 것을 재게 된다.
 *
 * ⚠️ **테스트 이름에 공백을 넣지 않는다.** 계측 테스트는 dex 로 가고 `minSdk` 26 이면 DEX 035
 * 를 겨냥하는데 공백은 040 부터 허용된다 — D8 이 빌드를 깬다.
 */
@RunWith(AndroidJUnit4::class)
class MarkPreprocessParityTest {

    private lateinit var fixtures: JSONObject

    @Before
    fun setUp() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("mark/fixtures.json").use { it.readBytes().decodeToString() }
        fixtures = JSONObject(json)
    }

    @Test
    fun `픽스처는_같은_OpenCV_판으로_떴다`() {
        // 판이 갈리면 CLAHE·INTER_AREA 결과가 달라질 수 있다. 4.11↔4.12 는 같은 것을 확인했다.
        val dumped = fixtures.getString("opencv")
        val running = OpenCVLoader.OPENCV_VERSION
        assertEquals("픽스처 $dumped · 실행 $running — 앞 두 자리가 같아야 한다", dumped.take(3), running.take(3))
    }

    @Test
    fun `알파를_컬러에서_합성하고_흑백으로_바꾼다`() {
        forEachCase { name, case ->
            val gray = MarkPreprocess.toGrayOnWhite(loadCrop(name, case))
            assertEquals("$name 흑백", case.getString("gray"), gray.sha1())
            gray.release()
        }
    }

    @Test
    fun `정사각_224_로_줄인다`() {
        forEachCase { name, case ->
            val gray = MarkPreprocess.toGrayOnWhite(loadCrop(name, case))
            val square = MarkPreprocess.toSquare(gray)
            assertEquals("$name 정사각", case.getString("square"), square.sha1())
            assertEquals("$name 크기", MarkPreprocess.SIDE, square.rows())
            gray.release()
            square.release()
        }
    }

    @Test
    fun `여덟_방향_회전이_기준과_같다`() {
        forEachCase { name, case ->
            val square = squareOf(name, case)
            val expected = case.getJSONArray("rotated")
            for (i in 0 until MarkPreprocess.ROTATIONS) {
                val rotated = MarkPreprocess.rotate(square, i * 360.0 / MarkPreprocess.ROTATIONS)
                assertEquals("$name 회전 $i", expected.getString(i), rotated.sha1())
                rotated.release()
            }
            square.release()
        }
    }

    @Test
    fun `회전_뒤_CLAHE_가_기준과_같다`() {
        forEachCase { name, case ->
            val square = squareOf(name, case)
            val expected = case.getJSONArray("clahe")
            for (i in 0 until MarkPreprocess.ROTATIONS) {
                val rotated = MarkPreprocess.rotate(square, i * 360.0 / MarkPreprocess.ROTATIONS)
                val clahe = MarkPreprocess.applyClahe(rotated)
                assertEquals("$name CLAHE $i", expected.getString(i), clahe.sha1())
                rotated.release()
                clahe.release()
            }
            square.release()
        }
    }

    /** 끝단 — `[8,3,224,224]` float32 배치 전체가 기준과 같은지. */
    @Test
    fun `정규화까지_끝난_배치가_기준과_같다`() {
        forEachCase { name, case ->
            val square = squareOf(name, case)
            val batch = FloatArray(MarkPreprocess.BATCH)
            for (i in 0 until MarkPreprocess.ROTATIONS) {
                val rotated = MarkPreprocess.rotate(square, i * 360.0 / MarkPreprocess.ROTATIONS)
                val clahe = MarkPreprocess.applyClahe(rotated)
                MarkPreprocess.writeNormalized(clahe, batch, i * MarkPreprocess.PLANE)
                rotated.release()
                clahe.release()
            }
            assertEquals("$name 배치", case.getString("batch"), batch.sha1())
            square.release()
        }
    }

    // ── 거들기 ───────────────────────────────────────────────────────

    private fun forEachCase(block: (String, JSONObject) -> Unit) {
        val cases = fixtures.getJSONObject("cases")
        for (name in cases.keys()) block(name, cases.getJSONObject(name))
    }

    private fun squareOf(name: String, case: JSONObject): Mat {
        val gray = MarkPreprocess.toGrayOnWhite(loadCrop(name, case))
        return MarkPreprocess.toSquare(gray).also { gray.release() }
    }

    /** `*.rgba` 원시 바이트 → `ARGB_8888` 비트맵. 세그가 주는 크롭과 같은 형태다. */
    private fun loadCrop(name: String, case: JSONObject): android.graphics.Bitmap {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("mark/$name.rgba").use { it.readBytes() }
        val w = case.getInt("width")
        val h = case.getInt("height")
        // ⚠️ `createBitmap(IntArray, …)` 는 **곱하지 않은 값으로 받아 저장할 때 곱한다.** 그러면
        //    이 테스트가 재는 것이 전처리가 아니라 「곱했다 푸는 왕복 오차」가 된다. 빈 비트맵을
        //    비프리멀티플라이로 만들고 원시 바이트를 그대로 부어 그 왕복을 없앤다.
        //    (실제 세그 크롭은 곱해져 있고, 푸는 일은 `MarkPreprocess.toGrayOnWhite` 가 한다.)
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
        return bitmap
    }

    private fun Mat.sha1(): String {
        val bytes = ByteArray(rows() * cols() * CvType.channels(type()))
        get(0, 0, bytes)
        return bytes.sha1()
    }

    private fun FloatArray.sha1(): String {
        val buffer = java.nio.ByteBuffer.allocate(size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        forEach { buffer.putFloat(it) }
        return buffer.array().sha1()
    }

    private fun ByteArray.sha1(): String = MessageDigest.getInstance("SHA-1").digest(this)
        .joinToString("") { String.format(Locale.ROOT, "%02x", it) }
}
