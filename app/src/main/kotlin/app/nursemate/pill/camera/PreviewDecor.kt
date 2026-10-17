package app.nursemate.pill.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 정사각 바깥을 검게 덮는다 — SurfaceView 가 넘쳐 그려져도 안 보이게(`PillCameraScreen` KDoc 「미리보기는 SurfaceView」). */
@Composable
internal fun OutsideMask(layout: CameraLayout) {
    Canvas(Modifier.fillMaxSize()) {
        val top = layout.previewTop.toPx()
        val left = layout.previewLeft.toPx()
        val side = layout.previewSide.toPx()
        val bottom = top + side
        val right = left + side
        // 이 Canvas 는 가로 인셋(옆 내비 바 · 카메라 구멍) 안쪽에 놓인다. SurfaceView 는 그 바깥까지 넘칠 수 있어
        // 막을 Canvas 경계 너머로 넉넉히 그린다(Canvas 는 자기 경계로 자르지 않는다).
        val far = OVERSCAN.toPx()
        drawRect(Color.Black, Offset(-far, -far), Size(size.width + 2 * far, top + far))
        drawRect(Color.Black, Offset(-far, bottom), Size(size.width + 2 * far, size.height - bottom + far))
        drawRect(Color.Black, Offset(-far, top), Size(left + far, side))
        drawRect(Color.Black, Offset(right, top), Size(size.width - right + far, side))
    }
}

private val OVERSCAN = 200.dp

/** 격자 3×3 — 기본 카메라 「격자선」. */
@Composable
internal fun Grid() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.55f)
        val w = 0.8.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(c, Offset(x, 0f), Offset(x, size.height), w)
            drawLine(c, Offset(0f, y), Offset(size.width, y), w)
        }
    }
}
