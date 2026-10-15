package app.nursemate.core.designsystem.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor

/**
 * 타이머 진행 원 — 바탕 `neutral-100` 위에 [fraction] 만큼 12시에서 시계 방향으로 채운다.
 *
 * 정본(홈 「Ring」)은 원 두 개에 `innerRadius 0.82` 를 걸어 그린다 — 두께가 반지름의 18% 다.
 * 정본의 음수 sweep 은 Pencil 각도(반시계가 +) 기준이라 Compose 에서는 양수다.
 * [fraction] 은 **남은 비율**이다(`CareTimer.ringFractionAt`).
 */
@Composable
fun NmV2ProgressRing(fraction: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension / 2f * (1f - INNER_RADIUS)
        val inset = Offset(stroke / 2f, stroke / 2f)
        val arc = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(NmColor.Neutral.C100, 0f, FULL_TURN, false, inset, arc, style = Stroke(stroke))
        drawArc(color, TOP, FULL_TURN * fraction.coerceIn(0f, 1f), false, inset, arc, style = Stroke(stroke))
    }
}

private const val INNER_RADIUS = 0.82f
private const val TOP = -90f
private const val FULL_TURN = 360f
