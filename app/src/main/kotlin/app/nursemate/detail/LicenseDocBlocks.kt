package app.nursemate.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.Block
import app.nursemate.core.model.Cell
import app.nursemate.core.model.Row as DocRow
import app.nursemate.core.model.Span
import coil3.compose.AsyncImage

/*
 허가문서 블록 렌더러 — 디자인 `⑩-b 효능효과` · `⑩-c 용법용량 (표)`.

 서버가 원문을 구조화해 내려주고 앱은 파싱 없이 그리기만 한다. 계약이 "모르는 블록 타입은
 건너뛰고, 모르는 스타일은 일반 텍스트로" 라고 못박고 있어 그대로 따른다 — 서버가 블록을
 하나 늘린 날 세부정보 화면이 통째로 죽으면 안 된다.
*/

@Composable
internal fun ColumnScope.DocBlock(block: Block) {
    val colors = NmTheme.semanticColors
    when (block) {
        is Block.Heading -> Text(text = block.content.annotated(), style = DocHeading, color = colors.textPrimary)

        is Block.Paragraph -> Text(
            text = block.content.annotated(),
            style = DocParagraph,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        is Block.Table -> DocTable(block)

        // 용량 계산식·주사 도해 같은 그림이다. MVP 는 data URI 로 오지만 나중에 URL 로 바뀔 수
        // 있어 둘 다 Coil 에 그대로 넘긴다.
        is Block.Image -> AsyncImage(
            model = block.src,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth()
        )

        // 앱이 모르는 블록. 계약대로 건너뛴다.
        Block.Unsupported -> Unit
    }
}

/**
 * 표.
 *
 * ## 폭을 넘어가면 가로로 흐른다
 * 실데이터가 22열까지 온다. 화면에 욱여넣으면 글자가 세로로 쪼개져 읽을 수 없다.
 *
 * ## 병합은 가로만 실제로 그린다
 * ⚠️ `colspan` 은 칸을 넓혀 그대로 표현하지만 **`rowspan` 은 아래로 늘리지 않는다** —
 * Row 를 쌓는 구조로는 세로 병합을 그릴 수 없고, 그러자고 표 하나 때문에 커스텀 Layout 을
 * 짜는 값어치가 없다고 봤다. 대신 [gridRows] 로 **자리는 정확히 맞춘다** — 병합이 먹은
 * 칸을 빈 칸으로 채워 아래 행이 왼쪽으로 밀리지 않게 한다. 값은 첫 행에 한 번 나온다.
 */
@Composable
private fun ColumnScope.DocTable(table: Block.Table) {
    val colors = NmTheme.semanticColors
    if (table.caption != null) {
        Text(text = table.caption!!.annotated(), style = TableCaption, color = colors.textSecondary)
    }

    val grid = remember(table) { table.rows.gridRows() }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(modifier = Modifier.clip(shape).border(1.dp, colors.border, shape)) {
            grid.forEach { row ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    row.forEach { slot -> TableCell(slot) }
                }
            }
        }
    }
}

@Composable
private fun TableCell(slot: GridSlot) {
    val colors = NmTheme.semanticColors
    val cell = slot.cell
    Box(
        modifier = Modifier
            .width(ColumnWidth * slot.colspan)
            .fillMaxHeight()
            .background(if (cell?.header == true) NmColor.Neutral.C50 else colors.surface)
            .border(0.5.dp, colors.border)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        if (cell != null) {
            Text(
                text = cell.content.annotated(),
                style = if (cell.header) TableHeaderCell else TableBodyCell,
                color = if (cell.header) colors.textSecondary else colors.textPrimary
            )
        }
    }
}

/** 격자의 한 자리. [cell] 이 null 이면 위쪽 병합이 먹은 빈 자리다. */
internal data class GridSlot(val cell: Cell?, val colspan: Int)

/**
 * 병합이 먹은 자리를 채워 격자를 복원한다.
 *
 * 계약이 HTML 과 같아서 병합된 셀은 왼쪽 위에 한 번만 나오고 나머지 자리는 `cells` 에 아예
 * 없다. 그대로 그리면 아래 행들이 왼쪽으로 당겨져 열이 어긋난다.
 */
internal fun List<DocRow>.gridRows(): List<List<GridSlot>> {
    val taken = mutableSetOf<Pair<Int, Int>>()
    val rows = mutableListOf<MutableList<GridSlot>>()

    forEachIndexed { rowIndex, row ->
        val slots = mutableListOf<GridSlot>()
        var column = 0
        row.cells.forEach { cell ->
            while (rowIndex to column in taken) {
                slots += GridSlot(cell = null, colspan = 1)
                column++
            }
            val colspan = cell.colspan.coerceAtLeast(1)
            val rowspan = cell.rowspan.coerceAtLeast(1)
            repeat(rowspan) { r ->
                repeat(colspan) { c -> taken += (rowIndex + r) to (column + c) }
            }
            slots += GridSlot(cell = cell, colspan = colspan)
            column += colspan
        }
        rows += slots
    }
    return rows
}

/** 스팬 → 첨자가 살아 있는 문자열. 모르는 스타일은 일반 글자로 그린다(계약). */
internal fun List<Span>.annotated(): AnnotatedString = buildAnnotatedString {
    this@annotated.forEach { span ->
        when (span.style) {
            app.nursemate.core.model.SpanStyle.SUP -> withScript(BaselineShift.Superscript, span.text)
            app.nursemate.core.model.SpanStyle.SUB -> withScript(BaselineShift.Subscript, span.text)
            else -> append(span.text)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.withScript(shift: BaselineShift, text: String) {
    withStyle(SpanStyle(baselineShift = shift, fontSize = 0.75.em)) { append(text) }
}

/** 정본이 열마다 96~160 으로 다르지만 22열까지 오는 표에 열별 폭을 줄 근거가 없어 하나로 둔다. */
private val ColumnWidth = 120.dp

private val DocHeading = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
private val DocParagraph = NmTypography.body.copy(fontSize = 14.sp, lineHeight = 23.1.sp)
private val TableCaption = NmTypography.caption.copy(fontWeight = FontWeight.SemiBold)
private val TableHeaderCell = NmTypography.caption.copy(fontWeight = FontWeight.SemiBold, lineHeight = 16.8.sp)
private val TableBodyCell = NmTypography.body.copy(fontSize = 13.sp, lineHeight = 18.2.sp)
