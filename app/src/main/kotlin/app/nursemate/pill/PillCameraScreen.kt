package app.nursemate.pill

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.pill.camera.CameraFocusOverlay
import app.nursemate.pill.camera.CameraLayout
import app.nursemate.pill.camera.Grid
import app.nursemate.pill.camera.LevelGuide
import app.nursemate.pill.camera.OutsideMask
import app.nursemate.pill.camera.PillCamera
import app.nursemate.pill.camera.PillCameraState
import app.nursemate.ui.SystemBarIcons
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 촬영 화면 — 갤럭시 기본 카메라(S24 · One UI, 1:1 비율)의 배치와 조작을 옮겼다(NM-630).
 *
 * 본 앱 것으로 남긴 것은 **1:1 미리보기 · 안내 문구 · 아이콘 모양** 셋뿐이다(사용자 결정 2026-10-17).
 * 나머지 — 상태 표시줄 숨김 · 오른쪽 위 아이콘 줄 · 배율 캡슐 · 셔터 줄 · 탭 초점 · 노출 · 수평계 ·
 * 야간 · 볼륨 키 셔터 — 는 기본 카메라를 실측해 맞췄다. 수치는 [CameraLayout], 조작은 [PillCamera] ·
 * [CameraFocusOverlay].
 *
 * ⚠️ 정본(`DESIGN.pen` 「① 촬영」)은 아직 옛 배치다. 이 화면이 **정본 개정보다 먼저** 갔다 — 차이는
 * NM-630 PR 에 정리해 spec/design 개정 근거로 넘긴다.
 *
 * 기본 카메라와 일부러 다르게 둔 것:
 * - 왼쪽 위 닫기 — 기본 카메라엔 없지만 앱 흐름에서 빠져나갈 길이 필요하다
 * - 왼쪽 원 = 갤러리 — 기본 카메라는 방금 찍은 사진이다. 여기선 찍으면 곧바로 다음 화면이라 둘 사진이 없다
 * - 셔터 오른쪽 빈자리 — 전면 카메라 전환은 알약 촬영에 쓸 일이 없다
 * - 초점 링 색(흰 → 노랑 · 빨강) — 알약은 초점이 결과를 가르므로 맞았는지 바로 보이게 했다
 *
 * ## 프리뷰가 곧 정사각형이다 — 이게 정확도 요건이다
 * 화면 전체에 프리뷰를 깔고 정사각 테두리만 얹으면, 사용자가 사각형 안에 맞춘 것과
 * 모델이 받는 것이 어긋난다. 촬영본은 [app.nursemate.core.vision.ImageLoader.load] 가
 * **중앙 정사각으로 자르는데**, 화면을 채운 프리뷰의 가운데 사각형은 그 영역과 다르기 때문이다.
 *
 * 그래서 `PreviewView` 자체를 정사각형으로 두고 `FILL_CENTER` 로 채운다. 이러면 기하가 정확히
 * 맞는다 — 3:4 센서를 정사각 뷰에 `FILL_CENTER` 로 채우면 세로 12.5%~87.5% 구간이 보이는데,
 * 이는 중앙 정사각 크롭과 같은 영역이다. **보이는 것이 곧 모델이 받는 것**이 된다.
 *
 * ## 미리보기는 SurfaceView(PERFORMANCE)다 — 발열
 * 예전엔 TextureView(COMPATIBLE)였다. PoC 에서 TextureView 는 CPU 74~85% 로 폰이 금방 뜨거워졌고,
 * SurfaceView 로 바꾸니 32~37%(기본 카메라 26~54%)였다.
 *
 * SurfaceView 는 창 뒤에 따로 그려져 Compose 가 잘라내지 못하는 기기가 있다 — 2026-09 에 그래서
 * 정사각 밖 위아래로 영상이 넘쳤고, COMPATIBLE 로 피했었다. 이번엔 **잘라내기에 기대지 않는다.**
 * 미리보기 바로 위에 정사각 바깥을 검게 덮는 막([OutsideMask])을 그린다. SurfaceView 는 창에 구멍을 뚫어
 * 보이는 방식이라, 그 뒤에 그린 것은 구멍을 덮는다 — 잘림이 되든 안 되든 정사각 밖은 늘 검다.
 */
@Composable
fun PillCameraScreen(
    onPhotoCaptured: (Uri) -> Unit,
    onPickFromGallery: (Uri) -> Unit,
    /**
     * 갤러리 버튼을 **누른 순간**. 사진을 고르기 전이라 취소해도 찍힌다.
     *
     * iOS 가 탭 시점에 찍어서(`trackButton("gallery", …)`) 맞춘 것이다 — 선택 시점으로 옮기면
     * 취소한 횟수만큼 수치가 갈린다.
     */
    onGalleryTap: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val camera = remember { PillCamera(context.applicationContext) }
    val state by camera.state.collectAsStateWithLifecycle()
    val previewView = remember {
        PreviewView(context).apply {
            // 정사각 뷰를 잘라서 채운다. 중앙 정사각 크롭과 같은 영역이 보인다(KDoc 참조).
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    var flashOn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // 야간으로 찍는 중 — 한 장에 수 초 걸려 진행 표시를 띄운다([NightCapturing]).
    var nightShooting by remember { mutableStateOf(false) }
    // 재촬영(이 화면으로 되돌아옴) · 야간 전환 · 백그라운드 복귀에도 **고른 야간 · 배율을 이어 간다**. 기본 카메라도 기억한다
    // (2026-10-19 실기기 — 재촬영하면 야간이 꺼지고 복귀하면 1× 로 돌아갔다, NM-632 체크리스트 6번).
    var wantNight by rememberSaveable { mutableStateOf(false) }
    var wantZoom by rememberSaveable { mutableFloatStateOf(1f) }
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    val blink = remember { Animatable(0f) }
    val keys = remember { FocusRequester() }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickFromGallery(uri) }

    LaunchedEffect(Unit) { camera.bindOrIgnore(owner, previewView, night = wantNight, zoom = wantZoom) }
    // 배율은 핀치 · 버튼 · 다시 묶기에서 바뀐다 — 원하는 값([PillCamera.zoomTarget])을 따라 둔다.
    LaunchedEffect(state.zoom, state.bound) { wantZoom = camera.zoomToKeep(wantZoom) }
    DisposableEffect(Unit) { onDispose { camera.unbind() } }
    HideStatusBar()
    // 검은 바탕이라 내비 바 아이콘(과 끌어 내린 상태 표시줄)을 밝게.
    SystemBarIcons(darkIcons = false)
    // 볼륨 키를 받으려면 이 화면이 포커스를 쥐고 있어야 한다. 갤러리에서 돌아오면 포커스가 풀려 있다.
    LifecycleResumeEffect(Unit) {
        runCatching { keys.requestFocus() }
        onPauseOrDispose { }
    }

    val shoot: () -> Unit = shoot@{
        if (!state.bound || busy) return@shoot
        busy = true
        nightShooting = state.night
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            blink.snapTo(1f)
            blink.animateTo(0f, tween(220))
        }
        camera.shoot(flashOn) { uri ->
            busy = false
            nightShooting = false
            if (uri != null) {
                onPhotoCaptured(uri)
            } else {
                // onError 만으로는 셔터를 눌렀는데 반응이 없는 것처럼 보인다.
                Toast.makeText(context, "촬영에 실패했어요. 다시 시도해 주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // 위아래는 상태 표시줄을 숨기고 끝까지 그리되(배치는 [CameraLayout]), 가로로 돌았을 때 옆에 오는
            // 내비 바 · 카메라 구멍은 비켜 선다.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .semantics { paneTitle = "알약 촬영" }
            .focusRequester(keys)
            .focusable()
            .onPreviewKeyEvent { e ->
                // 볼륨 키 셔터 — 기본 카메라와 같다. 누를 때 한 번만 찍고, 뗄 때도 먹어서 음량이 바뀌지 않게 한다.
                if (e.key != Key.VolumeDown && e.key != Key.VolumeUp) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) shoot()
                true
            }
    ) {
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val layout = CameraLayout.of(
            width = maxWidth,
            height = maxHeight,
            navBottom = navBottom,
            cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        )
        val previewModifier = Modifier
            .offset(layout.previewLeft, layout.previewTop)
            .size(layout.previewSide)

        AndroidView({ previewView }, previewModifier.clipToBounds())
        OutsideMask(layout)

        Box(previewModifier.clipToBounds().testTag(PREVIEW_TAG)) {
            Grid()
            LevelGuide()
            CameraFocusOverlay(state, camera, previewView, showRing = !nightShooting)
            // 깜빡임은 그리기 단계에서만 읽는다 — 구성 단계에서 읽으면 깜빡이는 프레임마다 화면 전체가 다시 구성된다.
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = blink.value }.background(Color.Black.copy(alpha = 0.7f)))
        }

        TopIcons(
            state = state,
            flashOn = flashOn,
            onClose = onClose,
            onFlash = { flashOn = !flashOn },
            onNight = {
                if (!busy) {
                    busy = true
                    // 배율 애니메이션이 새 세션에 옛 목표를 이어 쓰지 않게 먼저 멈춘다 — 다시 묶을 때 원하던 배율을 이어 건다.
                    zoomJob?.cancel()
                    wantNight = !state.night
                    scope.launch {
                        camera.bindOrIgnore(owner, previewView, night = wantNight)
                        busy = false
                    }
                }
            },
            modifier = Modifier.offset(y = layout.iconsTop)
        )

        GuideHint(
            onPreview = !layout.roomy,
            modifier = Modifier
                .fillMaxWidth()
                .offset(
                    y = if (layout.roomy) {
                        layout.previewBottom + HINT_TOP
                    } else {
                        layout.previewTop +
                            CameraLayout.INSET
                    }
                )
        )

        ZoomPills(
            state = state,
            onZoom = { to ->
                zoomJob?.cancel()
                zoomJob = scope.launch {
                    Animatable(camera.zoomTarget).animateTo(to, tween(280, easing = FastOutSlowInEasing)) {
                        camera.setZoom(value)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset(
                    y = if (layout.roomy) {
                        maxHeight - navBottom - CameraLayout.ZOOM_CENTER - CameraLayout.ZOOM_HEIGHT / 2
                    } else {
                        layout.previewBottom - CameraLayout.INSET - CameraLayout.ZOOM_HEIGHT
                    }
                )
        )

        // 배율 선택기(좁은 화면에선 미리보기 안에 있다)보다 위에 그린다.
        NightCapturing(visible = nightShooting, modifier = previewModifier)

        ShutterRow(
            captureEnabled = state.bound && !busy,
            onShutter = shoot,
            onGallery = {
                onGalleryTap()
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.offset(
                y = maxHeight - navBottom - CameraLayout.SHUTTER_CENTER - CameraLayout.SHUTTER_HEIGHT / 2
            )
        )
    }
}

/** 묶다 실패하면(카메라를 다른 앱이 쥐고 있는 등) 미리보기가 검게 남고 셔터가 잠긴다 — 예전과 같다. */
private suspend fun PillCamera.bindOrIgnore(
    owner: LifecycleOwner,
    view: PreviewView,
    night: Boolean = state.value.night,
    zoom: Float = zoomTarget
) {
    runCatching { bind(owner, view, night, zoom) }.onFailure {
        if (it is CancellationException) throw it
        Log.w("PillCamera", "카메라를 묶지 못했다", it)
    }
}

/**
 * 상태 표시줄을 숨긴다 — 기본 카메라와 같다. 위에서 끌면 잠깐 나온다.
 *
 * 떠날 때 **되돌린다.** 아이콘 색([app.nursemate.ui.SystemBarIcons])과 달리 다음 화면들은 표시 여부를
 * 따로 선언하지 않아, 안 되돌리면 앱 전체에서 상태 표시줄이 사라진다.
 */
@Composable
private fun HideStatusBar() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

/**
 * 아이콘 줄 — 기본 카메라처럼 오른쪽 위에 아이콘만(플래시 · 야간). 칸 36, 좌우 여백 15.
 * 닫기는 기본 카메라에 없지만 앱 흐름에서 빠져나갈 길이라 왼쪽 끝에 둔다.
 *
 * 플래시: 스펙 「기기에 플래시 유닛이 없으면 버튼을 노출하지 않는다」. 토치가 아니라 촬영 순간에만 터지는
 * 플래시다(NM-440 — 표준 카메라 동작 · iOS 와 같은 온 · 오프 2단).
 *
 * 야간: 기기에 야간 확장이 있을 때만 둔다. 꺼져 있는데 어두우면 기본 카메라처럼 노란 점으로 권한다.
 */
@Composable
private fun TopIcons(
    state: PillCameraState,
    flashOn: Boolean,
    onClose: () -> Unit,
    onFlash: () -> Unit,
    onNight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(CameraLayout.ICONS_HEIGHT)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCell(onClick = onClose) {
            Icon(painterResource(NmSymbols.Close), "촬영 닫기", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.weight(1f))
        if (state.hasFlash) {
            IconCell(onClick = onFlash) {
                Icon(
                    painterResource(NmSymbols.FlashOn),
                    if (flashOn) "플래시 끄기" else "플래시 켜기",
                    tint = if (flashOn) NmColor.Warning.C300 else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (state.nightAvailable) {
            IconCell(onClick = onNight) {
                Box {
                    Icon(
                        painterResource(if (state.night) NmSymbols.BedtimeFill else NmSymbols.Bedtime),
                        if (state.night) "야간 촬영 끄기" else "야간 촬영 켜기",
                        tint = if (state.night) NmColor.Warning.C300 else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    if (!state.night && state.lowLight) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .background(NmColor.Warning.C300, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCell(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(CameraLayout.ICONS_HEIGHT)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 야간으로 찍는 동안 — 한 장에 수 초(S24 약 7~10초) 걸리는데 셔터가 흐려지는 것 말고 표시가 없어 멈춘 것처럼 보였다
 * (2026-10-19 실기기, NM-632 체크리스트 5번). 기본 카메라처럼 진행 원과 「움직이지 마세요」를 미리보기 위에 띄운다.
 *
 * ⚠️ 문구는 정본에 없다 — 촬영 화면 정본 개정(NM-630 PR 표) 대상이다.
 */
@Composable
private fun NightCapturing(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Box(modifier.background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = NmColor.Warning.C300, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            Text(text = "움직이지 마세요", style = GuideHintStyle, color = Color.White)
        }
    }
}

/**
 * 안내 문구 — 본 앱 정본 문구 그대로. 자리가 있으면 미리보기 아래 검은 바탕에(옅은 흰 알약),
 * 없으면 미리보기 위 영상에 얹는다(진한 검정 알약 — 영상 위에서도 읽히게).
 */
@Composable
private fun GuideHint(onPreview: Boolean, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Text(
            text = "알약을 겹치지 않게 펼쳐놓으세요",
            style = GuideHintStyle,
            color = Color.White,
            modifier = Modifier
                .background(
                    if (onPreview) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f),
                    CircleShape
                )
                .padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

/**
 * 배율 캡슐 — 기본 카메라와 같다. 어두운 회색 캡슐에 .6 · 1 · 2 · 3, 고른 칸은 한 단계 밝은 원에
 * 지금 배율(`1.4×`)을 그대로 보인다. 버튼은 0.28초에 걸쳐 바뀐다(한 번에 건너뛰면 덜컹거린다).
 *
 * ⚠️ .6(초광각)은 고정 초점이라 알약 거리(15~20cm)에선 흐리다. 기본 카메라와 같게 두되, 알약엔 1 이 기본이다.
 */
@Composable
private fun ZoomPills(state: PillCameraState, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    val presets = buildList {
        if (state.minZoom < 0.99f) add(state.minZoom)
        addAll(listOf(1f, 2f, 3f).filter { it <= state.maxZoom })
    }
    if (presets.size < 2) return
    val current = presets.lastOrNull { state.zoom >= it - 0.01f } ?: presets.first()
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .height(CameraLayout.ZOOM_HEIGHT)
                .background(CapsuleGray, CircleShape)
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { z ->
                val on = z == current
                Box(
                    Modifier
                        .size(ZOOM_CELL)
                        .clip(CircleShape)
                        .background(if (on) SelectedGray else Color.Transparent)
                        .clickable { onZoom(z) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (on) "${zoomLabel(state.zoom)}×" else zoomLabel(z),
                        color = Color.White,
                        fontSize = if (on) 13.sp else 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 0.6 → `.6`, 1.0 → `1`, 1.4 → `1.4` — 기본 카메라 표기. */
internal fun zoomLabel(z: Float): String = "%.1f".format(Locale.ROOT, z).let {
    if (z < 1f) it.removePrefix("0") else it.removeSuffix(".0")
}

/**
 * 셔터 줄 — 왼쪽 갤러리(보이는 원 55) · 가운데 셔터(보이는 원 73, 누르는 자리 100) · 오른쪽 빈자리.
 * 셔터는 누르는 동안 살짝 작아진다. 찍는 동안(야간은 한 장에 수 초)은 흐려지고 눌리지 않는다.
 */
@Composable
private fun ShutterRow(
    captureEnabled: Boolean,
    onShutter: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(90), label = "shutter")
    Row(
        modifier
            .fillMaxWidth()
            .height(CameraLayout.SHUTTER_HEIGHT)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(SIDE_SLOT), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(SIDE)
                    .clip(CircleShape)
                    .background(SideGray)
                    .clickable(onClick = onGallery),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(NmSymbols.Image), "갤러리에서 선택", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(CameraLayout.SHUTTER_HEIGHT)
                .semantics { contentDescription = "촬영" }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = captureEnabled,
                    onClick = onShutter
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(SHUTTER_VISUAL)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(if (captureEnabled) ShutterWhite else ShutterWhite.copy(alpha = 0.5f), CircleShape)
            )
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(SIDE_SLOT))
    }
}

/** 미리보기 정사각 — 계측 테스트가 자리를 찾는다(`PillCameraMaskTest`). */
internal const val PREVIEW_TAG = "camera_preview"

/** 정본 14·500. 스케일에 없는 굵기라 명시한다. */
private val GuideHintStyle = NmTypography.body.copy(fontWeight = FontWeight.Medium)

/** 안내 문구 — 미리보기 아래 여백. */
private val HINT_TOP: Dp = 18.dp
private val ZOOM_CELL = 34.dp
private val SHUTTER_VISUAL = 73.dp

/** 갤러리 · 오른쪽 빈자리 — 보이는 원 55, 자리 78. */
private val SIDE = 55.dp
private val SIDE_SLOT = 78.dp

// 기본 카메라 색(화면에서 뽑았다). 앱 팔레트에 없는 회색이라 여기 둔다.
private val CapsuleGray = Color(0xFF262626)
private val SelectedGray = Color(0xFF474747)
private val SideGray = Color(0xFF2E2E2E)
private val ShutterWhite = Color(0xFFF8F7F2)
