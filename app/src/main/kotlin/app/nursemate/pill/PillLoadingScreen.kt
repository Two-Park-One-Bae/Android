package app.nursemate.pill

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.ui.SystemBarIcons

/**
 * 식별 중 — 디자인 `④ 로딩 (식별 중)`.
 *
 * 제목 없는 Nav Bar / 사진 카드(300, 어둡게 덮음) / 스피너 + 안내 문구.
 *
 * ## 뒤로가기를 막는다
 * 디자인의 Nav Bar 제목이 비어 있고 iOS도 뒤로 버튼을 숨긴다. 지금은 온디바이스 검출만 돌아
 * 되돌아가도 잃을 게 없지만, 서버 속성 추출(`POST /pill-attributes`)이 붙으면 **요청이 이미
 * 나가 식별 횟수가 차감된 뒤**라 중간에 빠져나가면 안 된다. 그때 규칙을 바꾸지 않도록 지금부터
 * 막아 둔다. Android는 시스템 뒤로가 따로 살아 있으므로 [BackHandler] 로도 막는다.
 */
@Composable
fun PillLoadingScreen(state: PillUiState, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    // 검출이 끝날 때까지 나갈 수 없다(위 KDoc 참조).
    BackHandler(enabled = true) { /* 의도적으로 아무것도 하지 않는다 */ }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NmNavBar(title = "")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PillPhotoCard(photo = state.photo, dimAlpha = 0.15f)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(NmColor.Primary.C50, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = NmColor.Primary.C500,
                    // 정본 스피너는 지름 40, 안쪽 반지름 비율 0.8 → 선 두께 4.
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(40.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "알약을 식별하고 있어요",
                    style = LoadingTitle,
                    color = colors.textPrimary
                )
                Text(
                    text = "잠시만 기다려 주세요",
                    style = LoadingSubtitle,
                    color = colors.textTertiary
                )
            }
        }
    }
}

/** 정본 17·700 / 13·400. 스케일에 없는 크기라 명시한다. */
private val LoadingTitle = NmTypography.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)
private val LoadingSubtitle = NmTypography.body.copy(fontSize = 13.sp, lineHeight = 20.sp)
