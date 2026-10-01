package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

/**
 * 모델이 준 **표시값 hex** 를 그대로 칠한다 (NM-516).
 *
 * v1 은 색을 열거형이 아니라 sRGB hex 로 준다. 흰색 계열은 배경에 묻히므로 [Swatch] 와 같은
 * 규칙으로 테두리를 두른다 — 밝기로 판단한다(열거형이 아니라 어느 값이든 올 수 있다).
 *
 * 읽을 수 없는 값이 오면 **그리지 않는다.** 엉뚱한 색을 칠하느니 비우는 편이 낫다.
 */
@Composable
internal fun HexSwatch(hex: String) {
    val colors = NmTheme.semanticColors
    val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() ?: return
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(parsed, CircleShape)
            .let { if (parsed.luminance() > OUTLINE_LUMINANCE) it.border(1.dp, colors.border, CircleShape) else it }
    )
}

/** 이보다 밝으면 흰 배경에 묻혀 테두리를 두른다. [Swatch] 의 `needsOutline` 과 같은 뜻이다. */
private const val OUTLINE_LUMINANCE = 0.75f

/** 모양 아이콘 — 정본이 도형 자체로 알려 준다. 이름만 있으면 훑을 때 눈에 안 들어온다. */
@Composable
internal fun ShapeIcon(shape: PillShape, tint: Color = NmTheme.semanticColors.textTertiary) {
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
internal fun FormulationIcon(formulation: PillFormulation, tint: Color = NmTheme.semanticColors.textTertiary) {
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

/** 고른 색을 점으로 늘어놓는다. 하나도 없으면 칩이 텅 비지 않게 아무것도 그리지 않는다. */
@Composable
internal fun ColorDots(colors: List<PillColor>, placeholder: Boolean = false) {
    val border = NmTheme.semanticColors.border
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        // 아직 아무 색도 안 고른 수동 추가 카드는 빈 점 하나로 자리를 잡는다(정본 ⑧-h).
        if (colors.isEmpty() && placeholder) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(NmColor.Neutral.C200, CircleShape)
                    .border(1.dp, border, CircleShape)
            )
        }
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
