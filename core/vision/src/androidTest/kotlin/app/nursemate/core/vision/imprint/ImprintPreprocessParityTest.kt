package app.nursemate.core.vision.imprint

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
 * 각인 전처리가 **기준 구현과 같은 값을 내는지** 단계마다 대조한다(NM-485 DoD).
 *
 * ## 왜 단계마다인가
 * 마지막에 답만 보면 갈렸다는 것은 알아도 **어디서** 갈렸는지 모른다. `toSquare` → `zoomFrame`
 * → 회전+CLAHE 순으로 끊어 보면 틀어진 지점이 바로 나온다.
 *
 * ## 기준값은 어떻게 만들었나
 * ML 레포의 `code/train.py`·`code/preprocess.py` 에서 필요한 함수를 **원문 그대로 꺼내** 실행하고
 * 중간 산출물을 덤프했다(`scripts` 아님 — 일회용이라 저장소에 두지 않는다). 손으로 베끼면
 * 베낀 것 자체가 오차원이 된다.
 *
 * ⚠️ **OpenCV 버전을 맞춰야 한다.** 덤프는 `opencv-python-headless==4.11.0.86` 으로 떴고
 * 앱은 `org.opencv:opencv:4.11.0` 이다. 판이 갈리면 CLAHE·INTER_AREA 결과가 달라질 수 있어
 * 픽스처에 버전을 함께 적어 두고 여기서 확인한다.
 *
 * ⚠️ **테스트 이름에 공백을 넣지 않는다.** 계측 테스트는 dex 로 가고, DEX 버전을 정하는 것은
 * 돌리는 기기가 아니라 `minSdk` 다. minSdk 26 이면 DEX 035 를 겨냥하는데 공백은 040 부터
 * 허용된다 — D8 이 `Space characters in SimpleName '…' are not allowed prior to DEX version 040`
 * 로 되받아 **빌드가 깨진다**. 단위 테스트는 JVM 이라 공백이 통하는 것과 다르다.
 *
 * ## 입력이 이미지가 아니라 숫자다
 * `input_gray.bin` 은 `_to_square` 까지 마친 128×128 회색조 원시 바이트다. PNG 로 두면 파이썬과
 * Android 의 디코더 차이가 섞여 **전처리가 아닌 것을 재게 된다.**
 */
@RunWith(AndroidJUnit4::class)
class ImprintPreprocessParityTest {

    private lateinit var fixtures: JSONObject
    private lateinit var input: Mat

    @Before
    fun setUp() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        fixtures = JSONObject(asset("fixtures.json").decodeToString())
        input = grayMat(asset("input_gray.bin"), SIDE, SIDE)
    }

    @Test
    fun `픽스처를_뜬_OpenCV_판이_앱과_같다`() {
        // 판이 갈리면 아래 대조가 통과해도 의미가 없다. 먼저 못 박는다.
        assertEquals("4.11.0", fixtures.getString("opencv"))
    }

    @Test
    fun `toSquare_결과가_기준과_같다`() {
        // 입력이 이미 128 정사각이라 항등이어야 한다 — 기준도 같은 해시를 냈다.
        val squared = ImprintPreprocess.toSquare(input)
        assertEquals(
            fixtures.getJSONObject("input").getString("sha256_16"),
            squared.digest()
        )
        squared.release()
    }

    @Test
    fun `zoomFrame_이_배율_여섯에서_모두_기준과_같다`() {
        val expected = fixtures.getJSONObject("zooms")
        for (zoom in ImprintPreprocess.ZOOMS) {
            // ⚠️ 로캘을 박는다. 기본 로캘이면 일부 지역에서 "1,0" 이 나와 픽스처 키 조회가 깨진다.
            val key = String.format(Locale.ROOT, "%.1f", zoom)
            val framed = ImprintPreprocess.zoomFrame(input, zoom)
            assertEquals("배율 $key", expected.getString(key), framed.digest())
            framed.release()
        }
    }

    @Test
    fun `회전과_CLAHE_를_거친_144장이_모두_기준과_같다`() {
        // 여기가 가장 중요하다. CLAHE 가 미세하게 달라도 확률이 흔들려 0.949 채택 임계가
        // 뒤집히고, 각인이 달라지고, 후보가 달라진다 — 크래시가 아니라 순위로만 나타난다.
        val samples = fixtures.getJSONArray("samples")
        var index = 0
        for (zoom in ImprintPreprocess.ZOOMS) {
            val framed = ImprintPreprocess.zoomFrame(input, zoom)
            for (i in 0 until ImprintPreprocess.ROTATIONS) {
                val degrees = i * 360.0 / ImprintPreprocess.ROTATIONS
                val rotated = ImprintPreprocess.rotate(framed, degrees)
                val prepped = ImprintPreprocess.applyClahe(rotated)
                rotated.release()

                val want = samples.getJSONObject(index)
                assertEquals(
                    "배율 ${want.getDouble("zoom")} 회전 ${want.getDouble("rot")}",
                    want.getString("clahe_sha256_16"),
                    prepped.digest()
                )
                prepped.release()
                index++
            }
            framed.release()
        }
        assertEquals("표본 수", samples.length(), index)
    }

    private fun asset(name: String): ByteArray = InstrumentationRegistry.getInstrumentation().context.assets
        .open("imprint/$name").use { it.readBytes() }

    private fun grayMat(bytes: ByteArray, rows: Int, cols: Int): Mat =
        Mat(rows, cols, CvType.CV_8UC1).also { it.put(0, 0, bytes) }

    /** 기준 덤프와 같은 방식 — 연속 바이트의 sha256 앞 16자. */
    private fun Mat.digest(): String {
        val bytes = ByteArray(rows() * cols())
        get(0, 0, bytes)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }

    private companion object {
        const val SIDE = ImprintPreprocess.SIDE
    }
}
