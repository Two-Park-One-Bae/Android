package app.nursemate.settings

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.auth.LoginScreen
import app.nursemate.auth.LoginUiState
import app.nursemate.consent.ConsentScreen
import app.nursemate.consent.ConsentSettingsScreen
import app.nursemate.consent.ConsentSettingsUiState
import app.nursemate.consent.ConsentUiState
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.model.AlertMode
import app.nursemate.core.model.ConsentDefinition
import app.nursemate.core.model.ConsentType
import app.nursemate.gallery.DesignGalleryActivity
import app.nursemate.home.NmTab
import app.nursemate.navigation.NmTabScaffold
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * 인증 · 설정 V2.1.0 — 정본 「인증 · 설정 (NM-592)」 12장을 그려 PNG 로 남긴다.
 *
 * 화면 전체를 `UiAutomation` 으로 찍는다(상태 표시줄 포함). 눈으로 대조하는 용도이고, 단정은
 * 버튼이 있는지 · 잠겼는지 · 약관 및 동의에 탭 바가 있는지만 본다.
 * 결과는 `/sdcard/Android/data/app.nursemate.debug/files/v210auth/`.
 */
class V210AuthSettingsCaptureTest {

    @get:Rule val rule = createAndroidComposeRule<DesignGalleryActivity>()

    private val terms = ConsentDefinition(ConsentType.TERMS, "2026-09-01", true, "https://example.com/t", "이용약관")
    private val privacy =
        ConsentDefinition(ConsentType.PRIVACY, "2026-09-01", true, "https://example.com/p", "개인정보처리방침")
    private val overseas = ConsentDefinition(
        ConsentType.OVERSEAS,
        "2026-09-01",
        false,
        "https://example.com/o",
        "개인정보 국외 이전 및 제3자 제공"
    )

    private fun show(content: @Composable () -> Unit) {
        rule.activity.runOnUiThread { rule.activity.setContent { NurseMateTheme { content() } } }
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        Thread.sleep(SETTLE_MS)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val root = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val dir = File(root, "v210auth").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun consent(definitions: List<ConsentDefinition>, checked: Set<ConsentType>) = ConsentScreen(
        state = ConsentUiState(definitions = definitions, checked = checked, loading = false),
        onToggle = {},
        onToggleAll = {},
        onSubmit = {},
        onCancel = {},
        onOpenPolicy = {},
        onRetry = {}
    )

    @Composable
    private fun settings(confirm: SettingsConfirm? = null) = NmTabScaffold(
        selected = NmTab.Settings,
        onSelect = {},
        background = NmTheme.semanticColors.surface,
        overlay = confirm?.let { { SettingsConfirmDialog(it, onConfirmed = {}, onDismiss = {}) } }
    ) {
        SettingsScreen(
            state = SettingsUiState(),
            onConfirm = {},
            alertMode = AlertMode.SOUND,
            onAlertMode = {},
            onOpenConsents = {}
        )
    }

    @Composable
    private fun consentSettings(checked: Set<ConsentType>) = NmTabScaffold(
        selected = NmTab.Settings,
        onSelect = {},
        background = NmTheme.semanticColors.surface
    ) {
        ConsentSettingsScreen(
            state = ConsentSettingsUiState(
                definitions = listOf(terms, privacy, overseas),
                checked = checked,
                baseline = setOf(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.OVERSEAS),
                loading = false
            ),
            onBack = {},
            onToggle = {},
            onSave = {},
            onOpenPolicy = {},
            onRetry = {}
        )
    }

    @Test fun `로그인`() {
        show { LoginScreen(state = LoginUiState(), onGoogleClick = {}, onAppleClick = {}, onKakaoClick = {}) }
        rule.onNodeWithText("카카오 로그인").assertIsDisplayed()
        capture("01_login")
    }

    @Test fun `동의_온보딩`() {
        show { consent(listOf(terms, privacy), setOf(ConsentType.TERMS, ConsentType.PRIVACY)) }
        rule.onNodeWithText("동의하고 계속").assertIsEnabled()
        capture("02_consent")
    }

    @Test fun `동의_온보딩_미완료`() {
        show { consent(listOf(terms, privacy), emptySet()) }
        rule.onNodeWithText("동의하고 계속").assertIsNotEnabled()
        capture("03_consent_incomplete")
    }

    @Test fun `동의_온보딩_선택_처음`() {
        show { consent(listOf(terms, privacy, overseas), emptySet()) }
        capture("04_consent_optional_first")
    }

    @Test fun `동의_온보딩_선택_필수만`() {
        show { consent(listOf(terms, privacy, overseas), setOf(ConsentType.TERMS, ConsentType.PRIVACY)) }
        capture("05_consent_optional_required")
    }

    @Test fun `동의_취소_확인`() {
        show { consent(listOf(terms, privacy), setOf(ConsentType.TERMS, ConsentType.PRIVACY)) }
        rule.onNodeWithText("취소").performClick()
        rule.onNodeWithText("로그인 화면으로 돌아갈까요?").assertIsDisplayed()
        capture("06_consent_cancel")
    }

    @Test fun `설정`() {
        show { settings() }
        rule.onNodeWithText("약관 및 동의").assertIsDisplayed()
        capture("07_settings")
    }

    @Test fun `약관_및_동의_변경_없음`() {
        show { consentSettings(setOf(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.OVERSEAS)) }
        rule.onNodeWithText("저장").assertIsNotEnabled()
        rule.onNodeWithText("설정").assertIsDisplayed()
        capture("08_consent_settings")
    }

    @Test fun `약관_및_동의_선택_해제`() {
        show { consentSettings(setOf(ConsentType.TERMS, ConsentType.PRIVACY)) }
        rule.onNodeWithText("저장").assertIsEnabled()
        capture("09_consent_settings_changed")
    }

    @Test fun `로그아웃_확인`() {
        show { settings(SettingsConfirm.SignOut) }
        rule.onNodeWithText("로그아웃할까요?").assertIsDisplayed()
        capture("10_signout")
    }

    @Test fun `계정_삭제_확인`() {
        show { settings(SettingsConfirm.Delete) }
        rule.onNodeWithText("계정을 삭제할까요?").assertIsDisplayed()
        capture("11_delete")
    }

    private companion object {
        const val SETTLE_MS = 600L
    }
}
