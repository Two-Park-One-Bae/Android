package app.nursemate.history

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillHistory
import app.nursemate.core.model.PillHistoryPill
import app.nursemate.core.model.PillHistorySummary
import app.nursemate.core.model.Usage
import app.nursemate.gallery.DesignGalleryActivity
import app.nursemate.home.NmBottomBar
import app.nursemate.home.NmTab
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * 식별 기록 V2.1.0 — 정본 「식별 기록 (NM-590)」 ① ~ ⑩ 을 같은 데이터로 그려 PNG 로 남긴다.
 * **단정이 없다 — 눈으로 대조하는 용도다.** 결과는 `/sdcard/Android/data/app.nursemate.debug/files/v210history/`.
 */
class V210HistoryCaptureTest {

    @get:Rule val rule = createAndroidComposeRule<DesignGalleryActivity>()

    private val today = LocalDate.of(2026, 10, 9)

    private fun s(id: String, at: String, name: String, count: Int) = PillHistorySummary(id, at, name, count)

    private val items = listOf(
        s("1", "2026-10-09T05:14:00Z", "타이레놀정 500mg", 5),
        s("2", "2026-10-09T00:02:00Z", "세파클러캡슐 250mg", 1),
        s("3", "2026-10-08T22:30:00Z", "게보린정", 2),
        s("4", "2026-10-08T09:40:00Z", "마이티셋정", 3),
        s("5", "2026-10-08T04:12:00Z", "아세트정", 6),
        s("6", "2026-10-07T14:20:00Z", "펜잘큐정", 2),
        s("7", "2026-10-07T06:05:00Z", "알렉스정 30mg", 1)
    )

    private fun pill(name: String, company: String, revoked: Boolean = false) =
        PillHistoryPill("c-$name", name, company, if (revoked) LicenseStatus.REVOKED else LicenseStatus.NORMAL, "")

    private val history = PillHistory(
        "1",
        "2026-10-09T05:14:00Z",
        listOf(
            pill("타이레놀정 500mg", "한국얀센"),
            pill("세파클러캡슐 250mg", "종근당"),
            pill("게보린정", "삼진제약"),
            pill("펜잘큐정", "종근당", revoked = true),
            pill("마이티셋정", "에이치엘비제약")
        )
    )

    private fun show(content: @Composable () -> Unit) {
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                NurseMateTheme {
                    Column(Modifier.fillMaxSize().testTag(TAG)) {
                        Column(Modifier.weight(1f)) { content() }
                        NmBottomBar(selected = NmTab.Pill, onSelect = {})
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun save(bitmap: Bitmap, name: String) {
        val root = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val dir = File(root, "v210history").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(name: String) = save(rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap(), name)

    private fun tab(state: HistoryListState, name: String) {
        show { PillTabScreen(Usage(15, 12, ""), state, today, {}, {}, {}, {}) }
        capture(name)
    }

    @Test fun `목록`() = tab(HistoryListState.Loaded(items, nextCursor = "next"), "01_list")

    @Test fun `기록_없음`() = tab(HistoryListState.Loaded(emptyList(), null), "02_empty")

    @Test fun `불러오기_실패`() = tab(HistoryListState.Failed, "03_failed")

    @Test fun `목록_끝`() = tab(HistoryListState.Loaded(items.take(2), nextCursor = null), "04_end")

    private fun detail(state: HistoryDetailState) = show {
        PillHistoryDetailScreen("10월 9일 (금) 오후 2:14", 5, state, false, {}, {}, {}, {}, {}, {})
    }

    @Test fun `기록_상세`() {
        detail(HistoryDetailState.Loaded(history))
        capture("05_detail")
    }

    @Test fun `더보기_메뉴_삭제_확인`() {
        detail(HistoryDetailState.Loaded(history))
        rule.onNodeWithContentDescription("더보기").performClick()
        rule.waitForIdle()
        capture("06_menu")
        rule.onNodeWithText("삭제").performClick()
        rule.waitForIdle()
        save(rule.onNode(isDialog()).captureToImage().asAndroidBitmap(), "07_delete_confirm")
    }

    @Test fun `조회_중`() {
        detail(HistoryDetailState.Loading)
        capture("09_loading")
    }

    @Test fun `조회_실패`() {
        detail(HistoryDetailState.Failed)
        capture("10_failed")
    }

    private companion object {
        const val TAG = "history"
    }
}
