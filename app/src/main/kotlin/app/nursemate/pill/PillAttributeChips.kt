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
import app.nursemate.core.model.DividingLine
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
        PillFaceSummary(faces = faces, manual = manual)
    }
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
        HexPie(hexes = hexes)
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
private val ChipValue = NmTypography.caption.copy(fontSize = 12.sp)
