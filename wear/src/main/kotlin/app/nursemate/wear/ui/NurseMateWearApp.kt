package app.nursemate.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.nursemate.core.model.TimerState
import app.nursemate.wear.R
import kotlin.math.ceil
import kotlinx.coroutines.launch

/**
 * W1 — 좌우 2페이지: 활성 타이머 ↔ 프리셋 (정본 `타이머 워치 / W1`).
 *
 * ## 정본을 그대로 옮기지 않은 것 셋
 * 정본은 애플워치(396×484px @2x ≈ 198×242pt) 기준인데, 이 워치는 **432×432px / 203dp 원형**
 * 이라 세로가 훨씬 짧고 네 귀퉁이를 못 쓴다.
 * - **헤더의 시계(`9:41`)를 그리지 않는다.** Wear 는 [AppScaffold] 가 곡선 `TimeText` 를
 *   띄운다 — 우리가 그리면 시계가 둘이 된다.
 * - **하단 페이지 도트를 직접 그리지 않는다.** [HorizontalPagerScaffold] 가 원형에 맞춰
 *   곡선으로 배치해 준다.
 * - **목록은 [ScalingLazyColumn]** 이다. 일반 목록은 원형 화면의 위아래 끝에서 잘린다.
 *
 * 글자 크기도 정본 수치(px)를 반으로 나눈 값 대신 Wear Material3 타이포를 쓴다 — 사용자
 * 글꼴 크기 설정을 따라야 하고(WO-V1), 핵심 텍스트 최소 크기 요건이 있다(WO-V14).
 */
@Composable
fun NurseMateWearApp(openPresets: Boolean = false, viewModel: WearTimerViewModel = hiltViewModel()) {
    // 화면을 열 때마다 기기에 남아 있는 마지막 스냅샷을 한 번 당겨온다.
    // 앱이 꺼져 있는 동안 리스너가 못 받았어도 DataItem 은 최신이라, 열자마자 맞는 걸 본다.
    // 옛 값이 와도 `newerOf` 가 무시하므로 되돌아가는 일은 없다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = if (openPresets) PAGE_PRESETS else PAGE_ACTIVE) { PAGE_COUNT }

    // 시작하면 활성 페이지로 돌아간다 — 방금 만든 타이머를 바로 보여 준다.
    LaunchedEffect(Unit) {
        viewModel.startConfirmed.collect { pagerState.animateScrollToPage(PAGE_ACTIVE) }
    }
    val scope = rememberCoroutineScope()

    val navController = rememberSwipeDismissableNavController()

    AppScaffold {
        // 뒤로 가기는 오른쪽 스와이프다 — 워치의 표준 제스처라 뒤로 버튼을 두지 않는다.
        SwipeDismissableNavHost(navController = navController, startDestination = ROUTE_LIST) {
            composable(ROUTE_LIST) { TimerPages(pagerState, viewModel, navController) }
            composable("$ROUTE_DETAIL/{$ARG_TIMER_ID}") { entry ->
                val id = entry.arguments?.getString(ARG_TIMER_ID).orEmpty()
                val timer = timers?.firstOrNull { it.id == id }

                // 다른 데서 끝났으면 목록으로 돌린다.
                // ⚠️ **아직 안 읽었을 때는 나가지 않는다.** 그때도 timer 가 null 이라,
                // 구분하지 않으면 화면이 열리자마자 튕긴다.
                //
                // ⚠️ **울리기 시작해도 목록으로 돌린다.** 이 화면에는 [완료] 가 없고
                // ([TimerDetailScreen]) 만료 뒤의 [일시정지]·[정지] 는 뜻이 없다. 그대로 두면
                // `00:00` 에 진행 중 버튼만 남아 **알람을 끌 길이 없다**(실기기 확인).
                // 만료 조작은 W1 의 만료 카드가 맡는다 — spec §워치.
                //
                // `isExpiredAt` 도 함께 본다. 저장된 상태가 RINGING 으로 오르는 건 알람이
                // 울린 뒤라, 그 사이 잠깐 만료된 채로 남는 창이 있다.
                val expired = timer != null &&
                    (timer.state == TimerState.RINGING || timer.isExpiredAt(now))
                LaunchedEffect(timers, timer, expired) {
                    if (timers != null && timer == null) navController.popBackStack()
                    if (expired) navController.popBackStack()
                }
                timer?.let {
                    TimerDetailScreen(
                        timer = it,
                        now = now,
                        onBack = { navController.popBackStack() },
                        onPauseOrResume = { viewModel.pauseOrResume(it) },
                        onStop = { viewModel.stop(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerPages(
    pagerState: androidx.wear.compose.foundation.pager.PagerState,
    viewModel: WearTimerViewModel,
    navController: androidx.navigation.NavController
) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val tilePrompt by viewModel.tilePrompt.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Box {
        // Scaffold 는 곡선 페이지 인디케이터만 얹는다. 실제 스와이프는 안쪽 Pager 가 한다.
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    PAGE_ACTIVE -> ActivePage(
                        timers = viewModel.ordered(timers.orEmpty(), now),
                        now = now,
                        onComplete = viewModel::complete,
                        onOpen = { navController.navigate("$ROUTE_DETAIL/${it.id}") },
                        onGoToPresets = { scope.launch { pagerState.animateScrollToPage(PAGE_PRESETS) } }
                    )

                    else -> PresetPage(presets = presets, onStart = viewModel::start)
                }
            }
        }

        tilePrompt?.let { TileAddDialog(prompt = it, onDismiss = viewModel::dismissTilePrompt) }
    }
}

@Composable
private fun ActivePage(
    timers: List<app.nursemate.core.model.CareTimer>,
    now: Long,
    onComplete: (app.nursemate.core.model.CareTimer) -> Unit,
    onOpen: (app.nursemate.core.model.CareTimer) -> Unit,
    onGoToPresets: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        if (timers.isEmpty()) {
            EmptyActive(onGoToPresets = onGoToPresets)
        } else {
            ScalingLazyColumn(
                state = listState,
                contentPadding = roundSafe(contentPadding),
                // ⚠️ **자동 가운데 맞춤을 끈다.** 켜 두면 항목이 몇 개 없을 때 목록을 화면
                // 한가운데로 끌어올려 **헤더가 시스템 시계와 겹친다**(실기기에서 확인).
                // 정본도 헤더는 위에 고정이다.
                autoCentering = null,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { ListHeader { Text("타이머", style = WearTimerType.Header) } }
                items(timers, key = { it.id }) { timer ->
                    if (timer.state == TimerState.RINGING) {
                        ExpiredTimerCard(
                            timer = timer,
                            now = now,
                            onComplete = { onComplete(timer) }
                        )
                    } else {
                        RunningTimerCard(timer = timer, now = now, onOpen = { onOpen(timer) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetPage(
    presets: List<app.nursemate.core.model.TimerPreset>,
    onStart: (app.nursemate.core.model.TimerPreset) -> Unit
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = roundSafe(contentPadding),
            autoCentering = null,
            // 정본 `Preset List gap: 12`(애플워치 2x).
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { ListHeader { Text("프리셋", style = WearTimerType.Header) } }
            item {
                // ⚠️ **`fillMaxSize` 를 쓰면 안 된다.** 세로로 스크롤되는 목록 안에서는 높이
                // 제약이 무한이라, 이 한 줄이 화면 전체를 요구해 측정할 때마다 목록 길이가
                // 흔들린다 — 스크롤이 위아래로 튕겼다(실기기 확인).
                Text(
                    text = "누르면 바로 시작됩니다",
                    style = WearTimerType.Hint,
                    color = WearTimerColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(presets, key = { it.id }) { preset ->
                PresetCard(preset = preset, onStart = { onStart(preset) })
            }
        }
    }
}

/**
 * [ScreenScaffold] 가 준 세로 여백에 **가로 곡면 여백을 더한다.**
 *
 * ## 왜 필요한가
 * [ScreenScaffold] 의 `contentPadding` 은 **위아래 몫만** 준다 — 곡선 `TimeText` 와 스크롤
 * 인디케이터가 쓰는 자리다. 좌우는 0 이다. 그래서 카드가 `fillMaxWidth` 로 화면 폭을 다
 * 쓰면, 화면 한가운데라도 **카드의 네 귀퉁이가 원 밖으로 나간다** — 화면은 원인데 카드는
 * 사각형이다. 목록 행의 오른쪽 끝(소요시간)이 그렇게 잘렸고, Play 가
 * 「Wear 앱 품질 가이드라인: 시계 모양」으로 거부했다(2026-09-21).
 *
 * 세로만 보고 [ScalingLazyColumn] 을 고른 것이 놓친 자리다 — 그것은 위아래 끝의 잘림만
 * 막아 준다.
 *
 * ## 왜 고정 dp 가 아닌가
 * 워치 지름이 기기마다 다르다(이 기기는 203dp). 고정값을 두면 큰 화면에서 과하게 좁아진다.
 * **화면 폭의 5.2%** 는 Wear Material3 가 쓰는 값 그대로다
 * (`PaddingDefaults.horizontalContentPaddingPercentage` = 5.2f — 세로는 10f).
 * 그 객체가 `internal` 이라 부를 수 없어 같은 계산을 여기 둔다.
 * 203dp 에서 11dp 가 나오는데, [TimerDetailScreen] 이 실기기 측정으로 따로 고른 값과 같다.
 */
@Composable
private fun roundSafe(vertical: PaddingValues): PaddingValues {
    val horizontal = roundSafeHorizontal(HORIZONTAL_PADDING_FRACTION)
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = vertical.calculateTopPadding(),
        bottom = vertical.calculateBottomPadding()
    )
}

/**
 * 화면 폭의 [fraction] 만큼을 dp 로 준다. 곡면에 먹히지 않을 가로 여백을 정하는 자리다.
 *
 * ⚠️ **고정 dp 로 두면 안 된다.** 워치 지름이 기기마다 다르다 — 이 코드를 처음 쓸 때 기준으로
 * 삼은 기기는 203dp 였고 에뮬레이터는 227dp 다. 203dp 에 맞춰 고정한 값은 더 작은 워치에서
 * 그대로 모자라고, 실제로 Play 가 그렇게 거부했다(2026-09-21 「시계 모양」).
 */
@Composable
internal fun roundSafeHorizontal(fraction: Float): Dp = ceil(LocalConfiguration.current.screenWidthDp * fraction).dp

/** Wear Material3 의 가로 콘텐츠 패딩 비율(5.2%). [roundSafe] 주석 참고. */
internal const val HORIZONTAL_PADDING_FRACTION = 0.052f

/**
 * 정본 `W1 활성 — 빈 상태`. 프리셋 페이지로 유도한다.
 *
 * 아이콘을 **원형 판 위에** 얹는 것까지 정본이다 — 검은 배경에 회색 아이콘만 두면 떠 보인다.
 */
@Composable
private fun EmptyActive(onGoToPresets: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(WearTimerColors.Card, CircleShape)
        ) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_timer),
                contentDescription = null,
                tint = WearTimerColors.Muted,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = "진행 중인 타이머가 없어요",
            style = WearTimerType.EmptyTitle,
            color = WearTimerColors.OnBackground,
            textAlign = TextAlign.Center
        )
        // 정본의 화살표는 "저쪽에 있다"는 표시다. 누를 수 있게 해 두면 한 손으로 바로 넘어간다
        // — 스와이프만 남겨 두면 장갑을 낀 손으로는 잘 안 먹는다.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .clip(HintShape)
                .clickable(onClick = onGoToPresets)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "프리셋에서 시작하세요",
                style = WearTimerType.EmptyHint,
                color = WearTimerColors.PrimarySoft
            )
            Icon(
                painter = painterResource(R.drawable.nm_ic_chevron_right),
                contentDescription = null,
                tint = WearTimerColors.PrimarySoft,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

/** 힌트 탭 영역 모서리. 정본에 없는 값 — 누를 수 있다는 표시만 최소로 준다. */
private val HintShape = RoundedCornerShape(12.dp)

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail"
private const val ARG_TIMER_ID = "timerId"

private const val PAGE_ACTIVE = 0
private const val PAGE_PRESETS = 1
private const val PAGE_COUNT = 2
