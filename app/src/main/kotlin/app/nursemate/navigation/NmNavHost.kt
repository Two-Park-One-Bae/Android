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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.Usage
import app.nursemate.core.model.User
import app.nursemate.home.HomeScreen
import app.nursemate.home.HomeViewModel
import app.nursemate.home.NmTab
import app.nursemate.pill.PillLimitAlert
import app.nursemate.settings.SettingsConfirm
import app.nursemate.settings.SettingsConfirmDialog
import app.nursemate.settings.SettingsScreen
import app.nursemate.settings.SettingsViewModel
import app.nursemate.timer.TimerListRoute

/**
 * 앱 셸.
 *
 * ⚠️ **여기서 탭바를 그리지 않는다.** 디자인에서 탭바를 가진 화면은 홈·타이머·설정뿐이고
 * 알약 식별 플로우는 전체화면이다. 셸에 깔면 카메라 뷰파인더까지 눌린다.
 * 탭바가 필요한 루트는 각자 [NmTabScaffold] 로 감싼다.
 */
@Composable
fun NurseMateApp(
    openTimerTab: Boolean = false,
    onTimerTabOpened: () -> Unit = {},
    startPresetId: String? = null,
    onStartPresetHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    val sessionViewModel: AppSessionViewModel = hiltViewModel()
    val entry by sessionViewModel.entry.collectAsStateWithLifecycle()
    val sessionExpired by sessionViewModel.sessionExpired.collectAsStateWithLifecycle()
    val needsReconsent by sessionViewModel.needsReconsent.collectAsStateWithLifecycle()

    // ⚠️ 여기서 포그라운드 복귀마다 회원 정보를 다시 받던 것을 걷어냈다(NM-463).
    //    재동의 판정 시점은 **앱 실행 때**다(spec §진입 라우팅 「포그라운드 복귀에는 다시
    //    판정하지 않는다」). 복귀마다 받으면 잠깐 다른 앱을 보고 돌아온 사용자가 쓰던 화면에서
    //    동의 시트로 끌려 나온다. 세션 복원이 끝나면 init 의 session collect 가 한 번 받는다.

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

            else -> NmNavHost(
                entry = entry,
                sessionExpired = sessionExpired,
                openTimerTab = openTimerTab,
                onTimerTabOpened = onTimerTabOpened,
                startPresetId = startPresetId,
                onStartPresetHandled = onStartPresetHandled,
                onUserUpdated = sessionViewModel::onUserUpdated,
                needsReconsent = needsReconsent
            )
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
private fun NmNavHost(
    entry: AppEntry,
    sessionExpired: Boolean,
    openTimerTab: Boolean,
    onTimerTabOpened: () -> Unit,
    startPresetId: String?,
    onStartPresetHandled: () -> Unit,
    onUserUpdated: (User) -> Unit,
    needsReconsent: Boolean
) {
    val navController = rememberNavController()

    // 알약 탭 게이트를 홈·타이머·설정 세 화면이 공유해야 한다 — 화면마다 따로 물으면
    // 서로 다른 값을 볼 수 있다(spec §게이트 위치: "홈 '알약 식별' 카드/탭"). HomeViewModel
    // 이 이미 UsageHolder 를 감싼 얇은 창이라 새 클래스를 만들지 않고 이 레벨로 끌어올린다.
    val homeViewModel: HomeViewModel = hiltViewModel()
    val homeUsage by homeViewModel.usage.collectAsStateWithLifecycle()
    val activeTimerCount by homeViewModel.activeTimerCount.collectAsStateWithLifecycle()
    // 어느 탭에 있든 최신값을 본다 — Home 화면을 아직 안 들렀으면 usage 가 null 인 채로
    // 게이트를 통과시켜 버릴 수 있다(§한도 도달 플로우: 모르면 막지 않는다는 원칙과는 별개로,
    // 알 수 있으면 최대한 안다).
    //
    // ⚠️ **entry == Home 일 때만** 부른다. NmNavHost 는 Login·Consent 상태에서도 그려지는데,
    // 가드 없이 부르면 로그인하기도 전에 인증이 필요한 API(/pill-attributes/usage)를 쳐서
    // 401 로그만 남기고 아무 소용이 없다 — 홈에 닿은 뒤(entry 는 Login·Consent·Home 세
    // 상태만 오간다, §진입 라우팅)의 탭 내부 이동으로는 entry 자체가 안 바뀌어 다시 타지
    // 않으니 매 화면 전환마다 불필요하게 재조회하지도 않는다.
    LifecycleResumeEffect(entry) {
        if (entry == AppEntry.Home) homeViewModel.refresh()
        onPauseOrDispose {}
    }
    var pillTabLimitReached by remember { mutableStateOf(false) }

    // 첫 화면은 진입 상태로 **한 번만** 정한다. 이후 변화는 아래 LaunchedEffect 가 처리한다 —
    // startDestination 을 상태에 묶으면 값이 바뀔 때 NavHost 가 통째로 다시 만들어진다.
    val startDestination = remember { entry.route ?: NmRoute.LOGIN }

    // 딥링크로 열렸어도 세션이 풀리기 전에는 이 NavHost 가 아직 없다. 자동 처리는 그래프를
    // 세우는 그 순간에만 돌아서, 늦게 만들어진 컨트롤러에는 인텐트가 닿지 않는다 — 한 번 직접
    // 넘겨준다. 처리된 인텐트에는 표시가 남아 되풀이되지 않는다.
    val deepLinkActivity = LocalActivity.current
    LaunchedEffect(navController) { deepLinkActivity?.intent?.let(navController::handleDeepLink) }

    // 스펙(feature/auth §진입 라우팅)의 세 갈래 — 로그인 · 동의 온보딩 · 홈.
    // 홈에 닿은 뒤의 화면 이동은 각 화면이 알아서 한다 — 셸이 개입하지 않는다.
    LaunchedEffect(entry) {
        val target = entry.route ?: return@LaunchedEffect
        val current = navController.currentDestination?.route ?: return@LaunchedEffect
        val settled = when (target) {
            // 일단 홈에 닿은 뒤의 이동(탭 전환·알약 플로우)은 셸이 되돌리지 않는다.
            // 목록으로 두면 화면이 늘 때마다 여기를 고쳐야 하고, 빠뜨리면 사용자가 튕긴다.
            NmRoute.HOME -> current != NmRoute.LOGIN && current != NmRoute.CONSENT

            else -> current == target
        }
        if (!settled) navController.replaceWith(target)
    }

    AlarmLanding(
        navController = navController,
        entry = entry,
        requested = openTimerTab,
        onHandled = onTimerTabOpened
    )

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
                onKakaoClick = { viewModel.signInWithKakao(context) },
                // 내 의사와 무관하게 끊겨 돌아온 것이면 이유를 알린다. 스스로 누른
                // 로그아웃에는 뜨지 않는다 — 가르는 일은 AppSessionViewModel 이 한다.
                sessionExpired = sessionExpired
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
                onRetry = viewModel::load,
                // 개정 재동의면 시트 앞에 안내를 한 장 세운다 — 최초 가입자에게는 띄우지 않는다.
                needsReconsent = needsReconsent
            )
        }

        composable(NmRoute.HOME) {
            // 한도에 걸렸으면 촬영으로 보내지 않고 안내만 한다(spec §한도 도달 플로우).
            // 카드 탭 전용 — 하단 탭바 쪽은 pillTabLimitReached(TabRoot 공통 게이트)가 맡는다.
            var cardLimitReached by remember { mutableStateOf(false) }

            TabRoot(
                navController = navController,
                tab = NmTab.Home,
                usage = homeUsage,
                blocked = homeViewModel::blocked,
                pillTabLimitReached = pillTabLimitReached,
                onPillTabLimitReached = { pillTabLimitReached = true },
                onDismissPillTabLimit = { pillTabLimitReached = false },
                overlay = if (cardLimitReached) {
                    { PillLimitAlert(usage = homeUsage, onConfirm = { cardLimitReached = false }) }
                } else {
                    null
                }
            ) {
                HomeScreen(
                    usage = homeUsage,
                    activeTimerCount = activeTimerCount,
                    onPillClick = {
                        if (homeViewModel.blocked()) cardLimitReached = true else navController.switchTab(NmTab.Pill)
                    },
                    onTimerClick = { navController.switchTab(NmTab.Timer) },
                    onActiveTimerClick = { navController.switchTab(NmTab.Timer) }
                )
            }
        }

        pillNavGraph(navController)

        composable(NmRoute.TIMER) {
            TabRoot(
                navController = navController,
                tab = NmTab.Timer,
                usage = homeUsage,
                blocked = homeViewModel::blocked,
                pillTabLimitReached = pillTabLimitReached,
                onPillTabLimitReached = { pillTabLimitReached = true },
                onDismissPillTabLimit = { pillTabLimitReached = false }
            ) {
                TimerListRoute(startPresetId = startPresetId, onStartPresetHandled = onStartPresetHandled)
            }
        }

        // 타이머 울림 방식은 아직 없다 — 처치 타이머(NM-308)에 딸린 설정이라 그때 함께 온다.
        composable(NmRoute.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val alertMode by viewModel.alertMode.collectAsStateWithLifecycle()
            // 확인 모달은 탭바까지 덮어야 해서 화면 밖(scaffold overlay)에 그린다.
            var confirming by remember { mutableStateOf<SettingsConfirm?>(null) }

            TabRoot(
                navController = navController,
                tab = NmTab.Settings,
                usage = homeUsage,
                blocked = homeViewModel::blocked,
                pillTabLimitReached = pillTabLimitReached,
                onPillTabLimitReached = { pillTabLimitReached = true },
                onDismissPillTabLimit = { pillTabLimitReached = false },
                overlay = confirming?.let { pending ->
                    {
                        SettingsConfirmDialog(
                            confirm = pending,
                            // 로그아웃·탈퇴 모두 화면을 직접 옮기지 않는다.
                            // 세션이 끊기면 셸이 로그인으로 보낸다.
                            onConfirmed = {
                                confirming = null
                                when (pending) {
                                    SettingsConfirm.SignOut -> viewModel.signOut()
                                    SettingsConfirm.Delete -> viewModel.deleteAccount()
                                }
                            },
                            onDismiss = { confirming = null }
                        )
                    }
                }
            ) {
                SettingsScreen(
                    state = state,
                    onConfirm = { confirming = it },
                    alertMode = alertMode,
                    onAlertMode = viewModel::setAlertMode
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

/**
 * 만료 알람을 탭해서 들어왔으면 타이머 탭까지 이어서 보낸다 —
 * spec §만료·알람 "알람 확인·탭 후 랜딩 = C1".
 *
 * 홈에 닿은 **뒤**에 움직여야 한다. 알람은 잠금화면에서도 눌리므로, 그때 앱은 아직
 * 로그인이나 동의 화면일 수 있다.
 */
@Composable
private fun AlarmLanding(navController: NavController, entry: AppEntry, requested: Boolean, onHandled: () -> Unit) {
    LaunchedEffect(entry, requested) {
        if (!requested || entry != AppEntry.Home) return@LaunchedEffect
        navController.switchTab(NmTab.Timer)
        onHandled()
    }
}

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

/**
 * 탭바를 두르는 루트 화면. 전체화면 플로우(알약 촬영 등)는 이걸 쓰지 않는다.
 *
 * ## 알약 탭 게이트가 여기 있는 이유
 * spec §게이트 위치: "홈 '알약 식별' 카드**·탭** … 탭은 항상 가능하고, 0회일 때 탭하면
 * 팝업으로 응답한다." 카드는 화면마다 다르지만 **하단 탭바는 홈·타이머·설정 셋이 공유하는
 * 한 자리**라, 게이트도 이 공통 지점 하나에 둔다 — 화면마다 따로 걸면 빠뜨리는 곳이 생긴다.
 *
 * 호출부가 이미 자기 사정의 [overlay](확인 모달 등)를 쓰고 있을 수 있어 두 알럿이 동시에
 * 필요할 일이 없다는 전제로 [pillTabLimitReached] 를 우선 그린다 — 한도 알럿이 뜬 상태에서
 * 탈퇴 확인 같은 걸 새로 열 수 있는 조작 자체가 없다.
 */
@Composable
private fun TabRoot(
    navController: NavController,
    tab: NmTab,
    usage: Usage?,
    blocked: () -> Boolean,
    pillTabLimitReached: Boolean,
    onPillTabLimitReached: () -> Unit,
    onDismissPillTabLimit: () -> Unit,
    overlay: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    NmTabScaffold(
        selected = tab,
        onSelect = { target ->
            if (target == NmTab.Pill && blocked()) onPillTabLimitReached() else navController.switchTab(target)
        },
        overlay = if (pillTabLimitReached) {
            { PillLimitAlert(usage = usage, onConfirm = onDismissPillTabLimit) }
        } else {
            overlay
        },
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
        NmTab.Pill -> NmRoute.PILL_GRAPH
        NmTab.Timer -> NmRoute.TIMER
        NmTab.Settings -> NmRoute.SETTINGS
    }
