package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillFormulation
import app.nursemate.core.model.PillShape

/**
 * 속성 칩 안에 들어가는 작은 그림들 — 색 점 · 모양 · 제형.
 *
 * 칩 조립부([PillAttributeChips])와 나눠 둔다. 한 파일에 두면 "무엇을 보여주는가"와
 * "어떻게 생겼는가"가 뒤섞여 읽기 어렵고, 정본이 도형을 손보면 이쪽만 고치면 된다.
 */

@Composable
internal fun Swatch(color: PillColor) {
    val colors = NmTheme.semanticColors
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color.swatch, CircleShape)
            .let { if (color.needsOutline) it.border(1.dp, colors.border, CircleShape) else it }
    )
}

@Composable
internal fun TransparentTag() {
    val colors = NmTheme.semanticColors
    Box(
        modifier = Modifier
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 2.dp)
    ) {
        Text(text = "투명", style = IconTagLabel, color = colors.textTertiary)
    }
}

/** 모양 아이콘 — 정본이 도형 자체로 알려 준다. 이름만 있으면 훑을 때 눈에 안 들어온다. */
@Composable
internal fun ShapeIcon(shape: PillShape) {
    val tint = NmTheme.semanticColors.textTertiary
    when (shape) {
        PillShape.ROUND -> Box(Modifier.size(13.dp).background(tint, CircleShape))

        PillShape.OVAL -> Box(Modifier.size(width = 16.dp, height = 11.dp).background(tint, CircleShape))

        PillShape.OBLONG -> Box(Modifier.size(width = 18.dp, height = 10.dp).background(tint, RoundedCornerShape(5.dp)))

        // 나머지 모양은 전용 도형을 그리지 않는다. 사각형 하나로 자리만 잡고 이름으로 읽힌다 —
        // 삼각형·오각형까지 벡터를 만드는 값어치보다 이름이 더 정확하다.
        else -> Box(Modifier.size(13.dp).background(tint, RoundedCornerShape(3.dp)))
    }
}

/** 제형 아이콘 — 정제는 분할선 있는 원, 캡슐은 이음매 있는 알약 모양. */
@Composable
internal fun FormulationIcon(formulation: PillFormulation) {
    val tint = NmTheme.semanticColors.textTertiary
    Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        when (formulation) {
            PillFormulation.HARD_CAPSULE, PillFormulation.SOFT_CAPSULE -> {
                Box(Modifier.size(width = 16.dp, height = 9.dp).background(tint, RoundedCornerShape(4.5.dp)))
                Box(Modifier.size(width = 1.6.dp, height = 9.dp).background(NmColor.Neutral.C100))
            }

            else -> {
                Box(Modifier.size(16.dp).background(tint, CircleShape))
                Box(Modifier.size(width = 10.dp, height = 1.6.dp).background(NmColor.Neutral.C100))
            }
        }
    }
}

private val IconTagLabel = NmTypography.caption.copy(fontSize = 11.sp)
