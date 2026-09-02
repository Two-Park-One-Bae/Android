package app.nursemate.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.nursemate.auth.LoginScreen
import app.nursemate.auth.LoginViewModel
import app.nursemate.consent.ConsentScreen
import app.nursemate.consent.ConsentViewModel
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.User
import app.nursemate.home.FeaturePreparingScreen
import app.nursemate.home.HomeScreen
import app.nursemate.home.NmTab

/**
 * 앱 셸.
 *
 * ⚠️ **여기서 탭바를 그리지 않는다.** 디자인에서 탭바를 가진 화면은 홈·타이머·설정뿐이고
 * 알약 식별 플로우는 전체화면이다. 셸에 깔면 카메라 뷰파인더까지 눌린다.
 * 탭바가 필요한 루트는 각자 [NmTabScaffold] 로 감싼다.
 */
@Composable
fun NurseMateApp(modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    val sessionViewModel: AppSessionViewModel = hiltViewModel()
    val entry by sessionViewModel.entry.collectAsStateWithLifecycle()

    // 포그라운드 복귀마다 회원 정보를 다시 받는다 — 약관이 개정되면 서버가
    // onboardingRequired 를 다시 true 로 주고, 그래야 동의 화면이 뜬다(spec §약관 개정).
    LifecycleResumeEffect(Unit) {
        sessionViewModel.refresh()
        onPauseOrDispose {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
    ) {
        when (entry) {
            // 세션 복원·회원 조회 전에는 **아무것도 그리지 않는다.** 그리면 로그인 화면이 한 번
            // 번쩍였다가 홈으로 바뀐다. Firebase 의 첫 콜백은 디스크에서 읽는 것이라 수십 ms 다.
            AppEntry.Loading -> Unit

            AppEntry.Unavailable -> ServiceUnavailable(onRetry = sessionViewModel::refresh)

            else -> NmNavHost(entry, sessionViewModel::onUserUpdated)
        }
    }
}

/**
 * 로그인은 됐는데 회원 정보를 못 받았을 때.
 *
 * 정본에 없는 화면이다. 없으면 스플래시에 갇히므로 최소한으로 얹었다 — 스펙(§오류 처리)이
 * 이 상황에서 **로그아웃시키지 말라**고 하므로 로그인 화면으로 보내지 않는다.
 */
@Composable
private fun ServiceUnavailable(onRetry: () -> Unit) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(NmSpacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "연결이 원활하지 않아요\n잠시 후 다시 시도해 주세요",
            style = NmTypography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        NmButtonSecondary(text = "다시 시도", onClick = onRetry)
    }
}

@Composable
private fun NmNavHost(entry: AppEntry, onUserUpdated: (User) -> Unit) {
    val navController = rememberNavController()

    // 첫 화면은 진입 상태로 **한 번만** 정한다. 이후 변화는 아래 LaunchedEffect 가 처리한다 —
    // startDestination 을 상태에 묶으면 값이 바뀔 때 NavHost 가 통째로 다시 만들어진다.
    val startDestination = remember { entry.route ?: NmRoute.LOGIN }

    // 스펙(feature/auth §진입 라우팅)의 세 갈래 — 로그인 · 동의 온보딩 · 홈.
    // 탭 이동은 홈 안에서 일어나므로, 이미 홈 그래프 안에 있으면 건드리지 않는다.
    LaunchedEffect(entry) {
        val target = entry.route ?: return@LaunchedEffect
        val current = navController.currentDestination?.route ?: return@LaunchedEffect
        val settled = when (target) {
            // 홈은 탭 넷 중 어디에 있어도 "홈에 있는" 것이다. 여기서 되돌리면 탭 이동이 씹힌다.
            NmRoute.HOME -> current in HOME_GRAPH

            else -> current == target
        }
        if (!settled) navController.replaceWith(target)
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(NmRoute.LOGIN) {
            val viewModel: LoginViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val activity = LocalActivity.current
            LoginScreen(
                state = state,
                // 자격 증명 선택 UI 를 띄우려면 **Activity** 컨텍스트여야 한다.
                // Compose 의 LocalContext 는 호스팅 Activity 를 준다.
                // 카카오도 마찬가지다 — 카카오톡 전환·웹 로그인 모두 Activity 를 요구한다.
                onGoogleClick = { viewModel.signInWithGoogle(context) },
                // 애플만 Context 가 아니라 Activity 자체를 요구한다 — Firebase 가 웹 플로우를
                // 직접 띄우기 때문이다. Compose 밖(프리뷰 등)에서는 null 이라 그때는 아무 일도
                // 하지 않는다. 실기기에서는 항상 있다.
                onAppleClick = { activity?.let(viewModel::signInWithApple) },
                onKakaoClick = { viewModel.signInWithKakao(context) }
            )
        }

        composable(NmRoute.CONSENT) {
            val viewModel: ConsentViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            ConsentScreen(
                state = state,
                onToggle = viewModel::toggle,
                onToggleAll = viewModel::toggleAll,
                // 저장 응답의 회원 정보를 셸로 올린다. onboardingRequired 가 false 로 바뀌면서
                // 진입 상태가 홈으로 넘어간다 — 화면이 스스로 이동하지 않는다.
                onSubmit = { viewModel.submit(onUserUpdated) },
                onCancel = viewModel::cancel,
                onOpenPolicy = { context.openPolicy(it.policyUrl) },
                onRetry = viewModel::load
            )
        }

        composable(NmRoute.HOME) {
            TabRoot(navController, NmTab.Home) {
                HomeScreen(
                    onPillClick = { navController.switchTab(NmTab.Pill) },
                    onTimerClick = { navController.switchTab(NmTab.Timer) },
                    onActiveTimerClick = { navController.switchTab(NmTab.Timer) }
                )
            }
        }

        // 알약 식별 그래프는 NM-394 에서 이 자리를 대체한다.
        composable(NmRoute.PILL) {
            TabRoot(navController, NmTab.Pill) {
                FeaturePreparingScreen(
                    title = "알약 식별",
                    description = "사진으로 알약을 찾아주는 기능을 준비하고 있어요.\n" +
                        "테스트 기간 중 업데이트로 제공될 예정입니다.",
                    icon = painterResource(DsR.drawable.nm_ic_pill),
                    iconBackground = NmColor.Primary.C50,
                    iconTint = NmColor.Primary.C500
                )
            }
        }

        composable(NmRoute.TIMER) {
            TabRoot(navController, NmTab.Timer) {
                FeaturePreparingScreen(
                    title = "처치 타이머",
                    description = "여러 처치 시간을 한 번에 관리하는 타이머를 준비하고 있어요.\n" +
                        "테스트 기간 중 업데이트로 제공될 예정입니다.",
                    icon = painterResource(DsR.drawable.nm_ic_timer),
                    iconBackground = NmColor.Secondary.C50,
                    iconTint = NmColor.Secondary.C500
                )
            }
        }

        // 설정 화면의 내용(로그아웃·탈퇴, 타이머 울림 방식)은 인증·타이머 스펙에 딸려 있다.
        // 그 기능들이 붙기 전까지는 임의로 채우지 않는다 — spec/feature/auth·care-timer 참고.
        composable(NmRoute.SETTINGS) {
            TabRoot(navController, NmTab.Settings) {
                FeaturePreparingScreen(
                    title = "설정",
                    description = "계정과 알림 설정을 준비하고 있어요.\n" +
                        "테스트 기간 중 업데이트로 제공될 예정입니다.",
                    icon = painterResource(DsR.drawable.nm_ic_settings),
                    iconBackground = NmColor.Neutral.C100,
                    iconTint = NmColor.Neutral.C500
                )
            }
        }
    }
}

/** 진입 상태가 대응하는 경로. 화면을 그리지 않는 상태(로딩·오류)는 null 이다. */
private val AppEntry.route: String?
    get() = when (this) {
        AppEntry.Login -> NmRoute.LOGIN
        AppEntry.Consent -> NmRoute.CONSENT
        AppEntry.Home -> NmRoute.HOME
        AppEntry.Loading, AppEntry.Unavailable -> null
    }

/** 탭 넷은 모두 "홈에 있는" 상태다. */
private val HOME_GRAPH = setOf(NmRoute.HOME, NmRoute.PILL, NmRoute.TIMER, NmRoute.SETTINGS)

/**
 * 진입 화면을 바꾼다. **백스택을 통째로 비운다.**
 *
 * 로그인·동의·홈은 뒤로가기로 서로 오갈 수 있으면 안 된다 — 동의 화면에서 뒤로 눌러 홈이
 * 나오면 스펙(동의 없이 홈으로 가는 경로 없음)이 깨지고, 로그아웃 후 이전 계정의 화면이
 * 남으면 병동 공용 기기에서 그대로 노출된다.
 */
private fun NavController.replaceWith(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
    }
}

/**
 * 약관 전문을 기본 브라우저로 연다.
 *
 * Custom Tab 이 앱 안에 머무는 만큼 UX 는 낫지만 `androidx.browser` 의존성이 는다.
 * 지금은 '보기' 한 곳뿐이라 기본 브라우저로 둔다.
 */
private fun Context.openPolicy(url: String) {
    // 브라우저가 없는 기기는 사실상 없지만, 없다고 앱이 죽으면 동의를 못 끝낸다.
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** 탭바를 두르는 루트 화면. 전체화면 플로우(알약 촬영 등)는 이걸 쓰지 않는다. */
@Composable
private fun TabRoot(navController: NavController, tab: NmTab, content: @Composable () -> Unit) {
    NmTabScaffold(
        selected = tab,
        onSelect = navController::switchTab,
        content = content
    )
}

/**
 * 탭 전환.
 *
 * 탭을 오갈 때 각 탭의 화면 스택을 보존한다(`saveState`/`restoreState`).
 */
internal fun NavController.switchTab(tab: NmTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

internal val NmTab.route: String
    get() = when (this) {
        NmTab.Home -> NmRoute.HOME
        NmTab.Pill -> NmRoute.PILL
        NmTab.Timer -> NmRoute.TIMER
        NmTab.Settings -> NmRoute.SETTINGS
    }
