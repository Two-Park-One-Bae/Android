package app.nursemate.pill

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillFormulation
import app.nursemate.core.model.PillShape

/*
 * 속성 칩을 누르면 카드 안에서 펼쳐지는 선택판 — 디자인 `⑧-b 색상` · `⑧-c 모양` · `⑧-d 제형`.
 *
 * ## 색만 여러 개 고를 수 있다
 * 알약 하나가 두세 색을 함께 갖는 일이 흔해서(캡슐의 몸통·뚜껑) 색은 다중 선택이고,
 * 모양·제형은 하나다. 같은 것을 다시 누르면 조건에서 뺀다 — 잘못 고른 뒤 되돌릴 길이 있어야 한다.
 *
 * ## 미인식(UNKNOWN)은 목록에 없다
 * 서버가 못 읽었다는 표시일 뿐 사용자가 고를 값이 아니다. 정본 팔레트에도 없다.
 */

@Composable
internal fun ColorPanel(selected: List<PillColor>, onToggle: (PillColor) -> Unit) = Panel(gap = 9.dp) {
    Text(
        text = "여러 색을 선택할 수 있어요",
        style = PanelHint,
        color = NmTheme.semanticColors.textTertiary
    )
    SelectableColors.chunked(COLORS_PER_ROW).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { color ->
                ColorSwatchItem(
                    color = color,
                    selected = color in selected,
                    onClick = { onToggle(color) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.ColorSwatchItem(color: PillColor, selected: Boolean, onClick: () -> Unit) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(shape)
                .background(color.swatch)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) NmColor.Primary.C500 else colors.border,
                    shape = shape
                )
                .clickable(onClick = onClick)
        ) {
            if (color == PillColor.COLORLESS) ColorlessSlash()
        }
        Text(
            text = color.label,
            style = if (selected) SwatchLabelSelected else SwatchLabel,
            color = if (selected) NmColor.Primary.C600 else colors.textTertiary,
            textAlign = TextAlign.Center
        )
    }
}

/** 무색 칸의 사선. 흰 칸이 둘(하양·무색)이라 이게 없으면 어느 쪽인지 알 수 없다. */
@Composable
private fun ColorlessSlash() {
    Canvas(modifier = Modifier.size(30.dp)) {
        drawLine(
            color = NmColor.Neutral.C400,
            start = Offset(x = size.width * 0.15f, y = size.height * 0.85f),
            end = Offset(x = size.width * 0.85f, y = size.height * 0.15f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun ShapePanel(selected: PillShape?, onSelect: (PillShape) -> Unit) = Panel(gap = 8.dp) {
    ChoiceRows(items = SelectableShapes) { shape ->
        PanelChoice(
            label = shape.label,
            selected = shape == selected,
            verticalPadding = 8.dp,
            onClick = { onSelect(shape) }
        ) { tint -> PanelShapeIcon(shape = shape, tint = tint) }
    }
}

@Composable
internal fun FormulationPanel(selected: PillFormulation?, onSelect: (PillFormulation) -> Unit) = Panel(gap = 8.dp) {
    ChoiceRows(items = SelectableFormulations) { formulation ->
        PanelChoice(
            label = formulation.label,
            selected = formulation == selected,
            verticalPadding = 10.dp,
            onClick = { onSelect(formulation) }
        ) { tint ->
            PanelFormulationIcon(
                formulation = formulation,
                tint = tint,
                // 안쪽 홈은 칸 배경색으로 파낸다 — 선택 여부에 따라 배경이 달라진다.
                groove = if (formulation == selected) NmColor.Primary.C100 else NmColor.Neutral.C100
            )
        }
    }
}

/** 회색 바탕의 펼침판 한 장. 세 패널이 모두 같은 껍데기를 쓴다. */
@Composable
private fun Panel(gap: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NmColor.Neutral.C100, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content
    )
}

/**
 * 네 칸씩 끊어 배치한다.
 *
 * 마지막 줄이 모자라면 빈 칸으로 채운다 — 안 채우면 세 칸이 줄 전체로 늘어나 위 줄과 폭이 어긋난다.
 */
@Composable
private fun <T> ColumnScope.ChoiceRows(items: List<T>, item: @Composable RowScope.(T) -> Unit) {
    items.chunked(CHOICES_PER_ROW).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { item(it) }
            repeat(CHOICES_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

/**
 * 모양·제형 패널의 칸 하나.
 *
 * ⚠️ 정본은 폭을 70 으로 못박지만 weight 로 나눈다. 70×4 + 간격 24 = 304 라 좁은 기기에서
 * 카드 안쪽 폭을 넘는다 — 고정 폭을 지키면 마지막 칸이 잘린다.
 */
@Composable
private fun RowScope.PanelChoice(
    label: String,
    selected: Boolean,
    verticalPadding: Dp,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(if (selected) NmColor.Primary.C50 else colors.surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) NmColor.Primary.C500 else colors.border,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(if (selected) NmColor.Primary.C500 else colors.textTertiary)
        Text(
            text = label,
            style = if (selected) ChoiceLabelSelected else ChoiceLabel,
            color = if (selected) NmColor.Primary.C600 else colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** 사용자가 고를 수 있는 값들 — 미인식은 뺀다. 순서는 정본 팔레트와 같다. */
private val SelectableColors = PillColor.entries.filter { it != PillColor.UNKNOWN }
private val SelectableShapes = PillShape.entries.filter { it != PillShape.UNKNOWN }
private val SelectableFormulations = PillFormulation.entries.filter { it != PillFormulation.UNKNOWN }

private const val COLORS_PER_ROW = 8
private const val CHOICES_PER_ROW = 4

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val PanelHint = NmTypography.caption
private val SwatchLabel = NmTypography.caption.copy(fontSize = 9.sp)
private val SwatchLabelSelected = SwatchLabel.copy(fontWeight = FontWeight.SemiBold)
private val ChoiceLabel = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal)
private val ChoiceLabelSelected = ChoiceLabel.copy(fontWeight = FontWeight.SemiBold)
