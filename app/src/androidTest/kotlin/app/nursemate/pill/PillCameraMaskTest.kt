package app.nursemate.pill

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.gallery.DesignGalleryActivity
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 촬영 미리보기가 **정사각 밖으로 넘쳐 보이지 않는가**(NM-630).
 *
 * 미리보기를 SurfaceView(PERFORMANCE)로 바꿨다. SurfaceView 는 Compose 가 잘라내지 못하는 기기가 있어
 * 2026-09 에 위아래로 영상이 넘쳤다. 이번엔 바깥을 검은 막으로 덮는다 — 이 테스트가 화면을 찍어
 * 카메라가 붙은 뒤(셔터가 살아난 뒤) 정사각 바로 위 · 아래 줄이 **전부 검은지** 본다.
 *
 * ⚠️ 주변이 어두우면 넘침이 있어도 검게 보여 이 단정이 헛돈다. 그래서 안쪽 밝기를 로그로 남긴다 — 밝은 곳에서 돈
 * 결과(에뮬레이터 가상 장면 · 실기기)만 강한 증거다.
 *
 * 낮은 Android 버전 기기(Test Lab)에서도 이것만 돌리면 된다. 찍은 화면은 `/data/local/tmp/nm-camera-mask.png` 에
 * 남긴다 — 눈으로도 대조한다. 앱 폴더에 두면 테스트가 끝나며 앱과 함께 지워지고, `/sdcard` 는 갤러리에 섞인다.
 */
class PillCameraMaskTest {

    @get:Rule val rule = createAndroidComposeRule<DesignGalleryActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun grantCamera() {
        val pkg = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.executeShellCommand("pm grant $pkg android.permission.CAMERA").close()
    }

    @Test
    fun `카메라가_붙으면_정사각_바깥은_검다`() {
        rule.activity.runOnUiThread {
            rule.activity.setContent { NurseMateTheme { PillCameraScreen({}, {}, onClose = {}) } }
        }
        rule.waitForIdle()

        val node = rule.onNodeWithTag(PREVIEW_TAG).fetchSemanticsNode()
        val top = node.positionOnScreen.y.roundToInt()
        val left = node.positionOnScreen.x.roundToInt()
        val side = node.size.width

        // 카메라가 붙을 때까지 — 붙으면 셔터가 눌리는 상태가 된다. 에뮬레이터는 CameraX 초기화가 몇 초씩 재시도한다.
        // ⚠️ 영상 밝기로 판정하지 않는다. Test Lab 실기기는 어두운 랙 안이라 미리보기가 거의 검다(2026-10-18 S9 · A12 · F-01L).
        val shutter = rule.onNode(hasContentDescription("촬영"))
        val deadline = System.currentTimeMillis() + BIND_TIMEOUT_MILLIS
        while (!shutter.isEnabled() && System.currentTimeMillis() < deadline) settle()
        assertTrue("카메라가 ${BIND_TIMEOUT_MILLIS}ms 안에 붙지 않았다", shutter.isEnabled())
        // 첫 프레임이 들어오고 노출이 잡힐 때까지 조금 더.
        repeat(SETTLE_ROUNDS) { settle() }

        val shot = instrumentation.uiAutomation.takeScreenshot()
        saveScreencap()

        // 안쪽이 어두우면(랙 안) 넘침이 있어도 안 보인다 — 증거가 약하다는 걸 로그로 남긴다.
        Log.i(TAG, "정사각 안 네 모서리 중 밝은 곳 ${litCorners(shot, left, top, side)}곳")

        val gap = (MARGIN_DP * instrumentation.targetContext.resources.displayMetrics.density).roundToInt()
        val above = row(shot, top - gap)
        val below = row(shot, top + side + gap)
        assertTrue("정사각 위 줄에 검지 않은 점 ${above}개", above == 0)
        assertTrue("정사각 아래 줄에 검지 않은 점 ${below}개", below == 0)
    }

    private fun SemanticsNodeInteraction.isEnabled(): Boolean = runCatching { assertIsEnabled() }.isSuccess

    /** 캡처가 끝날 때까지 기다린다 — 안 기다리면 단정이 실패해 화면이 닫힌 뒤 찍힌다. */
    private fun saveScreencap() {
        val out = instrumentation.uiAutomation.executeShellCommand("screencap -p $SHOT_PATH")
        ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes() }
    }

    /**
     * 기다린다. ⚠️ 테스트 규칙의 Compose 시계는 테스트가 넘겨야 흐른다 — 그냥 재우면 카메라 영상(SurfaceView)은
     * 들어와도 Compose 쪽(셔터 · 아이콘 · 배율)은 처음 모습 그대로다. `waitForIdle` 은 맞추는 중 링의
     * 무한 깜빡임 때문에 끝나지 않을 수 있어 시계를 직접 넘긴다.
     */
    private fun settle() {
        Thread.sleep(POLL_MILLIS)
        rule.runOnUiThread { rule.mainClock.advanceTimeBy(POLL_MILLIS) }
    }

    /**
     * 정사각 안 네 모서리(가로세로 1/6 지점) 중 검지 않은 곳 수. 격자선(1/3 · 2/3)과
     * 가운데 수평계를 피한 자리라, 여기가 밝으면 영상이 보이는 것이다.
     */
    private fun litCorners(bitmap: Bitmap, left: Int, top: Int, side: Int): Int =
        listOf(1, 5).flatMap { i -> listOf(1, 5).map { j -> i to j } }.count { (i, j) ->
            !bitmap.getPixel(left + side * i / 6, top + side * j / 6).isBlack()
        }

    /** 한 줄에서 검지 않은 점 수. */
    private fun row(bitmap: Bitmap, y: Int): Int = (0 until bitmap.width).count { !bitmap.getPixel(it, y).isBlack() }

    private fun Int.isBlack(): Boolean = Color.red(this) < DARK && Color.green(this) < DARK && Color.blue(this) < DARK

    private companion object {
        const val BIND_TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 500L
        const val SETTLE_ROUNDS = 4
        const val TAG = "PillCameraMaskTest"
        const val SHOT_PATH = "/data/local/tmp/nm-camera-mask.png"

        /** 정사각 경계에서 이만큼 떨어진 줄을 본다 — 경계 바로 위는 안티에일리어싱이 섞인다. */
        const val MARGIN_DP = 3

        /** 검다고 볼 한계(0~255). */
        const val DARK = 8
    }
}
