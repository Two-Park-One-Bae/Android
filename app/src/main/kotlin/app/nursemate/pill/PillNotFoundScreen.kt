package app.nursemate.pill

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nursemate.R
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.ui.SystemBarIcons

/**
 * 결과 없음 — 디자인 `⑥ 결과 없음`.
 *
 * 탐지가 0개일 때다. **실패가 아니라 정상 결과**라서 실패 화면과 문구·아이콘이 다르다.
 * 사진을 55% 어둡게 덮고 그 위에 `search-x` 를 얹어 "이 사진에서 못 찾았다"를 드러낸다.
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
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)
    BackHandler(onBack = onExit)

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickFromGallery(uri) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        NmNavBar(title = "인식 결과", onBack = onExit)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PillPhotoCard(photo = state.photo, dimAlpha = 0.55f) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_search_x),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "알약을 찾지 못했어요",
                    style = PillOutcomeTitle,
                    color = colors.textPrimary
                )
                Text(
                    text = "알약이 잘 보이도록 다시 촬영하거나\n갤러리에서 다시 선택해 주세요",
                    style = PillOutcomeDescription,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NmButtonPrimary(
                text = "재촬영",
                onClick = onRetake,
                modifier = Modifier.fillMaxWidth()
            )
            NmButtonSecondary(
                text = "갤러리에서 선택",
                onClick = {
                    onGalleryTap()
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
