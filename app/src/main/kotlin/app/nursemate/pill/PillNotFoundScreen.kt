package app.nursemate.pill

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.v2.NmSymbols

/**
 * 결과 없음 — 정본 「알약 식별 · 촬영 ~ 로딩 · V2.1.0 (NM-586)」 ⑥.
 *
 * 탐지가 0개일 때다. **실패가 아니라 정상 결과**라서 실패 화면과 문구·아이콘이 다르다.
 * 사진을 55% 어둡게 덮고 그 위에 `search_off` 를 얹어 "이 사진에서 못 찾았다"를 드러낸다.
 *
 * 뒤로가기는 확인 없이 홈으로 보낸다(iOS도 동일). 잃을 게 사진 한 장뿐이다.
 */
@Composable
fun PillNotFoundScreen(
    state: PillUiState,
    onRetake: () -> Unit,
    onPickFromGallery: (Uri) -> Unit,
    /**
     * 갤러리 버튼을 **누른 순간**. 사진을 고르기 전이라 취소해도 찍힌다.
     *
     * iOS 가 탭 시점에 찍어서(`trackButton("gallery", …)`) 맞춘 것이다 — 선택 시점으로 옮기면
     * 취소한 횟수만큼 수치가 갈린다.
     */
    onGalleryTap: () -> Unit = {},
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickFromGallery(uri) }
    BackHandler(onBack = onExit)

    PillStateLayout(
        navTitle = "인식 결과",
        onBack = onExit,
        title = "알약을 찾지 못했어요",
        description = "알약이 잘 보이도록 다시 촬영하거나\n갤러리에서 다시 선택해 주세요",
        primary = PillStateAction("재촬영", onRetake),
        secondary = PillStateAction("갤러리에서 선택") {
            onGalleryTap()
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        modifier = modifier,
        visual = {
            PillPhotoCard(photo = state.photo, dimAlpha = 0.55f) {
                Icon(
                    painter = painterResource(NmSymbols.SearchOff),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
                )
            }
        }
    )
}
