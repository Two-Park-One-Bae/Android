package app.nursemate.pill

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.ui.SystemBarIcons
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 촬영 화면 — 디자인 `① 촬영 (카메라)`.
 *
 * 상단바(56) / 뷰파인더 / 하단 컨트롤(140). 뷰파인더 가운데에 폭을 꽉 채운 정사각형이 있고
 * 위아래는 **불투명한** `neutral-900` 이다(반투명 딤이 아니다).
 *
 * ## 프리뷰가 곧 정사각형이다 — 이게 정확도 요건이다
 * 화면 전체에 프리뷰를 깔고 정사각 테두리만 얹으면, 사용자가 사각형 안에 맞춘 것과
 * 모델이 받는 것이 어긋난다. 촬영본은 [app.nursemate.core.vision.ImageLoader.load] 가
 * **중앙 정사각으로 자르는데**, 화면을 채운 프리뷰의 가운데 사각형은 그 영역과 다르기 때문이다.
 *
 * 그래서 `PreviewView` 자체를 정사각형으로 두고 `FILL_CENTER` 로 채운다. 이러면 기하가 정확히
 * 맞는다 — 3:4 센서를 정사각 뷰에 `FILL_CENTER` 로 채우면 세로 12.5%~87.5% 구간이 보이는데,
 * 이는 중앙 정사각 크롭과 같은 영역이다. **보이는 것이 곧 모델이 받는 것**이 된다.
 */
@Composable
fun PillCameraScreen(
    onPhotoCaptured: (Uri) -> Unit,
    onPickFromGallery: (Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickFromGallery(uri) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 배경을 먼저 칠하고 인셋 패딩을 뒤에 건다. 순서를 바꾸면 상태바·제스처 영역에
            // 밝은 띠가 남는다.
            .background(NmColor.Neutral.C900)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        SystemBarIcons(darkIcons = false)

        CameraTopBar(
            torchOn = torchOn,
            torchAvailable = camera?.cameraInfo?.hasFlashUnit() == true,
            onClose = onClose,
            onToggleTorch = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            }
        )

        Column(modifier = Modifier.weight(1f)) {
            // 위아래 여백은 루트 배경(neutral-900)이 그대로 보이는 영역이다.
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    // 프리뷰가 이 경계를 넘지 않게 못박는다.
                    .clipToBounds()
            ) {
                CameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    onBound = { boundCamera, capture ->
                        camera = boundCamera
                        imageCapture = capture
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.White.copy(alpha = 0.7f))
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "알약을 겹치지 않게 펼쳐놓으세요",
                    style = GuideHintStyle,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }

        CameraControls(
            captureEnabled = imageCapture != null && !capturing,
            onGallery = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onShutter = {
                val capture = imageCapture ?: return@CameraControls
                capturing = true
                capture.takeAndSave(context) { uri ->
                    capturing = false
                    if (uri != null) onPhotoCaptured(uri)
                }
            }
        )
    }
}

/**
 * 상단바 — 닫기 · "알약 촬영" · 플래시. 디자인: 56dp, 좌우 16, space-between.
 *
 * ⚠️ **iOS와 동작이 다르다.** iOS는 `cameraFlashMode` 를 켜고 꺼서 **촬영 순간에만** 터뜨린다.
 * 여기서는 **토치(계속 켜짐)** 로 간다 — 어두운 병동에서 정사각 가이드 안에 알약을 맞추는
 * 동안 빛이 필요하기 때문이다. 켜져 있는지도 프리뷰로 바로 보인다.
 */
@Composable
private fun CameraTopBar(torchOn: Boolean, torchAvailable: Boolean, onClose: () -> Unit, onToggleTorch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            painter = painterResource(R.drawable.nm_ic_close),
            contentDescription = "촬영 닫기",
            tint = Color.White,
            modifier = Modifier
                .size(26.dp)
                .clickable(onClick = onClose)
        )
        Text(
            text = "알약 촬영",
            style = NmTypography.title.copy(fontSize = 17.sp),
            color = Color.White
        )
        if (torchAvailable) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_flash),
                contentDescription = if (torchOn) "조명 끄기" else "조명 켜기",
                tint = if (torchOn) NmColor.Warning.C300 else Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onToggleTorch)
            )
        } else {
            // 플래시가 없는 기기에서도 제목이 가운데 오도록 자리는 남긴다.
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

/** 하단 컨트롤 — 갤러리(48) · 셔터(74) · 균형용 빈 자리(48). 디자인: 140dp, 좌우 36. */
@Composable
private fun CameraControls(captureEnabled: Boolean, onGallery: () -> Unit, onShutter: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(NmColor.Neutral.C700, RoundedCornerShape(NmRadius.md))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(NmRadius.md))
                .clickable(onClick = onGallery),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_image),
                contentDescription = "갤러리에서 선택",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(74.dp)
                .border(4.dp, Color.White, CircleShape)
                .clickable(enabled = captureEnabled, onClick = onShutter),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        if (captureEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
        }

        // 셔터를 화면 정중앙에 두기 위한 균형추. 디자인의 'Spacer' 그대로다.
        Spacer(modifier = Modifier.size(48.dp))
    }
}

/**
 * CameraX 프리뷰. 부모가 정사각형이므로 이 뷰도 정사각형이다.
 *
 * `PreviewView`는 View라서 [AndroidView]로 감싼다.
 */
@Composable
private fun CameraPreview(lifecycleOwner: LifecycleOwner, onBound: (Camera, ImageCapture) -> Unit) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            // 정사각 뷰를 잘라서 채운다. 중앙 정사각 크롭과 같은 영역이 보인다(KDoc 참조).
            scaleType = PreviewView.ScaleType.FILL_CENTER

            // ⚠️ 기본값(PERFORMANCE)은 SurfaceView를 쓰는데, **별도 윈도우 레이어라
            //    Compose가 잘라내지 못한다.** 그래서 정사각 경계를 무시하고 위아래 딤 영역까지
            //    영상이 넘쳐 그려졌다. COMPATIBLE은 TextureView라 일반 뷰처럼 잘린다.
            //    지연·전력이 조금 불리하지만 정지 사진 촬영 화면이라 감수한다.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(previewView) {
        val cameraProvider = context.awaitCameraProvider()

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val capture = ImageCapture.Builder()
            // 알약 각인을 봐야 하므로 속도보다 화질을 택한다.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
        }.onSuccess { boundCamera ->
            provider = cameraProvider
            onBound(boundCamera, capture)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // ⚠️ `provider` 를 여기서 읽으면 안 된다. DisposableEffect(provider) 로 두고 안에서
            //    읽으면, provider 가 null → 실제값으로 바뀔 때 이전 효과가 정리되면서
            //    **방금 바인딩한 카메라를 곧바로 해제**한다(프리뷰가 검게 죽고 플래시도 먹통이 된다).
            //    키를 Unit 으로 고정하고, 화면을 떠날 때 한 번만 푼다.
            provider?.unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/**
 * `ProcessCameraProvider` 준비를 기다린다.
 *
 * CameraX는 `ListenableFuture`를 주는데 이 버전에는 코루틴 어댑터가 없다. `get()`으로 기다리면
 * 메인 스레드가 멈추므로 콜백을 코루틴으로 감싼다.
 */
private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener(
        {
            runCatching { future.get() }
                .onSuccess(continuation::resume)
                .onFailure(continuation::resumeWithException)
        },
        ContextCompat.getMainExecutor(this)
    )
}

/**
 * 촬영해 앱 캐시에 저장하고 그 위치를 돌려준다.
 *
 * 캐시에 두는 이유: 약포 사진은 환자 정보(**PII**)라 갤러리에 남기지 않는다.
 * 검출이 끝나면 지워도 되는 임시 파일이다.
 */
private fun ImageCapture.takeAndSave(context: Context, onResult: (Uri?) -> Unit) {
    val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
    takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onResult(output.savedUri ?: Uri.fromFile(file))
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(null)
            }
        }
    )
}

/** 정본 14·500. 스케일에 없는 굵기라 명시한다. */
private val GuideHintStyle = NmTypography.body.copy(fontWeight = FontWeight.Medium)
