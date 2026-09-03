package app.nursemate.pill

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nursemate.BuildConfig
import app.nursemate.R
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.ui.SystemBarIcons

/**
 * 분석 실패 — 디자인 `⑦ 분석 실패`.
 *
 * 모델 로드·추론이 깨졌거나(현재) 서버 호출이 실패했을 때(인증 이후)다.
 * 탐지 0개는 실패가 아니므로 여기가 아니라 [PillNotFoundScreen] 으로 간다.
 *
 * ## 두 버튼의 목적지는 스펙(NM-146)이 정한다
 * ```
 * E -->|재시도| B      로딩으로 — 곧바로 다시 검출
 * E -->|뒤로|   X      촬영/미리보기로 복귀
 * ```
 * **iOS는 둘 다 스펙과 다르다** — '다시 시도'가 `popViewController` 라 미리보기로 가고(사용자가
 * '이 사진 사용'을 한 번 더 눌러야 재시도가 된다), '뒤로'는 홈으로 나간다. 여기서는 스펙을 따른다.
 *
 * @param message 실패 사유. **디버그 빌드에서만** 설명 아래에 띄운다 — 예외 메시지에는
 *                모델 파일 절대 경로 같은 내부 사정이 담긴다. 정본에도 이 자리가 없다.
 *                릴리스에서는 로그(태그 `NM394`)로만 남는다.
 */
@Composable
fun PillAnalysisFailedScreen(message: String?, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        NmNavBar(title = "", onBack = onBack)

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
                    .background(NmColor.Error.C50, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_wifi_off),
                    contentDescription = null,
                    tint = NmColor.Error.C500,
                    modifier = Modifier.size(42.dp)
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
                    text = "분석에 실패했어요",
                    style = PillOutcomeTitle,
                    color = colors.textPrimary
                )
                Text(
                    text = "네트워크 연결을 확인하고\n다시 시도해 주세요",
                    style = PillOutcomeDescription,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                if (message != null && BuildConfig.DEBUG) {
                    Text(
                        text = message,
                        style = PillOutcomeDetail,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NmButtonPrimary(
                text = "다시 시도",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            )
            NmButtonSecondary(
                text = "뒤로",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
