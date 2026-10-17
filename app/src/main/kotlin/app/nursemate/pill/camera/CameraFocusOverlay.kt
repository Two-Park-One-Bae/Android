package app.nursemate.pill.camera

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.v2.NmSymbols
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 뷰파인더 위 손짓과 초점 표시 — 갤럭시 기본 카메라(S24 · One UI)를 뜯어 보고 맞췄다(NM-630, PoC 에서 옮김).
 *
 * ## 손짓
 * | 손짓 | 동작 | 기본 카메라 |
 * |---|---|---|
 * | 탭 | 그 자리에 AF · AE. 링이 커졌다 줄며 내려앉는다 | 같다 |
 * | 탭 뒤 **좌우로** 끌기 | 노출 — 링 **아래** 해 막대 | 같다(막대가 링 아래 가로) |
 * | 링 위 자물쇠 탭 · 길게 누르기 | AE/AF 고정 | 자물쇠가 링 위에 붙어 있다 |
 * | 두 손가락 | 줌. 1 · 2 · 3배를 지날 때 진동 | 같다 |
 *
 * ## 링 색 = 초점 상태(매 프레임 `CONTROL_AF_STATE`)
 * 흰색 깜빡임 = 맞추는 중 → 노랑 = 맞음 → 빨강 = 못 맞춤. 기본 카메라는 흰 링 하나로만 보여 주는데,
 * 알약은 맞았는지가 결과를 가르므로 상태를 색으로 더 드러냈다.
 */
@Composable
internal fun CameraFocusOverlay(state: PillCameraState, camera: PillCamera, previewView: PreviewView) {
    val current = rememberUpdatedState(state)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val g = remember(density, haptic) {
        FocusGestures(
            camera = camera,
            previewView = previewView,
            haptic = haptic,
            lockGapPx = with(density) { RING_RADIUS.toPx() },
            lockHitPx = with(density) { LOCK_HIT.toPx() }
        )
    }

    LaunchedEffect(g.tapKey) {
        if (g.tapKey == 0) return@LaunchedEffect
        g.alpha.snapTo(1f)
        g.scale.snapTo(1.45f)
        g.scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(state.af, g.tapKey) {
        if (g.ring != null && state.af == Af.Focused) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            g.scale.animateTo(0.9f, tween(90))
            g.scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
        }
    }
    LaunchedEffect(g.lastTouch, state.locked, state.af, g.dragging) {
        // 고정 · 끄는 중 · 맞추는 중에는 링을 남긴다.
        val keep = state.locked || g.dragging || state.af == Af.Searching
        if (g.ring == null || keep) return@LaunchedEffect
        delay(HIDE_AFTER_MS)
        g.alpha.animateTo(0f, tween(250))
        g.ring = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(g) { awaitEachGesture { with(g) { handle(current) } } }
    ) {
        val p = g.ring
        if (p != null) {
            RingWithLock(p, state, g.scale.value, g.alpha.value, g.lockGapPx)
        } else {
            ContinuousAfHint(state.af)
        }
    }
}

/** 링과 그 위(12시) 자물쇠. 자물쇠는 그림이 아니라 아이콘이라 Canvas 밖에 둔다. */
@Composable
private fun RingWithLock(p: Offset, state: PillCameraState, scale: Float, alpha: Float, lockGapPx: Float) {
    Ring(p, state, scale, alpha, showSun = state.exposureSupported)
    val half = with(LocalDensity.current) { 11.dp.roundToPx() }
    Icon(
        painterResource(if (state.locked) NmSymbols.LockFill else NmSymbols.LockOpenRight),
        contentDescription = if (state.locked) "AE/AF 고정 풀기" else "AE/AF 고정",
        tint = if (state.locked) NmColor.Warning.C300 else Color.White,
        modifier = Modifier
            .offset { IntOffset(p.x.roundToInt() - half, (p.y - lockGapPx).roundToInt() - half) }
            .size(22.dp)
            .alpha(alpha)
    )
}

private enum class Gesture { Tap, Long, Pinch, Drag }

/** 손을 뗐는지 · 끌었는지 · 두 손가락인지를 길게 누르기 시간 안에 가른다. 시간이 다 차면 길게 누르기다. */
private suspend fun AwaitPointerEventScope.classify(id: PointerId, start: Offset): Gesture {
    val slop = viewConfiguration.touchSlop
    return withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var kind: Gesture? = null
        while (kind == null) {
            val e = awaitPointerEvent()
            val c = e.changes.firstOrNull { it.id == id }
            kind = when {
                e.changes.count { it.pressed } >= 2 -> Gesture.Pinch
                c == null || !c.pressed -> Gesture.Tap
                (c.position - start).getDistance() > slop -> Gesture.Drag
                else -> null
            }
        }
        kind
    } ?: Gesture.Long
}

private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val e = awaitPointerEvent()
        e.changes.forEach { it.consume() }
    } while (e.changes.any { it.pressed })
}

/** 두 손가락 배율. 1 · 2 · 3배를 지날 때 한 번씩 떤다. */
private suspend fun AwaitPointerEventScope.pinch(camera: PillCamera, haptic: HapticFeedback) {
    do {
        val e = awaitPointerEvent()
        val z = e.calculateZoom()
        if (z != 1f) {
            val before = camera.zoomTarget
            camera.setZoom(before * z)
            if (ZOOM_TICKS.any { (before - it) * (camera.zoomTarget - it) < 0f }) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        e.changes.forEach { it.consume() }
    } while (e.changes.any { it.pressed })
}

/** 링 자리 · 애니메이션 · 손짓 결과. 화면 하나에 하나다. */
private class FocusGestures(
    private val camera: PillCamera,
    private val previewView: PreviewView,
    private val haptic: HapticFeedback,
    val lockGapPx: Float,
    private val lockHitPx: Float
) {
    var ring by mutableStateOf<Offset?>(null)
    var tapKey by mutableIntStateOf(0)
    var lastTouch by mutableLongStateOf(0L)
    var dragging by mutableStateOf(false)
    val scale = Animatable(1f)
    val alpha = Animatable(0f)

    suspend fun AwaitPointerEventScope.handle(state: State<PillCameraState>) {
        val down = awaitFirstDown(requireUnconsumed = false)
        val kind = classify(down.id, down.position)
        lastTouch = System.currentTimeMillis()
        when (kind) {
            Gesture.Tap -> tap(down.position, state.value.locked)

            Gesture.Long -> {
                lock(down.position)
                consumeUntilUp()
            }

            Gesture.Pinch -> pinch(camera, haptic)

            Gesture.Drag -> dragExposure(down.id, down.position.x, state.value)
        }
    }

    private fun focusAt(p: Offset, lock: Boolean) {
        ring = p
        tapKey++
        camera.focus(previewView.meteringPointFactory.createPoint(p.x, p.y), lock)
    }

    private fun lock(p: Offset) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        focusAt(p, lock = true)
    }

    /** 링 위 자물쇠를 눌렀으면 고정을 켜고 끈다(기본 카메라와 같은 자리). 아니면 그 자리에 초점. */
    private fun tap(at: Offset, locked: Boolean) {
        val r = ring
        val onLock = r != null && (at - Offset(r.x, r.y - lockGapPx)).getDistance() < lockHitPx
        when {
            onLock && locked -> camera.unlock()

            onLock -> lock(requireNotNull(r))

            else -> {
                if (locked) camera.unlock()
                focusAt(at, lock = false)
            }
        }
    }

    /** 노출은 링이 떠 있을 때만 · 좌우로 — 기본 카메라처럼 막대가 링 아래 가로로 놓인다. 0 EV 를 지나면 떤다. */
    private suspend fun AwaitPointerEventScope.dragExposure(id: PointerId, startX: Float, state: PillCameraState) {
        val canExpose = ring != null && state.exposureSupported
        val stepPx = EV_TRACK.toPx() / (state.exposureRange.last - state.exposureRange.first).coerceAtLeast(1)
        var index = state.exposureIndex
        dragging = canExpose
        do {
            val e = awaitPointerEvent()
            val c = e.changes.firstOrNull { it.id == id } ?: break
            if (canExpose) {
                index = exposeTo(state, (c.position.x - startX) / stepPx, index)
                c.consume()
            }
            lastTouch = System.currentTimeMillis()
        } while (c.pressed)
        dragging = false
    }

    private fun exposeTo(state: PillCameraState, steps: Float, before: Int): Int {
        val next = (state.exposureIndex + steps.roundToInt()).coerceIn(state.exposureRange)
        if (before != 0 && next == 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        camera.setExposure(next)
        return next
    }
}

@Composable
private fun Ring(at: Offset, s: PillCameraState, scale: Float, alpha: Float, showSun: Boolean) {
    val pulse = if (s.af == Af.Searching) pulse(0.45f, 420) else 1f
    val color = when {
        s.af == Af.Failed -> NmColor.Error.C300
        s.af == Af.Focused || s.locked -> NmColor.Warning.C300
        else -> Color.White
    }
    val a = alpha * pulse
    val text = rememberTextMeasurer()
    val label = when {
        s.locked -> "AE/AF 고정"
        s.af == Af.Failed -> "초점을 못 맞췄어요"
        else -> null
    }

    Canvas(Modifier.fillMaxSize()) {
        val r = RING_RADIUS.toPx() * scale
        // 기본 카메라처럼 자물쇠 자리(12시)는 선을 끊는다 — 아이콘이 선 위에 얹혀 보이지 않게.
        drawArc(
            color.copy(alpha = a),
            startAngle = -90f + LOCK_GAP_DEG,
            sweepAngle = 360f - 2 * LOCK_GAP_DEG,
            useCenter = false,
            topLeft = at - Offset(r, r),
            size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
            style = Stroke(2.dp.toPx())
        )

        if (label != null) {
            val m = text.measure(label, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            // 자물쇠 위로 한 칸 더.
            val tl = Offset(at.x - m.size.width / 2f, at.y - r - 22.dp.toPx() - m.size.height)
            drawRoundRect(
                Color.Black.copy(alpha = 0.55f * alpha),
                topLeft = tl - Offset(8.dp.toPx(), 3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(m.size.width + 16.dp.toPx(), m.size.height + 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            )
            drawText(m, color = color.copy(alpha = alpha), topLeft = tl)
        }

        if (showSun) {
            // 해 막대 — 기본 카메라처럼 링 아래 25 에 링 지름만큼 가로. 오른쪽이 밝게. 가운데가 0 EV.
            val y = at.y + RING_RADIUS.toPx() + 25.dp.toPx()
            val half = EV_TRACK.toPx() / 2f
            val range = (s.exposureRange.last - s.exposureRange.first).coerceAtLeast(1)
            val f = (s.exposureIndex - s.exposureRange.first) / range.toFloat()
            val sunX = at.x - half + f * 2 * half
            val gap = 14.dp.toPx()
            val line = Color.White.copy(alpha = alpha)
            if (sunX - gap > at.x - half) drawLine(line, Offset(at.x - half, y), Offset(sunX - gap, y), 1.5.dp.toPx())
            if (sunX + gap < at.x + half) drawLine(line, Offset(sunX + gap, y), Offset(at.x + half, y), 1.5.dp.toPx())
            sun(Offset(sunX, y), Color.White.copy(alpha = alpha))
        }
    }
}

/** 해 — 기본 카메라 크기(가운데 원 5 · 살 8~11.5). */
private fun DrawScope.sun(c: Offset, color: Color) {
    drawCircle(color, radius = 5.dp.toPx(), center = c)
    val inner = 8.dp.toPx()
    val outer = 11.5.dp.toPx()
    repeat(8) { i ->
        val t = Math.PI / 4 * i
        val d = Offset(cos(t).toFloat(), sin(t).toFloat())
        drawLine(color, c + d * inner, c + d * outer, 1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/** 탭하지 않았을 때 — 연속 AF 가 다시 맞추면 가운데 작은 링이 깜빡이다, 맞으면 노랗게 잠깐 남고 사라진다. */
@Composable
private fun ContinuousAfHint(af: Af) {
    val alpha = remember { Animatable(0f) }
    var focusedFlash by remember { mutableStateOf(false) }
    LaunchedEffect(af) {
        when (af) {
            Af.Searching -> {
                focusedFlash = false
                alpha.animateTo(0.85f, tween(120))
            }

            Af.Focused -> if (alpha.value > 0f) {
                focusedFlash = true
                delay(450)
                alpha.animateTo(0f, tween(250))
                focusedFlash = false
            }

            else -> alpha.animateTo(0f, tween(250))
        }
    }
    if (alpha.value <= 0f) return
    val pulse = if (focusedFlash) 1f else pulse(0.4f, 380)
    Canvas(Modifier.fillMaxSize()) {
        val color = if (focusedFlash) NmColor.Warning.C300 else Color.White
        drawCircle(
            color.copy(alpha = alpha.value * pulse),
            radius = 22.dp.toPx(),
            center = center,
            style = Stroke(1.4.dp.toPx())
        )
    }
}

@Composable
private fun pulse(low: Float, ms: Int): Float {
    val v by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f,
        low,
        infiniteRepeatable(tween(ms), RepeatMode.Reverse),
        label = "pulse"
    )
    return v
}

private val ZOOM_TICKS = listOf(1f, 2f, 3f)
private val RING_RADIUS = 34.dp

/** 기본 카메라 — 해 막대 길이 = 링 지름. */
private val EV_TRACK = 68.dp
private const val HIDE_AFTER_MS = 2500L
private val LOCK_HIT = 28.dp

/** 링 12시 자물쇠 자리에서 끊는 각(양쪽). */
private const val LOCK_GAP_DEG = 20f
