package app.nursemate.pill

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.model.Usage
import app.nursemate.gallery.DesignGalleryActivity
import app.nursemate.home.NmTab
import app.nursemate.navigation.NmTabScaffold
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * 촬영 ~ 로딩 · 실패 V2.1.0 — 정본 「알약 식별 · 촬영 ~ 로딩 · V2.1.0 (NM-586)」 ② ~ ⑦ 을 탭 바와 함께 그려 PNG 로 남긴다.
 * ① 촬영은 카메라가 있어야 해 실기기에서 따로 찍는다. **단정이 없다 — 눈으로 대조하는 용도다.**
 * 사진은 그려 낸 가짜다. 결과는 `/sdcard/Android/data/app.nursemate.debug/files/v210capture/`.
 */
class V210CaptureFlowCaptureTest {

    @get:Rule val rule = createAndroidComposeRule<DesignGalleryActivity>()

    private val photo = Bitmap.createBitmap(PHOTO, PHOTO, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.eraseColor(0xFFD9DDE2.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val canvas = Canvas(bitmap)
        paint.color = 0xFFF4E1A6.toInt()
        canvas.drawOval(110f, 250f, 270f, 330f, paint)
        paint.color = 0xFFB5523B.toInt()
        canvas.drawCircle(400f, 300f, 50f, paint)
    }

    private val state = PillUiState(photo = photo)
    private val usage = Usage(15, 12, "")

    private fun show(content: @Composable () -> Unit) {
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                NurseMateTheme {
                    NmTabScaffold(
                        selected = NmTab.Pill,
                        onSelect = {},
                        modifier = Modifier.testTag(TAG),
                        background = NmTheme.semanticColors.surface
                    ) { content() }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun save(bitmap: Bitmap, name: String) {
        val root = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val dir = File(root, "v210capture").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(name: String) = save(rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap(), name)

    /** 다이얼로그는 창이 따로라 화면 전체를 찍는다. */
    private fun captureScreen(name: String) {
        Thread.sleep(SETTLE_MILLIS)
        save(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(), name)
    }

    @Test fun `미리보기`() {
        show { PillPreviewScreen(state, usage, {}, {}) }
        capture("02_preview")
    }

    @Test fun `미리보기_한도_소진`() {
        show { PillPreviewScreen(state, usage, {}, {}, limitReached = true) }
        captureScreen("02a_preview_limit")
    }

    @Test fun `권한_거부`() {
        show { PillPermissionDeniedScreen(onBack = {}, onPickFromGallery = {}) }
        capture("03_permission_denied")
    }

    @Test fun `로딩`() {
        show { PillLoadingScreen(state) }
        capture("04_loading")
    }

    @Test fun `결과_없음`() {
        show { PillNotFoundScreen(state = state, onRetake = {}, onPickFromGallery = {}, onExit = {}) }
        capture("06_not_found")
    }

    @Test fun `분석_실패`() {
        show { PillAnalysisFailedScreen(message = null, onRetry = {}, onBack = {}) }
        capture("07_failed")
    }

    private companion object {
        const val TAG = "capture"
        const val PHOTO = 600
        const val SETTLE_MILLIS = 600L
    }
}
