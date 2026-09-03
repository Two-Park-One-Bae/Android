package app.nursemate.pill

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.nursemate.core.designsystem.NmColor

/**
 * 촬영 진입점 — 카메라 권한에 따라 [PillCameraScreen] 과 [PillPermissionDeniedScreen] 을 가른다.
 *
 * 스펙(NM-143)대로 **알약 식별에 들어오면 곧바로** 권한을 묻고, 허용돼 있으면 바로 촬영 화면이다.
 * 중간에 "무엇으로 찍을까요" 같은 선택 화면을 두지 않는다.
 */
@Composable
fun PillCaptureRoute(onPhotoSelected: (Uri) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    // 화면을 다시 그릴 때마다 권한 창이 뜨지 않도록 "이미 물어봤다"를 기억한다.
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        alreadyAsked = true
    }

    LaunchedEffect(Unit) {
        if (!granted && !alreadyAsked) requestPermission.launch(Manifest.permission.CAMERA)
    }

    // 설정에서 권한을 켜고 돌아오는 경로가 있다(③ 권한 거부 → 설정으로 이동).
    // 돌아왔을 때 화면이 그대로면 갇히므로 재개 시점에 다시 확인한다.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.hasCameraPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        granted -> PillCameraScreen(
            onPhotoCaptured = onPhotoSelected,
            onPickFromGallery = onPhotoSelected,
            onClose = onClose,
            modifier = modifier
        )

        alreadyAsked -> PillPermissionDeniedScreen(
            onBack = onClose,
            onPickFromGallery = onPhotoSelected,
            modifier = modifier
        )

        // 권한 창이 떠 있는 동안. 거부 화면을 미리 띄우면 허용을 눌러도 한 번 깜빡인다.
        else -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(NmColor.Neutral.C900)
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
