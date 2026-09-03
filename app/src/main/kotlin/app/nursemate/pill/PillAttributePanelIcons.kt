package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nursemate.R
import app.nursemate.core.model.PillFormulation
import app.nursemate.core.model.PillShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 펼침 패널(⑧-c·⑧-d)의 큰 아이콘.
 *
 * 칩 안의 작은 그림(ShapeIcon · FormulationIcon)과 나눠 둔다. 크기만 다른 게 아니라
 * 정본이 **도형 자체를 다르게** 그린다 — 칩에서는 자리만 잡는 사각형이던 삼각형·오각형이
 * 여기서는 진짜 다각형이다. 사용자가 모양을 고르는 곳이라 이름 말고 그림으로 알아봐야 한다.
 *
 * 선택되면 도형이 `primary-500`, 안쪽 홈이 `primary-100` 이 된다.
 */

/** 정다각형 — 꼭짓점 하나가 위를 향한다(정본 polygon 과 같은 방향). */
private class PolygonShape(private val sides: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2
        val cy = size.height / 2
        repeat(sides) { index ->
            val angle = -PI / 2 + 2 * PI * index / sides
            val x = cx + cx * cos(angle).toFloat()
            val y = cy + cy * sin(angle).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * 모양 아이콘 32×28.
 *
 * ⚠️ 정본은 다각형 모서리를 2~3 만큼 둥글린다. 20dp 짜리 그림에서 눈에 띄지 않는 차이라
 * 각진 채로 둔다 — 둥근 다각형 path 를 손으로 짜는 값어치가 없다.
 */
@Composable
internal fun PanelShapeIcon(shape: PillShape, tint: Color) {
    Box(modifier = Modifier.size(width = 32.dp, height = 28.dp), contentAlignment = Alignment.Center) {
        when (shape) {
            PillShape.ROUND -> Box(Modifier.size(19.dp).background(tint, CircleShape))

            PillShape.OVAL -> Box(Modifier.size(width = 23.dp, height = 14.dp).background(tint, CircleShape))

            PillShape.OBLONG ->
                Box(Modifier.size(width = 24.dp, height = 12.dp).background(tint, RoundedCornerShape(6.dp)))

            PillShape.SEMICIRCLE -> Box(
                Modifier
                    .size(width = 23.dp, height = 13.dp)
                    .background(tint, RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp))
            )

            PillShape.TRIANGLE ->
                Box(Modifier.size(width = 26.dp, height = 23.dp).background(tint, PolygonShape(3)))

            PillShape.SQUARE -> Box(Modifier.size(17.dp).background(tint, RoundedCornerShape(4.dp)))

            PillShape.DIAMOND -> Box(Modifier.size(24.dp).background(tint, PolygonShape(4)))

            PillShape.PENTAGON -> Box(Modifier.size(21.dp).background(tint, PolygonShape(5)))

            PillShape.HEXAGON -> Box(Modifier.size(20.dp).background(tint, PolygonShape(6)))

            PillShape.OCTAGON -> Box(Modifier.size(19.dp).background(tint, PolygonShape(8)))

            else -> Icon(
                painter = painterResource(R.drawable.nm_ic_shapes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 제형 아이콘 28×24. 안쪽 홈([groove])은 도형을 파낸 것처럼 보이게 배경색으로 덮는다. */
@Composable
internal fun PanelFormulationIcon(formulation: PillFormulation, tint: Color, groove: Color) {
    Box(modifier = Modifier.size(width = 28.dp, height = 24.dp), contentAlignment = Alignment.Center) {
        when (formulation) {
            PillFormulation.HARD_CAPSULE -> {
                Box(Modifier.size(width = 26.dp, height = 13.dp).background(tint, CircleShape))
                Box(Modifier.size(width = 2.dp, height = 13.dp).background(groove))
            }

            PillFormulation.SOFT_CAPSULE -> {
                Box(Modifier.size(width = 26.dp, height = 15.dp).background(tint, CircleShape))
                // 광택 한 점. 이게 없으면 경질캡슐과 실루엣이 같아 구분이 안 된다.
                Box(
                    Modifier
                        .size(width = 7.dp, height = 4.dp)
                        .offset(x = (-8).dp, y = (-3).dp)
                        .background(Color.White, CircleShape)
                )
            }

            PillFormulation.TABLET -> {
                Box(Modifier.size(22.dp).background(tint, CircleShape))
                Box(Modifier.size(width = 14.dp, height = 2.dp).background(groove, RoundedCornerShape(1.dp)))
            }

            else -> Icon(
                painter = painterResource(R.drawable.nm_ic_ellipsis),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
