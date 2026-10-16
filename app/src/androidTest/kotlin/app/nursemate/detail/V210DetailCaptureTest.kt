package app.nursemate.detail

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.gallery.DesignGalleryActivity
import app.nursemate.home.NmTab
import app.nursemate.navigation.NmTabScaffold
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * 세부정보 V2.1.0 — 정본 「세부정보 · 이미지 비교 (NM-589)」 ⑩-a · ⑩-e · ⑩-f · ⑩-g 를 탭 바와 함께 그려 PNG 로 남긴다.
 * 본문(⑩-b ~ ⑩-d)과 비교 뷰어는 실데이터 · 다이얼로그라 실기기에서 본다. **단정이 없다.**
 * 결과는 `/sdcard/Android/data/app.nursemate.debug/files/v210detail/`.
 */
class V210DetailCaptureTest {

    @get:Rule val rule = createAndroidComposeRule<DesignGalleryActivity>()

    private fun capture(state: DetailUiState, name: String) {
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                NurseMateTheme {
                    NmTabScaffold(
                        selected = NmTab.Pill,
                        onSelect = {},
                        modifier = Modifier.testTag(TAG),
                        background = NmTheme.semanticColors.surface
                    ) { PillDetailScreen(state = state, onBack = {}, onRetry = {}) }
                }
            }
        }
        rule.waitForIdle()
        val root = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val dir = File(root, "v210detail").apply { mkdirs() }
        val bitmap = rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `로딩`() = capture(DetailUiState.Loading, "10a_loading")

    @Test fun `데이터_없음`() = capture(DetailUiState.Empty, "10e_empty")

    @Test fun `오류`() = capture(DetailUiState.Failed, "10f_failed")

    @Test fun `허가_종료`() = capture(DetailUiState.Revoked, "10g_revoked")

    private companion object {
        const val TAG = "detail"
    }
}
