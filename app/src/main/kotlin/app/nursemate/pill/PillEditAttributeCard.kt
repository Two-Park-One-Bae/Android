package app.nursemate.pill

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillColor

/**
 * 수정 화면 맨 위의 속성 카드 — 디자인 `⑧-a` 및 펼침 상태 `⑧-b·c·d`.
 *
 * ## ⑤ 인식 결과의 칩과 다른 물건이다
 * 겉모습이 비슷해 [PillAttributeChips] 를 그대로 쓰고 싶어지지만, 여기 칩은 **누르는 것**이다.
 * 그래서 정본이 라벨을 '속성' 하나로 합치고(⑤ 는 색상·모양·제형 셋), 글자를 13 으로 키우고,
 * 꺾쇠를 달았다. 열린 칩은 `primary-50` 바탕에 `primary-500` 테두리로 어디를 고치는 중인지 알린다.
 *
 * ## 투명 여부는 색상판에만 붙는다
 * 정본에서 이 줄은 ⑧-b(색상 펼침)에만 있다. 투명은 색을 고를 때 함께 판단하는 것이라
 * 늘 띄워 두면 카드만 길어진다.
 *
 */
@Composable
fun PillEditAttributeCard(
    number: Int,
    crop: Bitmap?,
    attribute: PillAttribute,
    faces: FaceInputs,
    open: AttributePanel?,
    onToggle: (AttributePanel) -> Unit,
    onColorToggle: (PillColor) -> Unit,
    onTransparentChange: (Boolean) -> Unit,
    onChange: (PillAttribute) -> Unit,
    imprint: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NmTheme.semanticColors.surface, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
    ) {
        PillHeader(number = number, crop = crop)
        CardDivider()
        AttributeRow(attribute = attribute, open = open, onToggle = onToggle)

        when (open) {
            AttributePanel.Color -> ColorPanel(selected = attribute.colors.orEmpty(), onToggle = onColorToggle)

            AttributePanel.Shape -> ShapePanel(selected = attribute.shape) { shape ->
                // 같은 값을 다시 누르면 조건에서 뺀다 — 잘못 골랐을 때 되돌릴 길이 필요하다.
                onChange(attribute.copy(shape = shape.takeIf { it != attribute.shape }))
            }

            AttributePanel.Formulation -> FormulationPanel(selected = attribute.formulation) { formulation ->
                onChange(attribute.copy(formulation = formulation.takeIf { it != attribute.formulation }))
            }

            // 각인판은 칩이 아니라 각인 줄 아래에 붙는다(정본 ⑧-e 자식 순서).
            AttributePanel.Imprint, null -> Unit
        }

        if (open == AttributePanel.Color) {
            CardDivider()
            TransparentRow(checked = attribute.isTransparent, onCheckedChange = onTransparentChange)
        }

        CardDivider()
        ImprintRow(
            faces = faces,
            open = open == AttributePanel.Imprint,
            onClick = { onToggle(AttributePanel.Imprint) }
        )
        if (open == AttributePanel.Imprint) imprint()
    }
}

@Composable
private fun PillHeader(number: Int, crop: Bitmap?) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (crop != null) {
            Image(
                bitmap = crop.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NmColor.Neutral.C100)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "알약 $number", style = CardTitle, color = colors.textPrimary)
            Text(text = "속성을 수정하면 후보가 바뀌어요", style = CardSubtitle, color = colors.textTertiary)
        }
    }
}

/**
 * 색·모양·제형 칩 한 줄.
 *
 * 칩 묶음만 가로로 흐르게 둔다. 다색 알약이면 점이 늘어 정본보다 넓어지는데, 줄바꿈을 하면
 * 카드 높이가 들쭉날쭉해지고 아래 후보 목록이 흔들린다.
 */
@Composable
private fun AttributeRow(attribute: PillAttribute, open: AttributePanel?, onToggle: (AttributePanel) -> Unit) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "속성", style = RowLabel, color = colors.textSecondary, modifier = Modifier.width(44.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val selectedColors = attribute.colors.orEmpty()
            AttributeChip(
                open = open == AttributePanel.Color,
                label = selectedColors.chipLabel(attribute.isTransparent),
                onClick = { onToggle(AttributePanel.Color) }
            ) { ColorDots(selectedColors) }

            AttributeChip(
                open = open == AttributePanel.Shape,
                label = attribute.shape?.label ?: UNSET,
                onClick = { onToggle(AttributePanel.Shape) }
            ) { tint -> attribute.shape?.let { ShapeIcon(shape = it, tint = tint) } }

            AttributeChip(
                open = open == AttributePanel.Formulation,
                label = attribute.formulation?.chipLabel ?: UNSET,
                onClick = { onToggle(AttributePanel.Formulation) }
            ) { tint -> attribute.formulation?.let { FormulationIcon(formulation = it, tint = tint) } }
        }
    }
}

@Composable
private fun AttributeChip(
    open: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit
) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(8.dp)
    val accent = if (open) NmColor.Primary.C500 else colors.textTertiary
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (open) NmColor.Primary.C50 else NmColor.Neutral.C100)
            .let { if (open) it.border(1.dp, NmColor.Primary.C500, shape) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon(accent)
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = ChipLabel,
                color = if (open) NmColor.Primary.C600 else colors.textPrimary
            )
        }
        Icon(
            painter = painterResource(R.drawable.nm_ic_chevron_down),
            contentDescription = null,
            tint = accent,
            // 펼쳐지면 같은 꺾쇠를 뒤집는다. 정본의 chevron-up 과 같은 그림이 된다.
            modifier = Modifier.size(12.dp).rotate(if (open) 180f else 0f)
        )
    }
}

/** 고른 색을 점으로 늘어놓는다. 하나도 없으면 칩이 텅 비지 않게 아무것도 그리지 않는다. */
@Composable
private fun ColorDots(colors: List<PillColor>) {
    val border = NmTheme.semanticColors.border
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color.swatch, CircleShape)
                    .let { if (color.needsOutline) it.border(1.dp, border, CircleShape) else it }
            )
        }
    }
}

@Composable
private fun TransparentRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "투명 여부", style = RowLabel, color = colors.textSecondary)
            Text(text = "빛이 비치는 반투명·투명 재질", style = CardSubtitle, color = colors.textTertiary)
        }
        NmSwitch(checked = checked)
    }
}

/**
 * 정본 그대로의 스위치(46×28, 손잡이 24).
 *
 * Material3 `Switch` 를 쓰지 않는다 — 크기·손잡이 비율이 고정이라 정본과 어긋나고,
 * 눌림 표시가 카드 안에서 과하게 번진다. 누르는 일은 줄 전체가 받는다.
 */
@Composable
private fun NmSwitch(checked: Boolean) {
    val offset by animateDpAsState(targetValue = if (checked) 20.dp else 2.dp, label = "knob")
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .background(
                color = if (checked) NmColor.Primary.C500 else NmColor.Neutral.C300,
                shape = CircleShape
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(24.dp)
                .background(NmColor.Neutral.C0, CircleShape)
        )
    }
}

/** 앞뒤 각인·구분선·마크. 줄 전체를 눌러 각인 입력판(⑧-e)을 펼친다. */
@Composable
private fun ImprintRow(faces: FaceInputs, open: Boolean, onClick: () -> Unit) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ImprintValues(faces)
        }
        Icon(
            painter = painterResource(R.drawable.nm_ic_chevron_down),
            contentDescription = null,
            tint = if (open) NmColor.Primary.C500 else colors.textTertiary,
            modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f)
        )
    }
}

@Composable
private fun CardDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NmTheme.semanticColors.border))
}

/**
 * 칩에 적을 색 이름.
 *
 * 다색이면 이름을 다 늘어놓는 대신 `파랑 외 1` 로 줄인다. 그래도 칩 셋이 한 줄을 넘길 수 있는데,
 * 그때는 [AttributeRow] 의 가로 스크롤로 넘긴다 — 이름을 지우는 쪽이 더 큰 손해다.
 */
private fun List<PillColor>.chipLabel(transparent: Boolean): String = when {
    isEmpty() -> if (transparent) "투명" else UNSET
    size == 1 -> first().label
    else -> "${first().label} 외 ${size - 1}"
}

/** 아직 고르지 않았다. '없음'이 아니라 '모른다'라서 이 말을 쓴다. */
private const val UNSET = "미인식"

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val CardTitle = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
private val CardSubtitle = NmTypography.caption
private val RowLabel = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
private val ChipLabel = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
