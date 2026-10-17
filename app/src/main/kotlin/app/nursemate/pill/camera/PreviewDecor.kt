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
        drawRect(Color.Black, Offset.Zero, Size(size.width, top))
        drawRect(Color.Black, Offset(0f, bottom), Size(size.width, size.height - bottom))
        drawRect(Color.Black, Offset(0f, top), Size(left, side))
        drawRect(Color.Black, Offset(right, top), Size(size.width - right, side))
    }
}

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
