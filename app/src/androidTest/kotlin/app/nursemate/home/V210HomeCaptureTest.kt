package app.nursemate.home

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerState
import app.nursemate.core.model.Usage
import app.nursemate.gallery.DesignGalleryActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 홈 V2.1.0 — 정본 「홈 · V2.1.0 (NM-585)」 여섯 상태를 같은 데이터로 그려 PNG 로 남긴다.
 *
 * **단정이 없다 — 눈으로 대조하는 용도다.** 결과는
 * `/sdcard/Android/data/app.nursemate.debug/files/v210home/` 에 쌓인다.
 */
class V210HomeCaptureTest {

    @get:Rule val rule = createAndroidComposeRule<DesignGalleryActivity>()

    private val now = 1_800_000_000_000L

    private fun timer(label: String, category: TimerCategory, durationMin: Int, remainingSec: Int, state: TimerState) =
        CareTimer(
            id = label,
            label = label,
            category = category,
            durationSeconds = durationMin * 60,
            endAtEpochMillis = now + remainingSec * 1_000L,
            remainingSeconds = if (state == TimerState.PAUSED) remainingSec else null,
            state = state
        )

    private val ast = timer("AST", TimerCategory.TEST, 15, 12 * 60 + 34, TimerState.RUNNING)
    private val med = timer("투약 반응 관찰", TimerCategory.MEDICATION, 30, 21 * 60 + 5, TimerState.RUNNING)

    private fun capture(
        name: String,
        usage: Usage?,
        timers: List<CareTimer>,
        recent: HomeRecent,
        fontScale: Float? = null
    ) {
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                NurseMateTheme {
                    val density = LocalDensity.current
                    val scaled = fontScale?.let { Density(density.density, it) } ?: density
                    CompositionLocalProvider(LocalDensity provides scaled) {
                        Column(Modifier.fillMaxSize().background(NmTheme.semanticColors.surface).testTag(TAG)) {
                            HomeScreen(usage, timers, { now }, recent, {}, {}, {}, {}, {}, Modifier.weight(1f))
                            NmBottomBar(selected = NmTab.Home, onSelect = {})
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap()
        val root = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val dir = File(root, "v210home").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val recentItem = HomeRecent.Item("h1", "오후 2:14", "타이레놀정 500mg 외 3")

    @Test fun `기본`() = capture("1_default", Usage(15, 12, ""), listOf(ast, med), recentItem)

    @Test fun `빈_상태`() = capture("2_empty", Usage(15, 15, ""), emptyList(), HomeRecent.None)

    @Test fun `한도_소진`() = capture("3_exhausted", Usage(15, 0, ""), listOf(ast, med), recentItem)

    @Test fun `타이머_5개_긴_품목명`() = capture(
        "4_five_long",
        Usage(15, 7, ""),
        listOf(
            ast,
            med,
            timer("수혈 바이탈", TimerCategory.TREATMENT, 15, 0, TimerState.RINGING),
            timer("체위 변경", TimerCategory.TREATMENT, 120, 3_600, TimerState.RUNNING),
            timer("I/O 체크", TimerCategory.TREATMENT, 60, 2_000, TimerState.RUNNING)
        ),
        HomeRecent.Item("h2", "어제 오후 6:40", "타이레놀8시간이알서방정 650mg 외 2")
    )

    @Test fun `타이머_3개_일시정지`() = capture(
        "5_paused",
        Usage(15, 12, ""),
        listOf(ast, med, timer("체위 변경", TimerCategory.TREATMENT, 120, 4_360, TimerState.PAUSED)),
        recentItem
    )

    /** 정본에 없는 상태 — 한도가 큰 계정(개발 계정 1,000,000회)에서 숫자가 줄바꿈되던 회귀를 본다. */
    @Test fun `큰_한도`() = capture("7_huge_limit", Usage(1_000_000, 1_000_000, ""), listOf(ast, med), recentItem)

    /** 정본에 없는 상태 — 시스템 글꼴 2배. 기능 카드가 자라 초기화 줄이 잘리지 않는지 본다. */
    @Test fun `큰_글꼴`() = capture("8_font_2x", Usage(15, 0, ""), listOf(ast, med), recentItem, fontScale = 2f)

    /** 0회면 비활성 `사진 찍기` 를 눌러도 카드 클릭(알약 탭 · 한도 팝업)으로 새지 않는다(spec home). */
    @Test
    fun `한도_소진에서_사진_찍기를_눌러도_카드로_새지_않는다`() {
        var photo = 0
        var card = 0
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                NurseMateTheme {
                    HomeScreen(Usage(15, 0, ""), emptyList(), {
                        now
                    }, HomeRecent.None, { photo++ }, { card++ }, {}, {}, {})
                }
            }
        }
        rule.onNodeWithText("사진 찍기").performClick()
        rule.waitForIdle()
        assertEquals("사진 찍기", 0, photo)
        assertEquals("카드", 0, card)
    }

    @Test fun `남은_횟수_모름`() = capture("6_unknown", null, listOf(ast, med), recentItem)

    private companion object {
        const val TAG = "home"
    }
}
