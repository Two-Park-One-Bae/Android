package app.nursemate.pill

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.Usage
import app.nursemate.ui.SystemBarIcons

/**
 * 미리보기 — 디자인 `② 미리보기`.
 *
 * Nav Bar("미리보기") / 폭을 꽉 채운 정사각 사진 / 하단 액션 바(재촬영 · 이 사진 사용).
 *
 * 사진은 **정사각**이다. 촬영 화면의 가이드와 [app.nursemate.core.vision.ImageLoader.load] 의
 * 중앙 정사각 크롭을 거쳤으므로, 여기 보이는 것이 곧 모델이 받는 그림이다.
 *
 * ## iOS와 다른 점 — 뒤로가기
 * iOS는 네비바 뒤로를 누르면 확인 팝업("지금 나가면 촬영한 사진이 사라져요")을 띄우고 **홈으로**
 * 나간다. 여기서는 **재촬영과 같이 촬영 화면으로** 돌아간다. Android는 시스템 뒤로 제스처가
 * 항상 살아 있어 경로가 둘인데, 둘을 같은 동작으로 맞추는 편이 예측 가능하다. 사진을 잃는
 * 대가도 "다시 찍으면 됨"이라 팝업으로 막을 만큼 무겁지 않다.
 * (스펙 NM-143 도 미리보기의 분기를 `재촬영` / `이 사진 사용` 둘로만 규정한다.)
 */
@Composable
fun PillPreviewScreen(
    state: PillUiState,
    usage: Usage?,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    // 네비바 뒤로와 시스템 뒤로를 같은 동작으로 묶는다. 그냥 두면 시스템 뒤로만
    // 사진을 안 버리고 돌아가서, 어느 쪽으로 나갔느냐에 따라 상태가 달라진다.
    BackHandler(onBack = onRetake)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        NmNavBar(title = "미리보기", onBack = onRetake)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // 정본 Content 프레임이 justifyContent = center 다 — 사진은 네비바에 붙지 않고
            // 남은 공간의 세로 가운데에 온다.
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhotoArea(state)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 모르면 줄 자체를 두지 않는다 — 숫자를 지어내면 남은 횟수를 오해하게 한다.
            if (usage != null) {
                Text(
                    text = "오늘 남은 횟수 ${usage.remaining}/${usage.limit}",
                    style = RemainingLabel,
                    color = colors.textTertiary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NmButtonSecondary(
                    text = "재촬영",
                    onClick = onRetake,
                    modifier = Modifier.weight(1f)
                )
                NmButtonPrimary(
                    text = "이 사진 사용",
                    onClick = onConfirm,
                    enabled = state.photo != null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 사진 자리 — 폭을 꽉 채운 정사각형.
 *
 * 디코딩에 4000×3000 기준 500 ms 안팎이 걸린다. 자리를 미리 잡아 두지 않으면 사진이 들어올 때
 * 레이아웃이 덜컥거린다.
 */
@Composable
private fun PhotoArea(state: PillUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(NmColor.Neutral.C100),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.photo != null -> Image(
                bitmap = state.photo.asImageBitmap(),
                contentDescription = "촬영한 알약 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            state.errorMessage != null -> Text(
                text = state.errorMessage,
                style = NmTypography.body,
                color = NmColor.Error.C600,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(NmSpacing.lg)
            )

            else -> CircularProgressIndicator(color = NmColor.Primary.C500)
        }
    }
}

/** 정본 12·500. 스케일에 없는 크기라 여기 명시한다. */
private val RemainingLabel = NmTypography.caption
