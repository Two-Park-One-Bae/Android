package app.nursemate.pill

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme

/**
 * 속성 메뉴 칸의 색 — **확실한 값인가**가 정한다(V2.1.0 — NM-588).
 *
 * 사용자가 정한 값 · 모델이 확신한 값은 파랑, 추정값 · 조건 없음은 회색이다. 필터인지 점수인지가 아니라
 * 값이 확실한지를 나타내므로, 계약에서 필터 · 점수가 바뀌어도 화면 규칙은 그대로다(spec §칩 테두리).
 * 출시된 앱은 호박색이었다.
 */
@Composable
internal fun conditionTone(certain: Boolean): ConditionTone {
    val colors = NmTheme.semanticColors
    return if (certain) {
        ConditionTone(
            fill = NmColor.Primary.C50,
            stroke = NmColor.Primary.C300,
            label = NmColor.Primary.C900,
            chevron = NmColor.Primary.C500,
            weight = FontWeight.Bold
        )
    } else {
        ConditionTone(
            fill = colors.surface,
            stroke = NmColor.Neutral.C300,
            label = colors.textPrimary,
            chevron = colors.textTertiary,
            weight = FontWeight.SemiBold
        )
    }
}

internal data class ConditionTone(
    val fill: Color,
    val stroke: Color,
    val label: Color,
    val chevron: Color,
    val weight: FontWeight
)
