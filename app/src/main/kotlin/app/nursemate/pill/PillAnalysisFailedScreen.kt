package app.nursemate.pill

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.nursemate.BuildConfig
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols

/**
 * 분석 실패 — 정본 「알약 식별 · 촬영 ~ 로딩 · V2.1.0 (NM-586)」 ⑦.
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
    BackHandler(onBack = onBack)
    PillStateLayout(
        navTitle = "",
        onBack = onBack,
        title = "분석에 실패했어요",
        description = "네트워크 연결을 확인하고\n다시 시도해 주세요",
        primary = PillStateAction("다시 시도", onRetry),
        secondary = PillStateAction("뒤로", onBack),
        modifier = modifier,
        visual = { PillStateIcon(NmSymbols.WifiOff, NmColor.Error.C50, NmColor.Error.C500) },
        extra = {
            if (message != null && BuildConfig.DEBUG) {
                Text(
                    text = message,
                    style = PillOutcomeDetail,
                    color = NmTheme.semanticColors.textTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    )
}
