package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 면 조건의 **읽기 카드** — 정본 ② · ⑫ 의 접힌 면 카드.
 *
 * 펼친 상태([PillFaceCard])와 **같은 말·같은 색**을 쓴다([conditionLabel] · [conditionTone]).
 * 접었다 폈다 할 때 읽는 법이 바뀌면 사용자가 같은 값을 둘로 읽는다. 다른 것은 꺾쇠가 없고
 * 칸이 알약 모양이라는 것뿐이다.
 */
@Composable
internal fun PillFaceSummary(faces: FaceInputs, manual: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FaceSummaryColumn(title = "앞면", input = faces.front, manual = manual)
        FaceSummaryColumn(title = "뒷면", input = faces.back, manual = manual)
    }
}

@Composable
private fun RowScope.FaceSummaryColumn(title: String, input: FaceInput, manual: Boolean) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(NmColor.Neutral.C50)
            .border(1.dp, NmColor.Neutral.C200, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = title, style = FaceTitle, color = colors.textSecondary)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ConditionLabel("각인")
            ConditionValue(
                value = input.imprintText(manual),
                certain = input.imprint != null,
                // 각인은 읽을 글자라 한 급 크다 — 정본 14/700.
                fontSize = 14.sp,
                padding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            SummaryCell(
                label = "구분선",
                value = input.dividingLine.conditionLabel(manual),
                certain = input.dividingLine != null
            )
            Box(modifier = Modifier.width(1.dp).height(22.dp).background(NmColor.Neutral.C200))
            SummaryCell(
                label = "마크",
                value = input.hasMark.markConditionLabel(manual),
                certain = input.hasMark != null
            )
        }
    }
}

@Composable
private fun RowScope.SummaryCell(label: String, value: String, certain: Boolean) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ConditionLabel(label)
        ConditionValue(
            value = value,
            certain = certain,
            fontSize = 12.sp,
            padding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

/** 접힘은 각인 **값**을 그대로 보여 준다 — 펼침 드롭다운의 「입력」과 다른 자리다. */
private fun FaceInput.imprintText(manual: Boolean): String = when {
    imprint == null -> unconditioned(manual)
    imprint.isEmpty() -> "없음"
    else -> imprint
}

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val FaceTitle = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
