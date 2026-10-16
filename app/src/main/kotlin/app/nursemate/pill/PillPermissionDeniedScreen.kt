package app.nursemate.pill

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.v2.NmSymbols

/**
 * 카메라 권한 거부 안내 — 정본 「알약 식별 · 촬영 ~ 로딩 · V2.1.0 (NM-586)」 ③.
 *
 * 스펙(NM-143)이 요구하는 두 갈래를 준다: **설정으로 이동** / **갤러리에서 선택**.
 * 촬영을 막더라도 갤러리 경로는 열어 둬서 기능 자체가 잠기지 않게 한다.
 */
@Composable
fun PillPermissionDeniedScreen(
    onBack: () -> Unit,
    onPickFromGallery: (Uri) -> Unit,
    /**
     * 갤러리 버튼을 **누른 순간**. 사진을 고르기 전이라 취소해도 찍힌다.
     *
     * iOS 가 탭 시점에 찍어서(`trackButton("gallery", …)`) 맞춘 것이다 — 선택 시점으로 옮기면
     * 취소한 횟수만큼 수치가 갈린다.
     */
    onGalleryTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickFromGallery(uri) }

    PillStateLayout(
        navTitle = "",
        onBack = onBack,
        title = "카메라 접근이 필요해요",
        description = "알약을 촬영하려면 카메라 권한을 허용해 주세요",
        // 권한을 두 번 거부하면 앱에서 다시 물을 수 없다. 설정으로 보내는 게 유일한 길이다.
        primary = PillStateAction("설정으로 이동") {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        },
        // 이름 · 모양은 결과 없음의 `갤러리에서 선택` 과 같다(spec §권한 거부).
        secondary = PillStateAction("갤러리에서 선택") {
            onGalleryTap()
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        modifier = modifier,
        gap = 20.dp,
        visual = { PillStateIcon(NmSymbols.NoPhotography, NmColor.Primary.C50, NmColor.Primary.C500) }
    )
}
