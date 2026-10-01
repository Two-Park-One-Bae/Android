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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillFormulation
import app.nursemate.core.model.PillShape

/*
 * 외형 칩을 누르면 **칩 아래로 뜨는 메뉴** — 정본 ③ 색상 · ④ 모양 · ⑤ 제형.
 *
 * ## 맨 위가 늘 「전체」다
 * 조건을 푸는 길이 메뉴 안에 있어야 사용자가 「잘못 골랐다」를 같은 자리에서 되돌린다 —
 * 정본이 「되돌리기: 각인 칸 안 · 외형은 메뉴의 전체」로 적는다.
 *
 * 그 줄 오른쪽에 **사진 기준** 미리보기를 둔다. 조건을 풀면 무엇으로 돌아가는지, 즉 모델이
 * 무엇을 읽었는지를 그 자리에서 보여 주는 것이다.
 *
 * ## 고른 칸은 호박색이다
 * 칩·드롭다운과 같은 규칙이다([conditionTone]) — 화면 어디서든 「내가 정한 값」은 호박색이다.
 * v0 은 여기만 파란 선택 표시를 썼는데, 그러면 칸은 파랗고 칩은 호박색이 된다.
 *
 * ## 색만 여러 개 고를 수 있다
 * 알약 하나가 두세 색을 함께 갖는 일이 흔해서(캡슐의 몸통·뚜껑) 색은 다중 선택이고,
 * 모양·제형은 하나다. 같은 것을 다시 누르면 조건에서 뺀다.
 *
 * ## 미인식(UNKNOWN)은 목록에 없다
 * 서버가 못 읽었다는 표시일 뿐 사용자가 고를 값이 아니다. 정본 팔레트에도 없다.
 */

@Composable
internal fun ColorMenu(
    selected: List<PillColor>,
    modelHexes: List<String>?,
    onToggle: (PillColor) -> Unit,
    onClear: () -> Unit
) = AttributeMenu(
    all = selected.isEmpty(),
    onClear = onClear,
    preview = {
        // 정본은 여러 색을 -4 만큼 겹쳐 둔다 — 두세 개가 작은 자리에 다 들어가게.
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
            modelHexes.orEmpty().forEach { HexSwatch(it) }
        }
    }
) {
    MenuRows(items = SelectableColors) { color ->
        MenuCell(
            label = color.label,
            selected = color in selected,
            onClick = { onToggle(color) }
        ) { certain ->
            Box(modifier = Modifier.size(28.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.swatch)
                        .border(
                            width = if (certain) 2.dp else 1.dp,
                            color = if (certain) NmColor.Warning.C700 else NmColor.Neutral.C300,
                            shape = CircleShape
                        )
                )
                if (color == PillColor.COLORLESS) ColorlessSlash()
            }
        }
    }
}

@Composable
internal fun ShapeMenu(
    selected: PillShape?,
    modelShape: PillShape?,
    onSelect: (PillShape) -> Unit,
    onClear: () -> Unit
) = AttributeMenu(
    all = selected == null,
    onClear = onClear,
    preview = { modelShape?.let { PanelShapeIcon(shape = it, tint = NmTheme.semanticColors.textSecondary) } }
) {
    MenuRows(items = SelectableShapes) { shape ->
        MenuCell(label = shape.label, selected = shape == selected, onClick = { onSelect(shape) }) { certain ->
            PanelShapeIcon(shape = shape, tint = iconTint(certain))
        }
    }
}

@Composable
internal fun FormulationMenu(
    selected: PillFormulation?,
    modelFormulation: PillFormulation?,
    onSelect: (PillFormulation) -> Unit,
    onClear: () -> Unit
) = AttributeMenu(
    all = selected == null,
    onClear = onClear,
    preview = {
        modelFormulation?.let {
            PanelFormulationIcon(
                formulation = it,
                tint = NmTheme.semanticColors.textSecondary,
                groove = NmTheme.semanticColors.surface
            )
        }
    }
) {
    MenuRows(items = SelectableFormulations) { formulation ->
        MenuCell(
            label = formulation.label,
            selected = formulation == selected,
            onClick = { onSelect(formulation) }
        ) { certain ->
            PanelFormulationIcon(
                formulation = formulation,
                tint = iconTint(certain),
                // 안쪽 홈은 칸 배경색으로 파낸다 — 선택 여부에 따라 배경이 달라진다.
                groove = if (certain) NmColor.Warning.C50 else NmTheme.semanticColors.surface
            )
        }
    }
}

/**
 * 메뉴 한 장 — 정본 358 폭, `$surface`, r16, padding 10, gap 6.
 *
 * @param all 지금 「전체」인가(= 고른 것이 없다)
 * @param preview 모델이 읽은 값 그림. 없으면 비워 둔다
 */
@Composable
private fun AttributeMenu(
    all: Boolean,
    onClear: () -> Unit,
    preview: @Composable () -> Unit,
    rows: @Composable ColumnScope.() -> Unit
) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, NmColor.Neutral.C200, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClear)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                if (all) {
                    Icon(
                        painter = painterResource(R.drawable.nm_ic_check),
                        contentDescription = null,
                        tint = NmColor.Primary.C600,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(text = "전체", style = MenuAll, color = colors.textPrimary)
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(width = 40.dp, height = 28.dp),
                contentAlignment = Alignment.Center,
                content = { preview() }
            )
            Text(text = "사진 기준", style = PreviewLabel, color = colors.textTertiary)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NmColor.Neutral.C200))
        rows()
    }
}

/**
 * 네 칸씩 끊어 배치한다.
 *
 * 마지막 줄이 모자라면 빈 칸으로 채운다 — 안 채우면 세 칸이 줄 전체로 늘어나 위 줄과 폭이 어긋난다.
 */
@Composable
private fun <T> ColumnScope.MenuRows(items: List<T>, cell: @Composable RowScope.(T) -> Unit) {
    items.chunked(CELLS_PER_ROW).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { cell(it) }
            repeat(CELLS_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

/** 칸 하나 — 고르면 호박색이다(정본 ③·⑤). @param icon 받는 값은 「고른 칸인가」다. */
@Composable
private fun RowScope.MenuCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Boolean) -> Unit
) {
    val colors = NmTheme.semanticColors
    val tone = conditionTone(selected)
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(if (selected) tone.fill else Color.Transparent)
            .let { if (selected) it.border(1.dp, tone.stroke, shape) else it }
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(width = 44.dp, height = 30.dp),
            contentAlignment = Alignment.Center,
            content = { icon(selected) }
        )
        Text(
            text = label,
            style = CellLabel.copy(fontWeight = tone.weight),
            color = if (selected) tone.label else colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** 무색 칸의 사선. 흰 칸이 둘(하양·무색)이라 이게 없으면 어느 쪽인지 알 수 없다. */
@Composable
private fun ColorlessSlash() {
    Canvas(modifier = Modifier.size(28.dp)) {
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
private fun iconTint(certain: Boolean): Color =
    if (certain) NmColor.Warning.C700 else NmTheme.semanticColors.textSecondary

/** 사용자가 고를 수 있는 값들 — 미인식은 뺀다. 순서는 정본 팔레트와 같다. */
private val SelectableColors = PillColor.entries.filter { it != PillColor.UNKNOWN }
private val SelectableShapes = PillShape.entries.filter { it != PillShape.UNKNOWN }
private val SelectableFormulations = PillFormulation.entries.filter { it != PillFormulation.UNKNOWN }

private const val CELLS_PER_ROW = 4

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val MenuAll = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
private val PreviewLabel = NmTypography.caption.copy(fontSize = 11.sp)
private val CellLabel = NmTypography.caption.copy(fontSize = 11.sp)
