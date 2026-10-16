package app.nursemate.pill

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
import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.ImprintSource
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.model.PillCandidateFace
import app.nursemate.core.model.PillConditions
import app.nursemate.core.model.PillFormulation
import app.nursemate.core.model.PillShape
import app.nursemate.gallery.DesignGalleryActivity
import app.nursemate.home.NmTab
import app.nursemate.navigation.NmTabScaffold
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * 수정 · 후보 선택 V2.1.0 — 정본 「알약 식별 — 수정 · 후보 선택 (NM-588)」 을 탭 바와 함께 그려 PNG 로 남긴다.
 * **단정이 없다 — 눈으로 대조하는 용도다.** 낱알 이미지는 망에서 받으므로 회색 자리로 남을 수 있다.
 * 메뉴 · 각인 입력 줄은 창이 따로라 여기서 잡히지 않는다 — 실기기에서 본다.
 * 결과는 `/sdcard/Android/data/app.nursemate.debug/files/v210edit/`.
 */
class V210EditCaptureTest {

    @get:Rule val rule = createAndroidComposeRule<DesignGalleryActivity>()

    private val crop = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFB5523B.toInt()) }

    private val attribute = PillAttribute(
        pillId = "3",
        attributeToken = "tok",
        colorHexes = listOf("#EA7A35"),
        shape = PillShape.OVAL,
        formulation = PillFormulation.TABLET
    )

    private val faces =
        FaceInputs(front = FaceInput(imprint = "AX", imprintSource = ImprintSource.MODEL, hasMark = true))

    private fun candidate(
        name: String,
        front: PillCandidateFace,
        back: PillCandidateFace = PillCandidateFace(),
        revoked: Boolean = false
    ) = PillCandidate(
        "c-$name",
        if (revoked) LicenseStatus.REVOKED else LicenseStatus.NORMAL,
        name,
        "업체",
        "",
        "",
        front,
        back
    )

    private val list = listOf(
        candidate("타이레놀정 500mg", PillCandidateFace("ALX3", DividingLine.PLUS, hasMark = true)),
        candidate("알렉스정", PillCandidateFace("AX"), PillCandidateFace("DW", DividingLine.PLUS)),
        candidate("아세트정", PillCandidateFace("AX5", DividingLine.MINUS), PillCandidateFace(hasMark = true)),
        candidate(
            "에이엑스정",
            PillCandidateFace("AXE", hasMark = true),
            PillCandidateFace(dividingLine = DividingLine.PLUS)
        ),
        candidate(
            "타이레놀이알서방정",
            PillCandidateFace("TYLENOL ER", DividingLine.MINUS),
            PillCandidateFace("CKD", hasMark = true)
        ),
        candidate(
            "펜잘큐정",
            PillCandidateFace("AKX", DividingLine.MINUS),
            PillCandidateFace("CKD", hasMark = true),
            revoked = true
        )
    )

    private val loaded = CandidateUiState(candidates = list, ids = List(38) { "id$it" }, searched = true)

    @Suppress("LongParameterList")
    private fun show(
        candidates: CandidateUiState = loaded,
        selected: PillCandidate? = null,
        manual: Boolean = false,
        attr: PillAttribute = attribute,
        inputs: FaceInputs = faces,
        conditions: PillConditions = PillConditions(attributeToken = "tok")
    ) {
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                NurseMateTheme {
                    NmTabScaffold(
                        selected = NmTab.Pill,
                        onSelect = {},
                        modifier = Modifier.testTag(TAG),
                        background = NmTheme.semanticColors.surface
                    ) {
                        PillEditScreen(
                            number = 3,
                            manual = manual,
                            crop = if (manual) null else crop,
                            attribute = attr,
                            conditions = conditions,
                            onConditionsChange = {},
                            faces = inputs,
                            onFacesChange = {},
                            reading = null,
                            candidates = candidates,
                            selected = selected,
                            onSelect = {},
                            onConfirm = {},
                            onCancel = {},
                            onDetail = {},
                            onLoadMore = {},
                            onRetry = {},
                            onRetryLoadMore = {}
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun save(bitmap: Bitmap, name: String) {
        val root = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val dir = File(root, "v210edit").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(name: String) = save(rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap(), name)

    @Test fun `처음_들어왔을_때`() {
        show()
        capture("01_enter")
    }

    @Test fun `후보_선택`() {
        show(selected = list[0])
        capture("02_selected")
    }

    @Test fun `각인_없음_모름`() {
        show(
            inputs = FaceInputs(
                front = FaceInput(imprint = ""),
                back = FaceInput(imprintUnknown = true, lineUnknown = true)
            )
        )
        capture("11_imprint_none_unknown")
    }

    @Test fun `긴_각인`() {
        show(
            inputs = FaceInputs(
                front = FaceInput(imprint = "TYLENOL ER 650", imprintSource = ImprintSource.USER, hasMark = true)
            )
        )
        capture("13_long_imprint")
    }

    @Test fun `조회_중`() {
        show(candidates = CandidateUiState(loading = true))
        capture("14_loading")
    }

    @Test fun `후보_0개`() {
        show(candidates = CandidateUiState(searched = true))
        capture("15_empty")
    }

    @Test fun `조회_실패`() {
        show(candidates = CandidateUiState(searched = true, failed = true))
        capture("16_failed")
    }

    @Test fun `수동_추가`() {
        show(candidates = CandidateUiState(), manual = true, attr = PillAttribute(pillId = "m1"), inputs = FaceInputs())
        capture("19_manual")
    }

    @Test fun `추출_실패`() {
        show(
            candidates = CandidateUiState(),
            attr = PillAttribute(pillId = "2", error = "EXTRACTION_FAILED"),
            inputs = FaceInputs(),
            conditions = PillConditions()
        )
        capture("20_extraction_failed")
    }

    private companion object {
        const val TAG = "edit"
    }
}
