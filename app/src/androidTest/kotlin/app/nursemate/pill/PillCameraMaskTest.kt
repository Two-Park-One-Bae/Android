package app.nursemate.pill

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
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
 * 정사각 바로 위 · 아래 줄이 **전부 검은지**, 정사각 가운데엔 **영상이 있는지**(카메라가 붙었는지) 본다.
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
    fun `정사각_바깥은_검고_안에는_영상이_있다`() {
        rule.activity.runOnUiThread {
            rule.activity.setContent { NurseMateTheme { PillCameraScreen({}, {}, onClose = {}) } }
        }
        rule.waitForIdle()

        val node = rule.onNodeWithTag(PREVIEW_TAG).fetchSemanticsNode()
        val top = node.positionOnScreen.y.roundToInt()
        val left = node.positionOnScreen.x.roundToInt()
        val side = node.size.width

        // 카메라가 붙고 첫 프레임이 들어올 때까지 기다린다. 에뮬레이터는 CameraX 초기화가 몇 초씩 재시도한다.
        val deadline = System.currentTimeMillis() + BIND_TIMEOUT_MILLIS
        var shot = instrumentation.uiAutomation.takeScreenshot()
        while (litCorners(shot, left, top, side) < CORNERS_NEEDED && System.currentTimeMillis() < deadline) {
            shot = settleAndShoot()
        }
        // 영상이 막 들어온 프레임은 노출이 덜 잡혔을 수 있고, 아이콘 · 셔터도 아직 옛 상태다 — 한 번 더 찍는다.
        shot = settleAndShoot()
        instrumentation.uiAutomation.executeShellCommand("screencap -p $SHOT_PATH").close()

        val lit = litCorners(shot, left, top, side)
        assertTrue("정사각 안 네 모서리 중 ${lit}곳만 영상이 있다 — 카메라가 안 붙었다", lit >= CORNERS_NEEDED)

        val gap = (MARGIN_DP * instrumentation.targetContext.resources.displayMetrics.density).roundToInt()
        val above = row(shot, top - gap)
        val below = row(shot, top + side + gap)
        assertTrue("정사각 위 줄에 검지 않은 점 ${above}개", above == 0)
        assertTrue("정사각 아래 줄에 검지 않은 점 ${below}개", below == 0)
    }

    /**
     * 기다렸다 찍는다. ⚠️ 테스트 규칙의 Compose 시계는 테스트가 넘겨야 흐른다 — 그냥 재우면 카메라 영상(SurfaceView)은
     * 들어와도 Compose 쪽(셔터 · 아이콘 · 배율)은 처음 모습 그대로 찍힌다. `waitForIdle` 은 맞추는 중 링의
     * 무한 깜빡임 때문에 끝나지 않을 수 있어 시계를 직접 넘긴다.
     */
    private fun settleAndShoot(): Bitmap {
        Thread.sleep(POLL_MILLIS)
        rule.runOnUiThread { rule.mainClock.advanceTimeBy(POLL_MILLIS) }
        return instrumentation.uiAutomation.takeScreenshot()
    }

    /**
     * 정사각 안 네 모서리(가로세로 1/6 지점) 중 검지 않은 곳 수. 격자선(1/3 · 2/3)과
     * 가운데 수평계를 피한 자리라, 여기가 밝으면 영상이다.
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
        const val CORNERS_NEEDED = 3
        const val SHOT_PATH = "/data/local/tmp/nm-camera-mask.png"

        /** 정사각 경계에서 이만큼 떨어진 줄을 본다 — 경계 바로 위는 안티에일리어싱이 섞인다. */
        const val MARGIN_DP = 3

        /** 검다고 볼 한계(0~255). */
        const val DARK = 8
    }
}
