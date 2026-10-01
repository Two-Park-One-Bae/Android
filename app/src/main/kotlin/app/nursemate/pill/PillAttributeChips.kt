package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
fun PillAttributeChips(
    attribute: PillAttribute?,
    faces: FaceInputs,
    manual: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorGroup(hexes = attribute?.colorHexes, manual = manual)
            ShapeGroup(shape = attribute?.shape, manual = manual)
            FormulationGroup(formulation = attribute?.formulation, manual = manual)
        }
        // 정본에서 앞뒤 두 줄은 `표기값` 프레임 하나로 묶여 간격이 5 다(칩 줄과는 6).
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { ImprintValues(faces, manual) }
    }
}

/** 앞뒤 표기값 두 줄. ⑤ 인식 결과 카드와 ⑧ 수정 카드가 같은 모양을 쓴다. */
@Composable
internal fun ImprintValues(faces: FaceInputs, manual: Boolean = false) {
    FaceRow(face = "앞", input = faces.front, manual = manual)
    FaceRow(face = "뒤", input = faces.back, manual = manual)
}

/**
 * 모델이 뽑은 색 — **표시값 hex 를 그대로 칠한다**(NM-516).
 *
 * v0 은 열거형이라 앱이 색을 골라 칠했는데, v1 은 서버가 Lab 을 변환한 sRGB hex 를 준다.
 * 열거형으로 되돌려 매핑하지 않는다 — 계약이 「검색에는 쓰지 않는다」고 못박았고,
 * 되돌리면 모델이 본 색과 화면에 뜨는 색이 갈린다.
 *
 * 순서가 의미를 갖는다(여러 색의 배치 순서다).
 */
@Composable
private fun ColorGroup(hexes: List<String>?, manual: Boolean) {
    AttributeGroup(label = "색상") {
        if (hexes.isNullOrEmpty()) {
            UnrecognizedText(manual)
            return@AttributeGroup
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            hexes.forEach { HexSwatch(it) }
        }
    }
}

@Composable
private fun ShapeGroup(shape: PillShape?, manual: Boolean) = AttributeGroup(label = "모양") {
    if (shape == null) {
        UnrecognizedText(manual)
        return@AttributeGroup
    }
    ShapeIcon(shape)
    ChipText(shape.label)
}

@Composable
private fun FormulationGroup(formulation: PillFormulation?, manual: Boolean) = AttributeGroup(label = "제형") {
    if (formulation == null) {
        UnrecognizedText(manual)
        return@AttributeGroup
    }
    FormulationIcon(formulation)
    ChipText(formulation.chipLabel)
}

/** 라벨 + 회색 칩 한 쌍. 정본의 `색상 G` · `모양 G` · `제형 G` 가 같은 모양이다. */
@Composable
private fun AttributeGroup(label: String, content: @Composable () -> Unit) {
    val colors = NmTheme.semanticColors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        // 정본이 라벨 폭을 24 로 못박는다 — 색상·모양·제형 칩의 시작선이 흔들리지 않게 한다.
        Text(text = label, style = FieldLabel, color = colors.textTertiary, modifier = Modifier.width(24.dp))
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

/**
 * 한 면의 각인·구분선·마크. 셋 다 같은 폭을 나눠 가져 앞뒤 줄이 세로로 맞는다.
 *
 * ## '없음'과 '미인식'은 필터와 같은 뜻이어야 한다
 * ⚠️ [PillFace] 를 받아 그리면 안 된다 — 거기서는 null 이 '없음' 하나뿐이라, 사용자가 각인만
 * 적은 면의 구분선·마크까지 '없음'이라고 단언하게 된다. 정작 후보 조회는 그 둘을 조건에서
 * 빼고 있어 화면과 필터가 어긋난다. 그래서 화면 입력값([FaceInput])을 그대로 읽는다.
 */
@Composable
private fun FaceRow(face: String, input: FaceInput, manual: Boolean) {
    val colors = NmTheme.semanticColors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = face, style = FaceLabel, color = colors.textTertiary, modifier = Modifier.width(16.dp))
        FaceField(
            label = "각인",
            value = input.imprint.ifBlank { null },
            blank = input.blank,
            manual = manual,
            size = 13.sp
        )
        FaceField(
            label = "구분선",
            // NONE 은 사용자가 '없음'을 고른 것이라 값이 있는 셈이다 — 아래 blank 와 결과가 같다.
            value = input.dividingLine?.label,
            blank = input.blank,
            manual = manual,
            size = 14.sp
        )
        FaceField(
            label = "마크",
            value = "있음".takeIf { input.hasMark },
            blank = input.blank,
            manual = manual,
            size = 12.sp
        )
    }
}

/**
 * @param manual 수동 추가 알약인가. 값이 비었을 때 '미인식' 대신 '-' 로 둔다.
 * @param blank 이 면이 통째로 '해당 없음'인가. true 면 값이 비었을 때 '없음', false 면 '미인식'.
 *              둘을 뒤섞으면 후보 조회가 조건으로 걸지도 않은 것을 화면이 단언하게 된다.
 */
@Composable
private fun RowScope.FaceField(
    label: String,
    value: String?,
    blank: Boolean,
    manual: Boolean,
    size: androidx.compose.ui.unit.TextUnit
) {
    val colors = NmTheme.semanticColors
    // 정본에서 각인·구분선·마크 칸이 모두 fill_container 다 — 셋이 폭을 균등하게 나눠 가져야
    // 앞줄과 뒷줄의 칩이 세로로 맞는다. weight 를 빼면 글자 길이대로 밀려 어긋난다.
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = FieldLabel, color = colors.textTertiary)
        Box(
            modifier = Modifier
                .background(NmColor.Neutral.C100, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 1.dp)
        ) {
            Text(
                // 수동 추가는 읽어 본 적이 없어 '미인식'이 성립하지 않는다(정본 ⑧-h 는 '-').
                text = value ?: when {
                    blank -> "없음"
                    manual -> "-"
                    else -> "미인식"
                },
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
private fun UnrecognizedText(manual: Boolean) {
    // 수동 추가는 서버가 읽어 본 적이 없다 — '미인식'은 읽고도 못 읽었을 때만 쓴다.
    Text(text = if (manual) "-" else "미인식", style = ChipValue, color = NmTheme.semanticColors.textTertiary)
}

private val NmChipRadius = 8.dp

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val FieldLabel = NmTypography.caption.copy(fontSize = 11.sp)
private val FaceLabel = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
private val FieldValue = NmTypography.caption.copy(fontWeight = FontWeight.Bold)
private val ChipValue = NmTypography.caption.copy(fontSize = 12.sp)
