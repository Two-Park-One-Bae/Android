package app.nursemate.pill

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.vision.CropBox
import app.nursemate.core.vision.DetectedPill
import app.nursemate.core.vision.Detection
import app.nursemate.gallery.DesignGalleryActivity
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * 식별 결과 V2.1.0 — 정본 「알약 식별 (V2.1.0)」 ① ~ ⑩ 을 그려 PNG 로 남긴다.
 * **단정이 없다 — 눈으로 대조하는 용도다.** 사진은 그려 낸 가짜다(약포 사진을 쓰지 않는다).
 * 결과는 `/sdcard/Android/data/app.nursemate.debug/files/v210result/`.
 */
class V210ResultCaptureTest {

    @get:Rule val rule = createAndroidComposeRule<DesignGalleryActivity>()

    private fun solid(width: Int, height: Int, color: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }

    /** 회색 판 위에 알약 셋 — 검출 좌표와 같은 자리에 그린다. */
    private val photo = solid(PHOTO, PHOTO, 0xFFD9DDE2.toInt()).also { bitmap ->
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        boxes.forEachIndexed { index, b ->
            paint.color = PILL_COLORS[index]
            canvas.drawOval(b.x * PHOTO, b.y * PHOTO, (b.x + b.width) * PHOTO, (b.y + b.height) * PHOTO, paint)
        }
    }

    private val detected = boxes.mapIndexed { index, b ->
        DetectedPill(
            Detection(index, 0.9f, b.x, b.y, b.width, b.height),
            solid(80, 48, PILL_COLORS[index]),
            CropBox(0, 0, 80, 48)
        )
    }

    private fun candidate(name: String, company: String, revoked: Boolean = false) = PillCandidate(
        "c-$name",
        if (revoked) LicenseStatus.REVOKED else LicenseStatus.NORMAL,
        name,
        company,
        "",
        ""
    )

    private fun card(n: Int, status: PillResultStatus, selected: PillCandidate? = null, id: String = "$n") =
        PillResultCard(id, n, status, detected.getOrNull(n - 1).takeIf { !id.isManualPill }, selected)

    private val pending = PillResultUi(
        photo,
        3,
        listOf(
            card(1, PillResultStatus.PENDING),
            card(2, PillResultStatus.FAILED),
            card(3, PillResultStatus.PENDING)
        )
    )

    private val partial = PillResultUi(
        photo,
        3,
        listOf(
            card(1, PillResultStatus.IDENTIFIED, candidate("세파클러캡슐 250mg", "종근당")),
            card(2, PillResultStatus.FAILED),
            card(3, PillResultStatus.IDENTIFIED, candidate("타이레놀정 500mg", "한국얀센"))
        )
    )

    private val all = PillResultUi(
        photo,
        3,
        listOf(
            card(1, PillResultStatus.IDENTIFIED, candidate("세파클러캡슐 250mg", "종근당")),
            card(2, PillResultStatus.IDENTIFIED, candidate("펜잘큐정", "종근당", revoked = true)),
            card(3, PillResultStatus.IDENTIFIED, candidate("타이레놀정 500mg", "한국얀센")),
            card(4, PillResultStatus.IDENTIFIED, candidate("아세트정", "한국얀센"), id = "m1")
        )
    )

    private fun show(ui: PillResultUi, save: HistorySave = HistorySave.Idle) {
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                NurseMateTheme {
                    PillResultScreen(
                        ui = ui,
                        save = save,
                        onExit = {},
                        onEditPill = {},
                        onRemovePill = {},
                        onAddPill = {},
                        onDetail = {},
                        onCopyText = {},
                        onSavePdf = {},
                        onSave = {},
                        onLeaveUnsaved = {},
                        onDismissSaveFailure = {},
                        modifier = Modifier.testTag(TAG)
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun save(bitmap: Bitmap, name: String) {
        val root = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val dir = File(root, "v210result").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(name: String) = save(rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap(), name)

    private fun captureDialog(name: String) = save(rule.onNode(isDialog()).captureToImage().asAndroidBitmap(), name)

    @Test fun `선택_전`() {
        show(pending)
        capture("01_pending")
    }

    @Test fun `일부_확정`() {
        show(partial)
        capture("02_partial")
    }

    @Test fun `전부_확정_허가종료_직접추가`() {
        show(all)
        capture("03_all")
    }

    @Test fun `더보기_메뉴_삭제_확인`() {
        show(partial)
        rule.onAllNodesWithContentDescription("더보기")[2].performClick()
        rule.waitForIdle()
        capture("04_menu")
        rule.onNodeWithText("삭제").performClick()
        rule.waitForIdle()
        captureDialog("05_delete_confirm")
    }

    @Test fun `완료_확인_미확정_남음`() {
        show(partial)
        rule.onNodeWithText("완료").performClick()
        rule.waitForIdle()
        captureDialog("06_finish_confirm")
    }

    @Test fun `저장_중`() {
        show(partial, HistorySave.Saving)
        capture("07_saving")
    }

    @Test fun `저장_실패`() {
        show(partial, HistorySave.Failed(retryable = true))
        captureDialog("08_save_failed")
    }

    @Test fun `저장_실패_다시_해도_안_될_때`() {
        show(partial, HistorySave.Failed(retryable = false))
        captureDialog("08b_save_rejected")
    }

    @Test fun `이탈_확인`() {
        show(partial)
        rule.onNodeWithContentDescription("뒤로").performClick()
        rule.waitForIdle()
        captureDialog("09_exit_confirm")
    }

    @Test fun `공유_방식_선택`() {
        show(partial)
        rule.onNodeWithText("공유").performClick()
        rule.waitForIdle()
        Thread.sleep(SHEET_SETTLE_MILLIS)
        save(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
            "10_share_sheet"
        )
    }

    private companion object {
        const val TAG = "result"
        const val PHOTO = 600
        const val SHEET_SETTLE_MILLIS = 600L

        /** 정규화 영역. */
        val boxes = listOf(
            Box(0.18f, 0.40f, 0.26f, 0.12f),
            Box(0.55f, 0.38f, 0.22f, 0.14f),
            Box(0.42f, 0.62f, 0.14f, 0.13f)
        )

        val PILL_COLORS = listOf(0xFFF4E1A6.toInt(), 0xFFFFFFFF.toInt(), 0xFFB5523B.toInt())
    }
}

private data class Box(val x: Float, val y: Float, val width: Float, val height: Float)
