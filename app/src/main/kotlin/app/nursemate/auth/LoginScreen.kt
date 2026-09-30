package app.nursemate.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.data.auth.AuthError
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.ui.SystemBarIcons

/**
 * 로그인 — 디자인 `인증 / 로그인`.
 *
 * 위에 로고, 가운데 카피, 아래 소셜 로그인 3종. 배경은 `bg-app` 이 아니라 **흰색**이다.
 *
 * ## 둘러보기가 없다
 * 스펙(`spec/feature/auth/README.md`)이 "미로그인 시 강제 진입 — 둘러보기/skip 없음(로그인 필수)"로
 * 못박는다. 알약 식별 API가 전부 Firebase ID 토큰을 요구하고, 익명 로그인은 서버가 401로 막는다
 * (열어두면 식별 한도를 무력화할 수 있다). 그래서 이 화면에는 빠져나갈 길이 없다.
 *
 * ## 배치를 절대 좌표로 옮기지 않았다
 * 정본은 390×844 기준 절대 배치(로고 y=74 · 카피 y=392 · 버튼 y=614)다. 그대로 두면 화면이
 * 길거나 짧은 기기에서 깨지므로, **로고는 위 · 버튼은 아래 고정**으로 두고 남는 세로를
 * 카피 위아래로 [COPY_TOP_WEIGHT] : 1 로 나눈다. 정본에서 잰 값이다:
 * ```
 * 로고 끝 100 ─ 292 ─ 카피 392..527 ─ 87 ─ 버튼 프레임 614
 * ```
 *
 * ## 진행·오류 표시는 정본에 없다
 * 디자인에 로딩·오류 상태 프레임이 없다. 그렇다고 실패를 삼킬 수는 없어 **최소한으로만** 얹었다 —
 * 진행 중에는 **누른 버튼의** 마크 자리에 인디케이터를 넣고, 오류는 버튼 묶음 위에 한 줄로 띄운다.
 * 디자인이 나오면 이 두 군데를 교체하면 된다.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onGoogleClick: () -> Unit,
    onAppleClick: () -> Unit,
    onKakaoClick: () -> Unit,
    modifier: Modifier = Modifier,
    sessionExpired: Boolean = false
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(DsR.drawable.nm_logo),
                contentDescription = null,
                tint = NmColor.Primary.C500,
                modifier = Modifier.size(27.dp)
            )
            Text(text = "NurseMate", style = Wordmark, color = colors.textPrimary)
        }

        Spacer(modifier = Modifier.weight(COPY_TOP_WEIGHT))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "간호사의 하루를\n조금 더 가볍게",
                style = Headline,
                color = colors.textPrimary
            )
            Text(
                text = "알약 식별부터 처치 타이머까지",
                style = Subhead,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 방금 누른 시도의 실패가 **먼저다.** 지난 만료 안내를 그대로 두면 사용자가 자기
            // 행동의 결과를 못 보고 엉뚱한 이유를 읽는다.
            val notice = state.error?.toMessage() ?: SESSION_EXPIRED.takeIf { sessionExpired }
            if (notice != null) {
                Text(
                    text = notice,
                    style = Notice,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ProviderButton(
                label = "카카오 로그인",
                container = KakaoYellow,
                content = Color.Black,
                enabled = state.pending == null,
                onClick = onKakaoClick
            ) {
                if (state.pending == LoginProvider.Kakao) {
                    ProgressMark(color = Color.Black)
                } else {
                    Icon(
                        painter = painterResource(DsR.drawable.nm_logo_kakao),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            ProviderButton(
                label = "Apple로 로그인",
                container = Color.Black,
                content = Color.White,
                enabled = state.pending == null,
                onClick = onAppleClick
            ) {
                if (state.pending == LoginProvider.Apple) {
                    ProgressMark(color = Color.White)
                } else {
                    Icon(
                        painter = painterResource(DsR.drawable.nm_logo_apple),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            ProviderButton(
                label = "Google로 계속하기",
                container = Color.White,
                content = GoogleTextColor,
                enabled = state.pending == null,
                border = GoogleBorder,
                onClick = onGoogleClick
            ) {
                if (state.pending == LoginProvider.Google) {
                    ProgressMark(color = NmColor.Primary.C500)
                } else {
                    // ⚠️ 4색 브랜드 로고라 tint 하면 안 된다. Icon 이 아니라 Image 로 그린다.
                    Image(
                        painter = painterResource(DsR.drawable.nm_logo_google),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** 소셜 로그인 버튼 — 높이 54, radius-md, 마크 22 자리 + 라벨 16·600, 간격 10. */
@Composable
private fun ProviderButton(
    label: String,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    border: Color? = null,
    mark: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(NmRadius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            // ⚠️ clip 이 clickable 보다 **앞**에 와야 한다. 없으면 눌렀을 때 리플이 둥근 모서리를
            //    무시하고 사각형으로 번져 나간다 — background(shape) 는 배경만 깎을 뿐
            //    자식 인디케이션까지 잘라 주지 않는다.
            .clip(shape)
            .background(container)
            .let { if (border != null) it.border(1.dp, border, shape) else it }
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // 정본의 Mark 는 22×22 고정 칸이다. 로고마다 크기가 19~20으로 달라도
        // 라벨 시작선이 흔들리지 않게 칸을 먼저 잡고 그 안에 그린다.
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) { mark() }
        Spacer(modifier = Modifier.size(10.dp))
        Text(text = label, style = ProviderLabel, color = content)
    }
}

/** 마크 자리(22×22)에 들어가는 진행 인디케이터. 버튼 배경에 묻히지 않게 색만 받는다. */
@Composable
private fun ProgressMark(color: Color) {
    CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
}

private fun AuthError.toMessage(): String = when (this) {
    // 취소는 ViewModel 이 걸러서 여기까지 오지 않는다. when 을 닫기 위해서만 둔다.
    AuthError.Cancelled -> ""

    AuthError.NoCredential -> "기기에 구글 계정이 없어요. 설정에서 계정을 추가해 주세요"

    // 우리 카카오 앱에서 발급된 토큰이 아니거나 만료됐다. 다시 시도하면 새 토큰이 나온다.
    AuthError.KakaoTokenInvalid -> "카카오 로그인에 실패했어요. 다시 시도해 주세요"

    // 카카오·Firebase 쪽 일시 장애라 **구글·애플은 멀쩡하다.** 그 길을 같이 알려 준다 —
    // 「잠시 후 다시」만 말하면 지금 당장 들어갈 방법이 있는데도 기다리게 된다.
    AuthError.ServiceUnavailable ->
        "잠시 연결이 원활하지 않아요. 잠시 후 다시 시도하거나 다른 방법으로 로그인해 주세요"

    is AuthError.Unknown -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요"
}

/**
 * 세션이 강제로 끊겨 돌아온 사람에게만 보인다.
 *
 * 스스로 누른 로그아웃에는 띄우지 않는다 — 방금 한 일을 설명하면 오작동처럼 읽힌다.
 * 가르는 일은 `AppSessionViewModel.sessionExpired` 가 한다.
 */
private const val SESSION_EXPIRED = "로그인 정보가 만료되어 로그아웃했어요. 다시 로그인해 주세요"

/**
 * 카피 위 여백 : 아래 여백 = 292 : 87 (정본 실측). 3.36 : 1.
 *
 * 예전에 2.4 : 1 로 두었더니 카피가 화면 한가운데보다 아래로 내려가 로고와 멀어졌다.
 */
private const val COPY_TOP_WEIGHT = 3.36f

private val KakaoYellow = Color(0xFFFEE500)
private val GoogleBorder = Color(0xFFDADCE0)
private val GoogleTextColor = Color(0xFF1F1F1F)

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
// lineHeight 는 정본의 배수(1.42·1.6)를 fontSize 에 곱한 값이다.
private val Wordmark = NmTypography.title.copy(
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.7).sp
)
private val Headline = NmTypography.heading2.copy(
    fontSize = 34.sp,
    lineHeight = 48.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-1.4).sp
)
private val Subhead = NmTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 24.sp)
private val ProviderLabel = NmTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
private val Notice = NmTypography.body.copy(fontSize = 13.sp)
