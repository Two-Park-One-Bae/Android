package app.nursemate.pill

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import app.nursemate.core.model.PillCandidate
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 최종 결과 공유 — 텍스트 복사 · PDF 저장.

 ## 왜 둘인가
 스펙(NM-136)은 MVP 를 "텍스트 복사"까지만 잡았지만, iOS 가 간호사 인터뷰를 반영해 PDF 를
 함께 냈다(PillShareComposer.swift):

 > 파일 추출해도 다시 병원 컴퓨터에 입력해야 하고, 보안 때문에 외부 파일이 아예 안 열리는
 > 경우가 많다. 폰에서 쉽게 열리는 일반 텍스트나 PDF 가 좋다.

 그래서 **텍스트가 기본**(폰에서 바로 읽고 재입력·메신저 붙여넣기)이고 PDF 는 문서로
 남겨야 할 때의 보조다. 스펙에 없는 결정이라 여기 근거를 남긴다.
*/

/** 텍스트를 클립보드에 넣는다. 화면은 그대로 둔다(spec §최종 결과 — "화면 유지"). */
fun Context.copyPillResult(pills: List<PillCandidate>) {
    getSystemService<ClipboardManager>()
        ?.setPrimaryClip(ClipData.newPlainText(SHARE_TITLE, pills.toShareText()))

    // 안드로이드 13 부터는 시스템이 복사 알림을 대신 띄운다 — 그 아래에서만 낸다.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, "결과를 복사했어요", Toast.LENGTH_SHORT).show()
    }
}

/** PDF 로 만들어 공유 시트에 넘긴다. */
fun Context.sharePillResultPdf(pills: List<PillCandidate>) {
    val uri = runCatching { writePdf(pills.toShareText()) }
        .onFailure { Log.w(TAG, "PDF 생성 실패", it) }
        .getOrNull()

    if (uri == null) {
        Toast.makeText(this, "PDF를 만들지 못했어요", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = PDF_MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, SHARE_TITLE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, SHARE_TITLE))
}

/**
 * 공유 텍스트.
 *
 * iOS 와 같은 모양으로 맞춘다 — 같은 결과를 두 플랫폼이 다르게 적으면 인수인계 메모에
 * 섞였을 때 어느 쪽이 맞는지 알 수 없다.
 *
 * ⚠️ **고지는 정본을 쓴다.** iOS 는 공유물에만 다른 문장을 쓰고 있는데, NM-380 이
 * App Store 심사 대응으로 문구를 확정하며 "정본은 제출본과 일치시킨다"고 못박았다.
 * 밖으로 나가는 문서라 오히려 더 지켜야 하는 자리다.
 */
internal fun List<PillCandidate>.toShareText(today: LocalDate = LocalDate.now()): String {
    // ⚠️ buildString 안에서는 `size`·`forEachIndexed` 가 StringBuilder 쪽으로 붙는다
    //    (CharSequence 라서 조용히 컴파일된다). 목록을 밖에서 잡아 둔다.
    val pills = this
    return buildString {
        appendLine("널스메이트 · 알약 식별 결과")
        appendLine(today.format(DateFormat))
        appendLine()
        appendLine("식별된 알약 ${pills.size}개")
        appendLine(DIVIDER)

        pills.forEachIndexed { index, pill ->
            appendLine("${index + 1}. ${pill.pillName ?: "이름 미상"}")
            pill.companyName?.takeIf { it.isNotBlank() }?.let { appendLine("   제조사: $it") }
            appendLine("   품목코드: ${pill.pillCode}")
            appendLine()
        }

        appendLine(DIVIDER)
        append(DISCLAIMER)
    }
}

/**
 * A4 한 장씩 그린다.
 *
 * 텍스트 길이를 미리 알 수 없어 [StaticLayout] 으로 한 번 재고, 페이지 높이만큼 잘라
 * 옮겨 그린다 — 줄 중간에서 잘리지 않게 **줄 경계로만** 나눈다.
 */
private fun Context.writePdf(text: String): Uri {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = BODY_TEXT_SIZE
        color = android.graphics.Color.BLACK
    }
    val width = PAGE_WIDTH - MARGIN * 2
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()

    val document = PdfDocument()
    var line = 0
    var pageNumber = 1
    while (line < layout.lineCount) {
        val top = layout.getLineTop(line)
        // 이 페이지에 들어가는 마지막 줄을 찾는다.
        var last = line
        while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= PAGE_HEIGHT - MARGIN * 2) {
            last++
        }

        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        page.canvas.apply {
            save()
            translate(MARGIN.toFloat(), MARGIN - top.toFloat())
            clipRect(0f, top.toFloat(), width.toFloat(), layout.getLineBottom(last).toFloat())
            layout.draw(this)
            restore()
        }
        document.finishPage(page)

        line = last + 1
        pageNumber++
    }

    val dir = File(cacheDir, "share").apply { mkdirs() }
    val file = File(dir, PDF_NAME)
    file.outputStream().use { document.writeTo(it) }
    document.close()

    return FileProvider.getUriForFile(this, "$packageName.share", file)
}

private const val TAG = "NM393"
private const val SHARE_TITLE = "알약 식별 결과"
private const val PDF_NAME = "널스메이트_알약식별결과.pdf"
private const val PDF_MIME = "application/pdf"
private const val DIVIDER = "────────────────"

/** NM-380 확정 문구. 화면·공유물 모두 이 한 문장을 쓴다. */
private const val DISCLAIMER =
    "널스메이트의 알약 식별 결과는 참고용 보조 정보입니다. " +
        "투약 전 반드시 처방 내용과 약품 라벨을 확인하시고, 최종 판단은 의료진의 확인을 따라 주세요."

private val DateFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

// A4 를 72dpi 로 잡은 크기. iOS 가 같은 값을 쓴다.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 44
private const val BODY_TEXT_SIZE = 13f
