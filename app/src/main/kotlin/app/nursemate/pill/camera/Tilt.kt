package app.nursemate.pill.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** 화면을 위로 두고 바닥과 나란히 들었을 때를 0 으로 한 기울기(도). 좌우 [x] · 앞뒤 [y]. */
internal data class Tilt(val x: Float, val y: Float)

/**
 * 중력 센서로 기울기를 읽는다. 화면이 아래를 보면(뒤집으면) null.
 *
 * 센서 값은 **기기 축**이다. 화면이 가로로 돌면(태블릿 등) 화면의 좌우 · 위아래와 어긋나므로
 * 화면 회전만큼 축을 돌려 화면 기준으로 바꾼다(`SensorManager.remapCoordinateSystem` 표와 같다).
 *
 * 0.5° 단위로 묶어 바뀔 때만 올린다 — 센서는 초당 수십 번 오는데 그대로 올리면 화면이 그만큼 다시 그려져
 * 발열로 돌아온다(초점 거리에서 이미 겪었다).
 */
@Composable
private fun rememberTilt(): Tilt? {
    val context = LocalContext.current
    val view = LocalView.current
    var tilt by remember { mutableStateOf<Tilt?>(null) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (gx, gy) = toScreen(e.values[0], e.values[1], view.display?.rotation ?: Surface.ROTATION_0)
                val gz = e.values[2]
                val next = if (gz <= 0f) null else Tilt(deg(gx, gz), deg(gy, gz))
                if (next != tilt) tilt = next
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}

/** 기기 축(x 오른쪽 · y 위)을 화면 축으로. */
private fun toScreen(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
    Surface.ROTATION_90 -> y to -x
    Surface.ROTATION_180 -> -x to -y
    Surface.ROTATION_270 -> -y to x
    else -> x to y
}

private fun deg(a: Float, gz: Float): Float = (Math.toDegrees(atan2(a, gz).toDouble()) * 2).roundToInt() / 2f

/**
 * 수평계 — 기본 카메라 「촬영 가이드 › 수직/수평계」. 폰을 바닥과 거의 나란히(25° 안) 들면 나타난다.
 *
 * 가운데 + 동그라미는 제자리, 흰 동그라미가 기울기만큼 비켜 있다. 둘이 겹치면(1.5° 안) 노래지고 한 번 떤다.
 * **알약은 위에서 내려다 찍어야** 모양 · 크기가 덜 일그러진다 — 기본 카메라보다 이 화면에서 더 쓸모 있다.
 */
@Composable
internal fun LevelGuide() {
    // 기울기는 여기서만 읽는다 — 바깥에서 읽으면 0.5° 바뀔 때마다 촬영 화면 전체가 다시 구성된다(발열).
    val tilt = rememberTilt()
    val haptic = LocalHapticFeedback.current
    // 바닥과 거의 나란히 들었을 때만 기울기를 쓴다.
    val near = tilt?.takeIf { abs(it.x) < SHOW_DEG && abs(it.y) < SHOW_DEG }
    val level = near != null && abs(near.x) < LEVEL_DEG && abs(near.y) < LEVEL_DEG
    LaunchedEffect(level) { if (level) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    Canvas(Modifier.fillMaxSize()) {
        // 기본 카메라처럼 가운데 노란 + 동그라미는 늘 있다(반지름 34).
        val c = center
        val yellow = NmColor.Warning.C300
        val r = 34.dp.toPx()
        val arm = 12.dp.toPx()
        drawCircle(yellow, radius = r, center = c, style = Stroke(1.5.dp.toPx()))
        drawLine(yellow, c - Offset(arm, 0f), c + Offset(arm, 0f), 1.5.dp.toPx())
        drawLine(yellow, c - Offset(0f, arm), c + Offset(0f, arm), 1.5.dp.toPx())
        // 바닥과 거의 나란히 들었을 때만 — 흰 동그라미가 기울기만큼 비켜 있다가 수평이면 겹친다.
        if (near != null && !level) {
            val k = 3.dp.toPx()
            drawCircle(
                Color.White.copy(alpha = 0.9f),
                radius = r,
                center = c + Offset(near.x * k, -near.y * k),
                style = Stroke(1.4.dp.toPx())
            )
        }
    }
}

private const val LEVEL_DEG = 1.5f
private const val SHOW_DEG = 25f
