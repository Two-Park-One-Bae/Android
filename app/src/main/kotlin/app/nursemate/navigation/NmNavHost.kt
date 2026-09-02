package app.nursemate.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.nursemate.auth.LoginScreen
import app.nursemate.auth.LoginViewModel
import app.nursemate.core.data.auth.AuthSession
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.R as DsR
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
    val session by sessionViewModel.session.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
    ) {
        // 세션 복원 전에는 **아무것도 그리지 않는다.** 그리면 로그인 화면이 한 번 번쩍였다가
        // 홈으로 바뀐다. Firebase 의 첫 콜백은 디스크에서 읽는 것이라 수십 ms 안에 온다.
        if (session != AuthSession.Unknown) {
            NmNavHost(session)
        }
    }
}

@Composable
private fun NmNavHost(session: AuthSession) {
    val navController = rememberNavController()

    // 첫 화면은 복원된 세션으로 **한 번만** 정한다. 이후의 로그인·로그아웃은 아래 LaunchedEffect 가
    // 처리한다 — startDestination 을 세션에 묶으면 값이 바뀔 때 NavHost 가 통째로 다시 만들어진다.
    val startDestination = remember { if (session is AuthSession.SignedIn) NmRoute.HOME else NmRoute.LOGIN }

    // 스펙(feature/auth §진입 라우팅)은 세션으로 화면을 가른다. 지금은 세션 유무까지만 본다 —
    // `onboardingRequired`(동의 온보딩 분기)는 GET /users/me 가 붙는 NM-392 이후다.
    LaunchedEffect(session) {
        val current = navController.currentDestination?.route
        when (session) {
            is AuthSession.SignedIn ->
                if (current == NmRoute.LOGIN) navController.enterHome()

            AuthSession.SignedOut ->
                if (current != null && current != NmRoute.LOGIN) navController.returnToLogin()

            AuthSession.Unknown -> Unit
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(NmRoute.LOGIN) {
            val viewModel: LoginViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LoginScreen(
                state = state,
                // 자격 증명 선택 UI 를 띄우려면 **Activity** 컨텍스트여야 한다.
                // Compose 의 LocalContext 는 호스팅 Activity 를 준다.
                onGoogleClick = { viewModel.signInWithGoogle(context) }
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

/** 로그인을 지나 홈으로. 뒤로가기로 로그인에 돌아오지 못하게 스택에서 뺀다. */
private fun NavController.enterHome() {
    navigate(NmRoute.HOME) {
        popUpTo(NmRoute.LOGIN) { inclusive = true }
    }
}

/**
 * 로그아웃·세션 만료로 로그인 화면으로 돌아간다.
 *
 * `popUpTo(0)`으로 **백스택을 통째로 비운다** — 남겨 두면 뒤로가기로 이전 계정의 화면이
 * 다시 보인다. 병동 공용 기기를 전제하므로 남기면 안 된다.
 */
private fun NavController.returnToLogin() {
    navigate(NmRoute.LOGIN) {
        popUpTo(0) { inclusive = true }
    }
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
