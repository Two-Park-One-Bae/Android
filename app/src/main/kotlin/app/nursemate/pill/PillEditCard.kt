package app.nursemate.pill

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2Surface
import app.nursemate.core.designsystem.v2.NmV2SurfaceStyle
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillConditions

/**
 * 수정 화면 맨 위의 속성 카드 — 정본 「알약 식별 — 수정 · 후보 선택 (NM-588)」 기준 열 · ①.
 *
 * ## 늘 고칠 수 있다 (V2.1.0)
 * 출시된 앱은 카드를 접어 두고 `수정` 을 눌러 펼쳤다. V2.1.0 은 접힘 · 펼침과 `수정` / `완료` 를 없앴다 —
 * 칩을 누르면 바로 메뉴 · 입력 줄이 열린다(spec §수정 · 후보 선택).
 *
 * 윗줄은 사진 · 이름 · 색 · 모양 · 제형 칩, 그 아래 **앞면 | 뒷면** 두 칸(각인, 그 아래 구분선 | 마크).
 *
 * ## 칩 테두리 — 값이 확실한가
 * 회색 = 추정값 · 조건 없음(손대지 않음 · `모름`, 모델이 추정한 색 · 모양 · 제형). 파랑(`primary-500`) =
 * 확실한 값(모델이 확신한 각인 · 마크 유무 + 사용자가 정한 값). 필터인지 점수인지가 아니라 **값이 확실한지**다.
 *
 * ## 모델값은 고치지 않는다 (NM-516)
 * [attribute] 는 읽기 전용이다 — 되돌리기의 기준이자 서버 정렬의 근거(`attributeToken`)다. 사용자가 고른 값만
 * [PillConditions] · [FaceInputs] 에 쌓이고 그것만 후보를 자른다.
 *
 * @param auto 색 · 모양 · 제형 메뉴에 `자동` 을 둘지 — 보여 줄 모델 출력이 있을 때만(수동 추가 · 추출 실패 아님)
 * @param editingSide 각인을 치는 중인 면 — 그 면의 각인 칩을 강조한다
 */
@Suppress("LongParameterList")
@Composable
internal fun PillEditCard(
    title: String,
    crop: Bitmap?,
    attribute: PillAttribute,
    conditions: PillConditions,
    faces: FaceInputs,
    auto: Boolean,
    editingSide: FaceSide?,
    open: AttributePanel?,
    onToggle: (AttributePanel) -> Unit,
    onColorToggle: (PillColor) -> Unit,
    onChange: (PillConditions) -> Unit,
    onFacesChange: (FaceInputs) -> Unit,
    onEditImprint: (FaceSide) -> Unit,
    modifier: Modifier = Modifier
) {
    NmV2Surface(NmV2SurfaceStyle.Card, modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Crop(crop)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = NmV2Type.tileTitle, color = NmTheme.semanticColors.textPrimary)
                    Box {
                        AppearanceRow(attribute, conditions, open, onToggle)
                        if (open != null && open != AttributePanel.Imprint) {
                            AttributeMenuPopup(onDismiss = { onToggle(open) }) {
                                AppearanceMenu(open, attribute, conditions, auto, onColorToggle, onChange)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FacePanel(
                    title = "앞면",
                    input = faces.front,
                    editing = editingSide == FaceSide.Front,
                    onChange = { onFacesChange(faces.copy(front = it)) },
                    onEditImprint = { onEditImprint(FaceSide.Front) },
                    modifier = Modifier.weight(1f)
                )
                FacePanel(
                    title = "뒷면",
                    input = faces.back,
                    editing = editingSide == FaceSide.Back,
                    onChange = { onFacesChange(faces.copy(back = it)) },
                    onEditImprint = { onEditImprint(FaceSide.Back) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 낱알 사진 44. 수동 추가 알약은 사진에 영역이 없어 알약 그림을 둔다. */
@Composable
private fun Crop(crop: Bitmap?) {
    val modifier = Modifier.size(44.dp).clip(RoundedCornerShape(NmRadius.md))
    if (crop != null) {
        Image(
            crop.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.background(NmColor.Neutral.C100)
        )
    } else {
        Box(modifier.background(NmColor.Primary.C50), contentAlignment = Alignment.Center) {
            Icon(painterResource(NmSymbols.Pill), null, tint = NmColor.Primary.C500, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * 색 · 모양 · 제형 — 라벨은 칩 **바깥 왼쪽**, 칩 안에는 그림만 둔다. 셋이 한 줄에 들어가야 해서다.
 * 사용자가 고르기 전에는 모델이 읽은 값을 회색 테두리 칩에 그린다(조건이 아니다).
 */
@Composable
private fun AppearanceRow(
    attribute: PillAttribute,
    conditions: PillConditions,
    open: AttributePanel?,
    onToggle: (AttributePanel) -> Unit
) {
    val tint = NmTheme.semanticColors.textSecondary
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LabeledChip("색상", conditions.colors.isNotEmpty(), open == AttributePanel.Color, {
            onToggle(AttributePanel.Color)
        }) {
            when {
                conditions.colors.isNotEmpty() -> ColorDots(conditions.colors)
                !attribute.colorHexes.isNullOrEmpty() -> HexPie(attribute.colorHexes, size = 13.dp)
                else -> ChipText("선택", ChipTone.Placeholder)
            }
        }
        val shape = conditions.shape ?: attribute.shape
        LabeledChip("모양", conditions.shape != null, open == AttributePanel.Shape, { onToggle(AttributePanel.Shape) }) {
            if (shape !=
                null
            ) {
                ShapeIcon(
                    shape,
                    tint = if (conditions.shape !=
                        null
                    ) {
                        CertainGlyph
                    } else {
                        tint
                    }
                )
            } else {
                ChipText("선택", ChipTone.Placeholder)
            }
        }
        val formulation = conditions.formulation ?: attribute.formulation
        LabeledChip(
            "제형",
            conditions.formulation != null,
            open == AttributePanel.Formulation,
            { onToggle(AttributePanel.Formulation) }
        ) {
            if (formulation != null) {
                FormulationIcon(formulation, tint = if (conditions.formulation != null) CertainGlyph else tint)
            } else {
                ChipText("선택", ChipTone.Placeholder)
            }
        }
    }
}

@Composable
private fun LabeledChip(
    label: String,
    certain: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        FieldLabel(label)
        EditChip(certain = certain, active = open, onClick = onClick, content = content)
    }
}

/** 지금 열어 둔 외형 메뉴. 고른 뒤에도 닫지 않는다 — 색은 여러 개를 이어서 고른다. */
@Composable
private fun AppearanceMenu(
    panel: AttributePanel,
    attribute: PillAttribute,
    conditions: PillConditions,
    auto: Boolean,
    onColorToggle: (PillColor) -> Unit,
    onChange: (PillConditions) -> Unit
) {
    when (panel) {
        AttributePanel.Color -> ColorMenu(
            selected = conditions.colors,
            modelHexes = attribute.colorHexes,
            auto = auto,
            onToggle = onColorToggle,
            onClear = { onChange(conditions.copy(colors = emptyList())) }
        )

        // `자동` 이 없으면(수동 추가 · 추출 실패) 같은 값을 다시 눌러 푼다.
        AttributePanel.Shape -> ShapeMenu(
            selected = conditions.shape,
            modelShape = attribute.shape,
            auto = auto,
            onSelect = { picked -> onChange(conditions.copy(shape = picked.unlessRepeated(conditions.shape, auto))) },
            onClear = { onChange(conditions.copy(shape = null)) }
        )

        AttributePanel.Formulation -> FormulationMenu(
            selected = conditions.formulation,
            modelFormulation = attribute.formulation,
            auto = auto,
            onSelect = { picked ->
                onChange(conditions.copy(formulation = picked.unlessRepeated(conditions.formulation, auto)))
            },
            onClear = { onChange(conditions.copy(formulation = null)) }
        )

        AttributePanel.Imprint -> Unit
    }
}

/** `자동` 이 없는 메뉴에서 같은 값을 다시 누르면 푼다. `자동` 이 있으면 그대로 둔다 — 푸는 길은 `자동` 이다. */
private fun <T> T.unlessRepeated(current: T?, auto: Boolean): T? = if (!auto && this == current) null else this

/**
 * 한 면 — `앞면` / `뒷면`, 각인 한 줄, 그 아래 구분선 | 마크. 바탕 `neutral-50` · 모서리 12.
 * 앞면 = 처음 찍은 사진의 면이다(spec §수정 · 후보 선택).
 */
@Composable
private fun FacePanel(
    title: String,
    input: FaceInput,
    editing: Boolean,
    onChange: (FaceInput) -> Unit,
    onEditImprint: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf<FaceMenu?>(null) }
    Column(
        modifier
            .clip(RoundedCornerShape(NmRadius.md))
            .background(NmColor.Neutral.C50)
            .padding(start = 8.dp, top = 5.dp, end = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = FaceTitle, color = NmTheme.semanticColors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("각인")
            // 각인 칩은 메뉴 없이 곧바로 입력 줄을 연다. 한 줄이고 넘치면 말줄임 — 전체는 입력 줄에서 본다.
            EditChip(
                certain = input.imprint != null,
                active = editing,
                onClick = onEditImprint,
                modifier = Modifier.weight(1f)
            ) { ChipText(input.imprintText(), input.imprintTone(), Modifier.weight(1f)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldLabel("구분선")
                    EditChip(
                        certain = input.dividingLine != null,
                        active = menu == FaceMenu.Line,
                        onClick = { menu = FaceMenu.Line }
                    ) { ChipText(input.lineText(), input.lineTone()) }
                }
                if (menu == FaceMenu.Line) {
                    AttributeMenuPopup(onDismiss = { menu = null }) {
                        DividingLineMenu(
                            selected = input.dividingLine,
                            unknown = input.lineUnknown,
                            onSelect = { onChange(input.line(it)) },
                            onUnknown = { onChange(input.line(null)) }
                        )
                    }
                }
            }
            Box(Modifier.width(1.dp).height(30.dp).background(NmColor.Neutral.C200))
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FieldLabel("마크")
                    EditChip(
                        certain = input.hasMark != null,
                        active = menu == FaceMenu.Mark,
                        onClick = { menu = FaceMenu.Mark }
                    ) { ChipText(input.markText(), input.markTone()) }
                }
                if (menu == FaceMenu.Mark) {
                    MarkMenu(
                        input = input,
                        onSelect = {
                            onChange(input.mark(it))
                            menu = null
                        },
                        onDismiss = { menu = null }
                    )
                }
            }
        }
    }
}

private enum class FaceMenu { Line, Mark }

/** 사용자가 고른 모양 · 제형 그림 — 확실한 값이라 파랗다. */
private val CertainGlyph = NmColor.Primary.C600

private val FaceTitle = NmV2Type.tag.copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun FieldLabel(text: String) = Text(text, style = NmV2Type.tag, color = NmTheme.semanticColors.textTertiary)
