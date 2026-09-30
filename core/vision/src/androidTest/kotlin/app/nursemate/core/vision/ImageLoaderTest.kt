package app.nursemate.core.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 사진 처리 규격을 고정한다 — `spec/feature/pill-recognition/README.md` §사진 처리 규격.
 *
 * | 단계 | 규칙 |
 * |---|---|
 * | ① EXIF 회전 | 회전을 픽셀에 적용해 정방향으로 정규화 |
 * | ② 정사각 크롭 | 짧은 변 기준 **중앙 1:1** |
 * | ③ 축소 | 최장변 **2048px** 이하 |
 *
 * ## 왜 이제서야 테스트를 붙이나
 * 이 경로는 검출만 쓰다가 **학습데이터 업로드본까지 여기로 모였다**(NM-454). 예전 업로드는
 * 원본 URI 의 바이트를 그대로 올렸는데, 스펙이 「학습데이터 업로드본도 이 가공본이다」로
 * 정하면서 두 쓰임이 같은 출력을 공유하게 됐다.
 *
 * ⚠️ **틀려도 조용하다.** 검출은 어차피 576 으로 stretch 되니 조금 다른 크롭이 와도 그림이
 * 나오고, 업로드는 결과를 보지 않는 베스트 에포트다. 어긋난 채로 학습 데이터가 쌓인 뒤에야
 * 드러난다.
 */
class ImageLoaderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun 가로_사진은_중앙_정사각으로_잘린다() {
        val loaded = ImageLoader.load(context, jpeg(width = 1200, height = 900))

        assertEquals("짧은 변 기준 정사각이어야 한다", loaded.height, loaded.width)
        assertEquals(900, loaded.width)
    }

    @Test
    fun 세로_사진도_짧은_변_기준이다() {
        val loaded = ImageLoader.load(context, jpeg(width = 900, height = 1200))

        assertEquals(loaded.height, loaded.width)
        assertEquals(900, loaded.width)
    }

    /**
     * 2048 은 성능 여유가 아니라 **정확도 요건**이다 — 학습 파이프라인이 `2048 → 576` 을
     * 거치므로 상한이 달라지면 최종 텐서가 달라진다([ImageLoader.load] KDoc).
     */
    @Test
    fun 최장변은_2048_이하로_줄어든다() {
        val loaded = ImageLoader.load(context, jpeg(width = 4000, height = 3000))

        assertTrue(
            "정사각으로 자른 뒤 2048 이하여야 한다 — 실제 ${loaded.width}",
            loaded.width <= RfDetrSpec.MAX_DIMENSION
        )
        assertEquals(loaded.height, loaded.width)
    }

    /** 이미 작은 사진을 굳이 키우지 않는다. 없는 화소를 만들어 봐야 각인이 선명해지지 않는다. */
    @Test
    fun 작은_사진은_키우지_않는다() {
        val loaded = ImageLoader.load(context, jpeg(width = 400, height = 400))

        assertEquals(400, loaded.width)
    }

    /**
     * EXIF 회전이 **픽셀에** 적용되는지 본다.
     *
     * 왼쪽 위에만 표식을 찍어 두고 180° 회전을 걸면, 회전이 구워졌을 때 표식이 오른쪽 아래로
     * 간다. 회전을 메타데이터로만 두면 표식이 그대로 왼쪽 위에 남는다 — iOS 가 이걸 어겨
     * 「BBOX 는 정상인데 크롭만 엉뚱한 영역」이 나왔다(커밋 `c4f4b90`, [ImageLoader] KDoc).
     */
    @Test
    fun EXIF_회전이_픽셀에_구워진다() {
        val uri = jpeg(
            width = 600,
            height = 600,
            markTopLeft = true,
            orientation = ExifInterface.ORIENTATION_ROTATE_180
        )

        val loaded = ImageLoader.load(context, uri)

        assertTrue("표식이 왼쪽 위에 남아 있으면 회전이 안 구워진 것이다", loaded.getPixel(20, 20).isWhitish())
        assertTrue("회전했다면 표식이 오른쪽 아래에 있어야 한다", loaded.getPixel(loaded.width - 20, loaded.height - 20).isReddish())
    }

    /**
     * 색을 **정확히** 비교하지 않는다 — JPEG 는 손실 압축이라 빨강이 `0xFFFF0000` 에서
     * `0xFFFE0000` 으로 밀린다. 보려는 것은 「표식이 어느 모서리에 있나」지 색값이 아니다.
     */
    private fun Int.isReddish(): Boolean =
        Color.red(this) > 200 && Color.green(this) < 80 && Color.blue(this) < 80

    private fun Int.isWhitish(): Boolean =
        Color.red(this) > 200 && Color.green(this) > 200 && Color.blue(this) > 200

    /**
     * 테스트용 JPEG 를 만들어 `file://` URI 로 돌려준다.
     *
     * 배경을 흰색으로 채우고 [markTopLeft] 면 왼쪽 위에 빨간 사각형을 찍는다. JPEG 는 손실
     * 압축이라 경계가 번지므로 표식을 **넉넉히** 크게 둔다 — 색을 정확히 비교하는 지점은
     * 표식 한가운데다.
     */
    private fun jpeg(
        width: Int,
        height: Int,
        markTopLeft: Boolean = false,
        orientation: Int = ExifInterface.ORIENTATION_NORMAL
    ): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).also { canvas ->
                canvas.drawColor(Color.WHITE)
                if (markTopLeft) {
                    canvas.drawRect(0f, 0f, width / 4f, height / 4f, Paint().apply { color = Color.RED })
                }
            }
        }
        val file = File.createTempFile("imageloader", ".jpg", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()

        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }
}
