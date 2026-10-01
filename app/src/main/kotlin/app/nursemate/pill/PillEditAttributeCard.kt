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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillConditions

/**
 * 수정 화면 맨 위의 속성 카드 — 디자인 `⑧-a` 및 펼침 상태 `⑧-b·c·d`.
 *
 * ## ⑤ 인식 결과의 칩과 다른 물건이다
 * 겉모습이 비슷해 [PillAttributeChips] 를 그대로 쓰고 싶어지지만, 여기 칩은 **누르는 것**이다.
 * 그래서 정본이 라벨을 '속성' 하나로 합치고(⑤ 는 색상·모양·제형 셋), 글자를 13 으로 키우고,
 * 꺾쇠를 달았다. 열린 칩은 `primary-50` 바탕에 `primary-500` 테두리로 어디를 고치는 중인지 알린다.
 *
 * ## 여기서 고치는 것은 **사용자 조건**이다 (NM-516)
 * 모델이 추정한 값([PillAttribute])은 **고치지 않는다** — 읽기 전용이고, 되돌리기의 기준이자
 * 서버 정렬의 근거(`attributeToken`)다. 사용자가 고른 값만 [PillConditions] 에 쌓이고
 * 그것만 후보를 자른다.
 *
 * 투명 축은 V1 에서 사라졌다(NM-487) — 색상판의 투명 줄도 함께 없앴다.
 */
@Composable
fun PillEditAttributeCard(
    number: Int,
    manual: Boolean,
    crop: Bitmap?,
    attribute: PillAttribute,
    conditions: PillConditions,
    faces: FaceInputs,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    open: AttributePanel?,
    onToggle: (AttributePanel) -> Unit,
    onColorToggle: (PillColor) -> Unit,
    onChange: (PillConditions) -> Unit,
    faceCard: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NmTheme.semanticColors.surface, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillCrop(manual = manual, crop = crop)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = if (manual) "새 알약" else "알약 $number",
                    style = CardTitle,
                    color = NmTheme.semanticColors.textPrimary
                )
                Box {
                    AttributeRow(
                        attribute = attribute,
                        conditions = conditions,
                        manual = manual,
                        expanded = expanded,
                        open = open,
                        onToggle = onToggle
                    )
                    // 메뉴는 칩 줄을 앵커로 삼아 화면 폭으로 뜬다(정본 ③·④·⑤).
                    if (expanded && open != null) {
                        AttributeMenuPopup(onDismiss = { onToggle(open) }) {
                            OpenMenu(
                                panel = open,
                                attribute = attribute,
                                conditions = conditions,
                                onColorToggle = onColorToggle,
                                onChange = onChange
                            )
                        }
                    }
                }
            }
            ExpandButton(expanded = expanded, onClick = { onExpandedChange(!expanded) })
        }

        // 접히면 읽기만, 펼치면 고친다. 둘은 **같은 말·같은 색**을 쓴다.
        if (expanded) faceCard() else PillFaceSummary(faces = faces, manual = manual)
    }
}

/** 지금 열어 둔 메뉴. 고른 뒤에도 닫지 않는다 — 색은 여러 개를 이어서 고른다. */
@Composable
private fun OpenMenu(
    panel: AttributePanel,
    attribute: PillAttribute,
    conditions: PillConditions,
    onColorToggle: (PillColor) -> Unit,
    onChange: (PillConditions) -> Unit
) {
    when (panel) {
        AttributePanel.Color -> ColorMenu(
            selected = conditions.colors,
            modelHexes = attribute.colorHexes,
            onToggle = onColorToggle,
            onClear = { onChange(conditions.copy(colors = emptyList())) }
        )

        AttributePanel.Shape -> ShapeMenu(
            selected = conditions.shape,
            modelShape = attribute.shape,
            // 같은 값을 다시 눌러도 조건에서 빼지 않는다 — 푸는 길은 메뉴 맨 위의 「전체」다.
            onSelect = { onChange(conditions.copy(shape = it)) },
            onClear = { onChange(conditions.copy(shape = null)) }
        )

        AttributePanel.Formulation -> FormulationMenu(
            selected = conditions.formulation,
            modelFormulation = attribute.formulation,
            onSelect = { onChange(conditions.copy(formulation = it)) },
            onClear = { onChange(conditions.copy(formulation = null)) }
        )

        // 각인은 칩이 아니라 면 카드 안에 있다 — 여기서 열 메뉴가 없다.
        AttributePanel.Imprint -> Unit
    }
}

/**
 * 접힘 ↔ 펼침 단추 — 정본 ② 는 조절 아이콘, ⑪ 은 파란 원에 체크다.
 *
 * 「수정」과 「완료」 둘을 한 자리에서 번갈아 보여 준다. 펼쳤을 때만 파랗게 두는 것은
 * **지금 고치는 중**이라는 표시이자, 끝내는 길이 거기 있다는 안내다.
 */
@Composable
private fun ExpandButton(expanded: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (expanded) NmColor.Primary.C500 else NmColor.Neutral.C100)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                if (expanded) R.drawable.nm_ic_check else R.drawable.nm_ic_sliders_horizontal
            ),
            contentDescription = if (expanded) "수정 마치기" else "수정하기",
            tint = if (expanded) NmColor.Neutral.C0 else NmTheme.semanticColors.textSecondary,
            modifier = Modifier.size(17.dp)
        )
    }
}

/** 낱알 크롭 44 — 수동 추가 알약은 사진에 대응 영역이 없어 알약 아이콘을 대신 둔다. */
@Composable
private fun PillCrop(manual: Boolean, crop: Bitmap?) {
    val thumbnail = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
    if (crop != null) {
        Image(
            bitmap = crop.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = thumbnail.background(NmColor.Neutral.C100)
        )
    } else {
        Box(
            modifier = thumbnail.background(if (manual) NmColor.Primary.C50 else NmColor.Neutral.C100),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_pill),
                contentDescription = null,
                tint = NmColor.Primary.C500,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 색·모양·제형 칩 한 줄 — 정본 ② · ⑪.
 *
 * ## 칩 안에는 **그림**만 들어간다
 * 이름은 칩 **왼쪽 바깥**에 붙는다("색상" · "모양" · "제형"). 칩 안에 이름까지 넣으면 셋이
 * 한 줄을 넘겨 가로로 밀린다 — 정본이 그림 하나와 꺾쇠만 남긴 이유다.
 *
 * ## 모델값도 보여 준다. 다만 **회색**이다
 * 사용자가 고르기 전에도 모델이 읽은 색·모양·제형을 그려 준다 — 「무엇으로 보고 찾고 있나」를
 * 알아야 고칠지 말지 정한다. 대신 회색이라 **조건이 아님**을 말한다. 사용자가 고르면
 * 호박색으로 바뀐다([conditionTone]).
 *
 * ⚠️ 모델이 추정한 모양·제형은 **요청에 실리지 않는다**(NM-516). 화면에만 보인다.
 */
@Composable
private fun AttributeRow(
    attribute: PillAttribute,
    conditions: PillConditions,
    manual: Boolean,
    expanded: Boolean,
    open: AttributePanel?,
    onToggle: (AttributePanel) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AttributeChip(
            name = "색상",
            certain = conditions.colors.isNotEmpty(),
            expanded = expanded,
            open = open == AttributePanel.Color,
            empty = conditions.colors.isEmpty() && attribute.colorHexes.isNullOrEmpty(),
            manual = manual,
            onClick = { onToggle(AttributePanel.Color) }
        ) {
            if (conditions.colors.isNotEmpty()) {
                ColorDots(colors = conditions.colors)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    attribute.colorHexes.orEmpty().forEach { HexSwatch(it) }
                }
            }
        }

        val shape = conditions.shape ?: attribute.shape
        AttributeChip(
            name = "모양",
            certain = conditions.shape != null,
            expanded = expanded,
            open = open == AttributePanel.Shape,
            empty = shape == null,
            manual = manual,
            onClick = { onToggle(AttributePanel.Shape) }
        ) { tint -> shape?.let { ShapeIcon(shape = it, tint = tint) } }

        val formulation = conditions.formulation ?: attribute.formulation
        AttributeChip(
            name = "제형",
            certain = conditions.formulation != null,
            expanded = expanded,
            open = open == AttributePanel.Formulation,
            empty = formulation == null,
            manual = manual,
            onClick = { onToggle(AttributePanel.Formulation) }
        ) { tint -> formulation?.let { FormulationIcon(formulation = it, tint = tint) } }
    }
}

/**
 * @param certain 사용자가 직접 고른 값인가. 아니면 추정값이라 회색이다
 * @param expanded 펼친 상태인가. 접히면 **누를 수 없고 꺾쇠도 없다** — 읽기만 한다
 * @param empty 그릴 그림이 아예 없는가 — 수동 추가·추출 실패. 그때만 이름을 칩 안에 적는다
 */
@Composable
private fun AttributeChip(
    name: String,
    certain: Boolean,
    expanded: Boolean,
    open: Boolean,
    empty: Boolean,
    manual: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val colors = NmTheme.semanticColors
    val tone = conditionTone(certain)
    val skin = chipSkin(tone = tone, certain = certain, expanded = expanded, open = open)
    val shape = RoundedCornerShape(13.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = ChipName, color = colors.textTertiary)
        Row(
            modifier = Modifier
                .height(26.dp)
                .clip(shape)
                .background(skin.fill)
                // 접힘 칩은 조건이 없으면 테두리도 없다(정본 ②) — 누를 수 없는 것이 함께 드러난다.
                .chipBorder(bordered = skin.bordered, color = skin.stroke, shape = shape)
                .let { if (expanded) it.clickable(onClick = onClick) else it }
                .padding(start = 7.dp, end = if (expanded) 5.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (empty) {
                // 그릴 것이 없을 때만 글자로 대신한다. 읽어 본 적 없는 수동 추가는 '-' 다.
                Text(text = if (manual) "-" else "전체", style = ChipLabel, color = colors.textSecondary)
            } else {
                icon(if (certain) NmColor.Warning.C700 else colors.textSecondary)
            }
            if (expanded) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_chevron_down),
                    contentDescription = null,
                    tint = skin.stroke.takeIf { open } ?: tone.chevron,
                    // 펼쳐지면 같은 꺾쇠를 뒤집는다. 정본의 chevron-up 과 같은 그림이 된다.
                    modifier = Modifier.size(11.dp).rotate(if (open) 180f else 0f)
                )
            }
        }
    }
}

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val CardTitle = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
private val ChipName = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
private val ChipLabel = NmTypography.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)

private fun Modifier.chipBorder(bordered: Boolean, color: Color, shape: Shape): Modifier =
    if (bordered) border(1.dp, color, shape) else this

/**
 * 칩의 바탕·테두리.
 *
 * 접힘 칩은 **테두리 없이 바탕만**이다(정본 ②) — 누를 수 없다는 것이 모양으로 드러난다.
 * 그래서 흰 바탕을 쓸 수 없다. 그림만 허공에 뜬 것처럼 보인다.
 */
private data class ChipSkin(val fill: Color, val stroke: Color, val bordered: Boolean)

private fun chipSkin(tone: ConditionTone, certain: Boolean, expanded: Boolean, open: Boolean): ChipSkin = when {
    open -> ChipSkin(NmColor.Primary.C50, NmColor.Primary.C500, bordered = true)
    certain || expanded -> ChipSkin(tone.fill, tone.stroke, bordered = true)
    else -> ChipSkin(NmColor.Neutral.C100, Color.Transparent, bordered = false)
}
