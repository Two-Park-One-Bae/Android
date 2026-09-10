package app.nursemate.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.nursemate.core.model.TimerState
import app.nursemate.wear.R

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
fun NurseMateWearApp(viewModel: WearTimerViewModel = hiltViewModel()) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState { PAGE_COUNT }

    AppScaffold {
        // Scaffold 는 곡선 페이지 인디케이터만 얹는다. 실제 스와이프는 안쪽 Pager 가 한다.
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    PAGE_ACTIVE -> ActivePage(
                        timers = snapshot?.let { viewModel.ordered(it, now) }.orEmpty(),
                        now = now,
                        pending = pending,
                        onComplete = viewModel::complete
                    )

                    else -> PresetPage(
                        presets = snapshot?.presets.orEmpty(),
                        pending = pending,
                        onStart = viewModel::start
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivePage(
    timers: List<app.nursemate.core.model.CareTimer>,
    now: Long,
    pending: String?,
    onComplete: (app.nursemate.core.model.CareTimer) -> Unit
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        if (timers.isEmpty()) {
            EmptyActive()
        } else {
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                // ⚠️ **자동 가운데 맞춤을 끈다.** 켜 두면 항목이 몇 개 없을 때 목록을 화면
                // 한가운데로 끌어올려 **헤더가 시스템 시계와 겹친다**(실기기에서 확인).
                // 정본도 헤더는 위에 고정이다.
                autoCentering = null,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { ListHeader { Text("타이머") } }
                items(timers, key = { it.id }) { timer ->
                    if (timer.state == TimerState.RINGING) {
                        ExpiredTimerCard(
                            timer = timer,
                            pending = pending == timer.id,
                            onComplete = { onComplete(timer) }
                        )
                    } else {
                        RunningTimerCard(timer = timer, now = now)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetPage(
    presets: List<app.nursemate.core.model.TimerPreset>,
    pending: String?,
    onStart: (app.nursemate.core.model.TimerPreset) -> Unit
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            autoCentering = null,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { ListHeader { Text("프리셋") } }
            item {
                Text(
                    text = "누르면 바로 시작됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = WearTimerColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize()
                )
            }
            items(presets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    pending = pending == preset.id,
                    onStart = { onStart(preset) }
                )
            }
        }
    }
}

/** 정본 `W1 활성 — 빈 상태`. 프리셋 페이지로 유도한다. */
@Composable
private fun EmptyActive() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.nm_ic_timer),
            contentDescription = null,
            tint = WearTimerColors.Muted,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = "진행 중인 타이머가 없어요",
            style = MaterialTheme.typography.bodyMedium,
            color = WearTimerColors.OnBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "프리셋에서 시작하세요 ›",
            style = MaterialTheme.typography.bodySmall,
            color = WearTimerColors.PrimarySoft,
            textAlign = TextAlign.Center
        )
    }
}

private const val PAGE_ACTIVE = 0
private const val PAGE_COUNT = 2
