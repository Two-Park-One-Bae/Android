package app.nursemate.timer

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerState
import app.nursemate.gallery.DesignGalleryActivity
import app.nursemate.home.NmTab
import app.nursemate.navigation.NmTabScaffold
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * 타이머 V2.1.0 — 정본 「타이머 (NM-591)」 13장 중 Android 에 있는 11장을 그려 PNG 로 남긴다.
 *
 * 시트 · 모달은 창이 따로라 `captureToImage` 로 안 잡힌다. 화면 전체를 `UiAutomation` 으로 찍는다
 * (상태 표시줄까지 들어간다). 눈으로 대조하는 용도이고, 단정은 버튼이 있는지 · 저장이 잠겼는지만 본다.
 * 결과는 `/sdcard/Android/data/app.nursemate.debug/files/v210timer/`.
 */
class V210TimerCaptureTest {

    @get:Rule val rule = createAndroidComposeRule<DesignGalleryActivity>()

    private val now = 1_800_000_000_000L

    private val timers = listOf(
        CareTimer("t0", "수혈 바이탈", TimerCategory.TREATMENT, durationSeconds = 900, endAtEpochMillis = now - 1_000),
        CareTimer("t1", "AST", TimerCategory.TEST, durationSeconds = 900, endAtEpochMillis = now + 754_000),
        CareTimer(
            "t2",
            "투약 반응 관찰",
            TimerCategory.MEDICATION,
            memo = "항생제 교체 후 관찰",
            durationSeconds = 1_800,
            endAtEpochMillis = now + 1_265_000
        ),
        CareTimer(
            "t3",
            "체위 변경",
            TimerCategory.TREATMENT,
            durationSeconds = 7_200,
            endAtEpochMillis = now + 4_360_000,
            remainingSeconds = 4_360,
            state = TimerState.PAUSED
        )
    )

    private val presets = listOf(
        TimerPreset("p1", "AST", TimerCategory.TEST, 900, sortOrder = 0),
        TimerPreset("p2", "수혈 바이탈", TimerCategory.TREATMENT, 900, sortOrder = 1),
        TimerPreset("p3", "투약 반응 관찰", TimerCategory.MEDICATION, 1_800, sortOrder = 2),
        TimerPreset("p4", "해열 재검", TimerCategory.MEDICATION, 1_800, sortOrder = 3),
        TimerPreset("p5", "바이탈 재측정", TimerCategory.TREATMENT, 900, sortOrder = 4),
        TimerPreset("p6", "체위 변경", TimerCategory.TREATMENT, 7_200, sortOrder = 5)
    )

    private fun show(content: @Composable () -> Unit) {
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                NurseMateTheme {
                    NmTabScaffold(
                        selected = NmTab.Timer,
                        onSelect = {},
                        background = NmTheme.semanticColors.surface
                    ) { content() }
                }
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun list(timers: List<CareTimer> = this.timers, memoEditing: String? = null) = TimerListScreen(
        timers = timers,
        now = now,
        memoEditing = memoEditing,
        onAdd = {},
        onPauseOrResume = {},
        onExtend = {},
        onMemoToggle = {},
        onMemoSave = { _, _ -> },
        onRemove = {},
        modifier = Modifier
    )

    @Composable
    private fun sheet(editing: Boolean) = TimerPresetSheet(
        presets = presets,
        editing = editing,
        onStart = {},
        onToggleEditing = {},
        onEditPreset = {},
        onDeletePreset = {},
        onReorder = {},
        onAdd = {},
        onDismiss = {}
    )

    /** 화면 전체 — 시트 · 모달 창까지 들어간다. 시트가 다 올라올 때까지 기다린다. */
    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(SHEET_SETTLE_MS)
        rule.waitForIdle()
        // 모달 창이 나타나는 애니메이션은 창 관리자가 돌려 Compose 시계로 넘길 수 없다 — 실제로 기다린다.
        Thread.sleep(WINDOW_SETTLE_MS)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val root = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val dir = File(root, "v210timer").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `목록`() {
        show { list() }
        rule.onNodeWithText("완료").assertIsDisplayed()
        rule.onNodeWithText("재개").assertIsDisplayed()
        rule.onNodeWithContentDescription("타이머 추가").assertIsDisplayed()
        capture("01_list")
    }

    @Test fun `빈_상태`() {
        show { list(timers = emptyList()) }
        rule.onNodeWithText("타이머 시작하기").assertIsDisplayed()
        capture("02_empty")
    }

    @Test fun `메모_입력`() {
        show { list(timers = timers.drop(1).take(2), memoEditing = "t1") }
        rule.onNodeWithContentDescription("메모 저장").assertIsDisplayed()
        capture("03_memo")
    }

    @Test fun `프리셋_시트`() {
        show {
            list()
            sheet(editing = false)
        }
        rule.onNodeWithText("편집").assertIsDisplayed()
        capture("04_preset_sheet")
    }

    @Test fun `프리셋_편집_모드`() {
        show {
            list()
            sheet(editing = true)
        }
        rule.onNodeWithText("프리셋 추가").assertIsDisplayed()
        capture("05_preset_editing")
    }

    @Test fun `프리셋_추가`() {
        show {
            list()
            TimerPresetEditSheet(preset = null, onSave = { _, _, _ -> }, onDelete = null, onDismiss = {})
        }
        rule.onNodeWithText("저장").assertIsNotEnabled()
        capture("06_preset_add")
    }

    @Test fun `프리셋_편집`() {
        show {
            list()
            TimerPresetEditSheet(preset = presets[0], onSave = { _, _, _ -> }, onDelete = {}, onDismiss = {})
        }
        rule.onNodeWithText("삭제").assertIsDisplayed()
        capture("07_preset_edit")
    }

    @Test fun `프리셋_삭제_확인`() {
        show {
            list()
            PresetDeleteDialog(onConfirm = {}, onCancel = {})
        }
        capture("08_preset_delete")
    }

    @Test fun `알람_권한_안내`() {
        show {
            list()
            notice(denied = false)
        }
        capture("09_permission")
    }

    @Test fun `알람_권한_거부`() {
        show {
            list()
            notice(denied = true)
        }
        capture("10_permission_denied")
    }

    @Test fun `울림_방식`() {
        show {
            list()
            TimerNoticeSheet(
                icon = NmSymbols.NotificationsActive,
                iconTint = NmColor.Primary.C600,
                iconBackground = NmColor.Primary.C50,
                title = "알람을 어떻게 알릴까요?",
                body = "타이머가 끝날 때 알리는 방식이에요.\n설정에서 언제든 바꿀 수 있어요.",
                primaryLabel = "이대로 시작하기",
                onPrimary = {},
                secondaryLabel = "나중에 할게요",
                onSecondary = {},
                onDismiss = {},
                content = { AlertModePicker(selected = app.nursemate.core.model.AlertMode.SOUND, onSelect = {}) }
            )
        }
        capture("11_alert_mode")
    }

    /** 앱이 띄우는 권한 시트 그대로 — 단계는 「알람 및 리마인더」. */
    @Composable
    private fun notice(denied: Boolean) =
        PermissionSheet(step = PermissionStep.EXACT_ALARM, denied = denied, onRequest = {}, onDismiss = {})

    private companion object {
        const val SHEET_SETTLE_MS = 1_000L
        const val WINDOW_SETTLE_MS = 800L
    }
}
