package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillFace
import app.nursemate.core.model.PillFormulation
import app.nursemate.core.model.PillShape

/**
 * 인식 결과 카드의 속성 블록 — 디자인 `⑤ 인식 결과 / Row / Attrs`.
 *
 * 위 줄에 색·모양·제형, 아래에 앞뒤 각인계열.
 *
 * ## 세 가지 상태를 구분해서 보여준다
 * - **값 있음** — 그대로
 * - **미인식**(서버가 그 속성만 못 뽑음, `null`) — '미인식'. 정본에는 이 표기가 없지만,
 *   빈칸으로 두면 사용자가 '없음'으로 읽는다. 없는 것과 모르는 것은 다르다
 * - **없음**(각인·구분선·마크가 실제로 없음) — '없음', 흐린 색
 *
 * 각인계열은 MVP 에서 서버가 뽑지 않아 **항상 미인식**이다(수동 입력 — spec §개별 추출 실패).
 */
@Composable
fun PillAttributeChips(attribute: PillAttribute?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorGroup(colors = attribute?.colors, transparent = attribute?.isTransparent == true)
            ShapeGroup(shape = attribute?.shape)
            FormulationGroup(formulation = attribute?.formulation)
        }
        FaceRow(face = "앞", value = attribute?.front)
        FaceRow(face = "뒤", value = attribute?.back)
    }
}

@Composable
private fun ColorGroup(colors: List<PillColor>?, transparent: Boolean) {
    AttributeGroup(label = "색상") {
        if (colors.isNullOrEmpty() && !transparent) {
            UnrecognizedText()
            return@AttributeGroup
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            colors?.forEach { Swatch(it) }
        }
        // 투명은 색이 아니라 별도 축이라 점이 아니라 태그로 붙인다(spec §PillColor).
        if (transparent) TransparentTag()
    }
}

@Composable
private fun ShapeGroup(shape: PillShape?) = AttributeGroup(label = "모양") {
    if (shape == null) {
        UnrecognizedText()
        return@AttributeGroup
    }
    ShapeIcon(shape)
    ChipText(shape.label)
}

@Composable
private fun FormulationGroup(formulation: PillFormulation?) = AttributeGroup(label = "제형") {
    if (formulation == null) {
        UnrecognizedText()
        return@AttributeGroup
    }
    FormulationIcon(formulation)
    ChipText(formulation.label)
}

/** 라벨 + 회색 칩 한 쌍. 정본의 `색상 G` · `모양 G` · `제형 G` 가 같은 모양이다. */
@Composable
private fun AttributeGroup(label: String, content: @Composable () -> Unit) {
    val colors = NmTheme.semanticColors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = FieldLabel, color = colors.textTertiary)
        Row(
            modifier = Modifier
                .background(NmColor.Neutral.C100, RoundedCornerShape(NmChipRadius))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = { content() }
        )
    }
}

/** 한 면의 각인·구분선·마크. 셋 다 같은 폭을 나눠 가져 앞뒤 줄이 세로로 맞는다. */
@Composable
private fun FaceRow(face: String, value: PillFace?) {
    val colors = NmTheme.semanticColors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = face, style = FaceLabel, color = colors.textTertiary, modifier = Modifier.width(16.dp))
        FaceField(label = "각인", value = value?.imprint, missing = value != null, size = 13.sp)
        FaceField(label = "구분선", value = value?.dividingLine?.label, missing = value != null, size = 14.sp)
        FaceField(
            label = "마크",
            value = if (value == null) {
                null
            } else if (value.hasMark) {
                "있음"
            } else {
                null
            },
            missing = value != null,
            size = 12.sp
        )
    }
}

/**
 * @param missing 면 정보 자체는 받았고 이 항목만 비었는가. true 면 '없음', false 면 '미인식'.
 *                각인계열은 MVP 에서 서버가 아예 안 보내므로 지금은 늘 '미인식'이다.
 */
@Composable
private fun FaceField(label: String, value: String?, missing: Boolean, size: androidx.compose.ui.unit.TextUnit) {
    val colors = NmTheme.semanticColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = FieldLabel, color = colors.textTertiary)
        Box(
            modifier = Modifier
                .background(NmColor.Neutral.C100, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 1.dp)
        ) {
            Text(
                text = value ?: if (missing) "없음" else "미인식",
                style = FieldValue.copy(fontSize = size),
                color = if (value != null) colors.textPrimary else colors.textTertiary
            )
        }
    }
}

@Composable
private fun ChipText(text: String) {
    Text(text = text, style = ChipValue, color = NmTheme.semanticColors.textSecondary)
}

@Composable
private fun UnrecognizedText() {
    Text(text = "미인식", style = ChipValue, color = NmTheme.semanticColors.textTertiary)
}

private val NmChipRadius = 8.dp

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val FieldLabel = NmTypography.caption.copy(fontSize = 11.sp)
private val FaceLabel = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
private val FieldValue = NmTypography.caption.copy(fontWeight = FontWeight.Bold)
private val ChipValue = NmTypography.caption.copy(fontSize = 12.sp)
