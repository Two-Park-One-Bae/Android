package app.nursemate.pill

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.ui.SystemBarIcons

/**
 * 카메라 권한 거부 안내 — 디자인 `③ 권한 거부`.
 *
 * 스펙(NM-143)이 요구하는 두 갈래를 준다: **설정으로 이동** / **갤러리에서 선택하기**.
 * 촬영을 막더라도 갤러리 경로는 열어 둬서 기능 자체가 잠기지 않게 한다.
 */
@Composable
fun PillPermissionDeniedScreen(onBack: () -> Unit, onPickFromGallery: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickFromGallery(uri) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
    ) {
        NmNavBar(title = "알약 촬영", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(NmColor.Primary.C50, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_camera_off),
                    contentDescription = null,
                    tint = NmColor.Primary.C500,
                    modifier = Modifier.size(42.dp)
                )
            }

            Text(
                text = "카메라 접근이 필요해요",
                style = NmTypography.heading3.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.padding(top = NmSpacing.lg)
            )
            Text(
                text = "알약을 촬영하려면 카메라 권한을 허용해 주세요",
                style = NmTypography.bodyLarge.copy(fontSize = 15.sp),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = NmSpacing.sm)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(NmSpacing.xs)
        ) {
            NmButtonPrimary(
                text = "설정으로 이동",
                onClick = {
                    // 권한을 두 번 거부하면 앱에서 다시 물을 수 없다. 설정으로 보내는 게 유일한 길이다.
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            NmButtonSecondary(
                text = "갤러리에서 선택하기",
                onClick = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
