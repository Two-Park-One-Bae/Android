package app.nursemate.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 남은 시간을 두르는 진행 링 — W1 카드와 W2 조작이 같은 것을 쓴다.
 *
 * ## 왜 직접 그리나
 * ⚠️ **[androidx.wear.compose.material3.CircularProgressIndicator] 를 쓰면 링이 멈춘다.**
 * `progress = { … }` 로 매 틱 새 값을 넘겨도 **처음 그려진 값에 고정**된다. 실기기에서 잰 값:
 *
 * | 화면 | 남은 시간 | 링 |
 * |---|---|---|
 * | W1 카드 | 00:55 → 00:10 (1분) | 100% 고정 |
 * | W2 조작 | 00:55 → 00:14 (1분) | 97.4% 고정 |
 * | W2 조작 | [+1분] 로 50% 가 됨 | 23.6% 고정 |
 *
 * 줄어들 때도 늘어날 때도 안 따라간다. 숫자는 같은 `now` 로 제대로 흐르므로 값이 도달하지
 * 못하는 게 아니라 그 컴포넌트가 목표값을 한 번만 잡는다. 그것 말고도 기본 두께가 큰 링을
 * 전제해 작은 링에서 호가 점으로 뭉개지는 문제로 이미 값을 전부 덮어쓰고 있었다 — 얻는 것이
 * 없어 걷어낸다.
 *
 * @param fraction 0~1. 남은 시간 ÷ 전체 시간
 * @param gap 진행 호와 트랙 사이 빈 틈. 끝이 맞닿아 어디가 끝인지 안 보이는 걸 막는다
 */
@Composable
internal fun TimerRing(
    fraction: Float,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = 2.dp
) {
    val safe = fraction.coerceIn(0f, 1f)
    Canvas(modifier) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2
        val topLeft = Offset(inset, inset)
        val size = Size(this.size.width - stroke.width, this.size.height - stroke.width)
        // 12시에서 시작해 시계 방향. 각도는 3시가 0도라 -90도에서 연다.
        val start = -90f
        val sweep = safe * FULL_TURN
        // 지름이 커질수록 같은 `gap` 이 차지하는 각도는 작아진다 — px 를 각도로 바꿔 준다.
        val gapDegrees = if (size.width > 0f) {
            (gap.toPx() / (size.width / 2)).toDegrees()
        } else {
            0f
        }

        // 트랙을 먼저 깐다. 진행 호가 차지한 만큼과 양쪽 틈을 뺀 나머지만 그린다 —
        // 한 바퀴를 통째로 깔면 둥근 끝이 트랙 위에 얹혀 틈이 사라진다.
        val trackSweep = FULL_TURN - sweep - gapDegrees * 2
        if (trackSweep > 0f) {
            drawArc(
                color = trackColor,
                startAngle = start + sweep + gapDegrees,
                sweepAngle = trackSweep,
                useCenter = false,
                topLeft = topLeft,
                size = size,
                style = stroke
            )
        }
        if (sweep > 0f) {
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = size,
                style = stroke
            )
        }
    }
}

private fun Float.toDegrees(): Float = (this * 180f / Math.PI).toFloat()

private const val FULL_TURN = 360f
