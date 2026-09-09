package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerState

/**
 * C1 타이머 리스트 — 정본 `타이머 / C1 리스트`.
 *
 * 타이머 탭의 루트다. 프리셋 시트(C3)까지가 하나의 화면 단위라 여기서 함께 띄운다.
 */
@Composable
fun TimerListRoute(viewModel: TimerListViewModel = hiltViewModel()) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val memoEditing by viewModel.memoEditing.collectAsStateWithLifecycle()
    val presetSheet by viewModel.presetSheetOpen.collectAsStateWithLifecycle()
    val gate by viewModel.startGate.state.collectAsStateWithLifecycle()

    TimerListScreen(
        timers = timers,
        now = now,
        memoEditing = memoEditing,
        onAdd = { viewModel.setPresetSheet(true) },
        onPauseOrResume = viewModel::pauseOrResume,
        onExtend = viewModel::extend,
        onMemoToggle = viewModel::toggleMemo,
        onMemoSave = viewModel::saveMemo,
        onRemove = viewModel::remove
    )

    if (presetSheet) {
        TimerPresetSheet(
            presets = presets,
            onStart = { preset ->
                viewModel.setPresetSheet(false)
                viewModel.startGate.start(preset)
            },
            onDismiss = { viewModel.setPresetSheet(false) }
        )
    }

    TimerGateHost(
        gate = gate,
        permissions = viewModel.startGate.permissions,
        onAdvance = viewModel.startGate::advance,
        onAsked = viewModel.startGate::markAsked,
        onConfirmAlertMode = viewModel.startGate::confirmAlertMode,
        onDismiss = viewModel.startGate::dismiss
    )
}

@Composable
fun TimerListScreen(
    timers: List<CareTimer>,
    now: Long,
    memoEditing: String?,
    onAdd: () -> Unit,
    onPauseOrResume: (CareTimer) -> Unit,
    onExtend: (String) -> Unit,
    onMemoToggle: (String) -> Unit,
    onMemoSave: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    // 만료를 화면에서도 판정한다.
    //
    // 저장 상태를 RINGING 으로 올리는 건 알람 리시버 몫이지만, 알람이 못 오는 경우가 있다 —
    // 정확 알람 권한이 꺼졌거나, 예약이 실패했거나. 그때 화면만 믿고 있으면 카드가 「진행 중
    // 00:00」으로 굳어 사용자가 손댈 수 없다. 보이는 것만이라도 만료로 바꿔 [완료]를 준다.
    val ordered = remember(timers, now) {
        CareTimerTransitions.ordered(timers, now).map { timer ->
            if (timer.isExpiredAt(now)) CareTimerTransitions.ring(timer) else timer
        }
    }

    Box(modifier.fillMaxSize().background(colors.bgApp)) {
        Column(Modifier.fillMaxSize()) {
            Header(timers)
            if (ordered.isEmpty()) {
                TimerListEmpty(onStart = onAdd)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = ContentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ordered, key = { it.id }) { timer ->
                        if (timer.state == TimerState.RINGING) {
                            ExpiredTimerCard(timer = timer, onComplete = { onRemove(timer.id) })
                        } else {
                            TimerCard(
                                timer = timer,
                                now = now,
                                memoEditing = memoEditing == timer.id,
                                onPauseOrResume = { onPauseOrResume(timer) },
                                onExtend = { onExtend(timer.id) },
                                onMemoToggle = { onMemoToggle(timer.id) },
                                onMemoSave = { memo -> onMemoSave(timer.id, memo) },
                                onStop = { onRemove(timer.id) }
                            )
                        }
                    }
                }
            }
        }

        // 정본은 FAB 을 탭바 위 16 에 띄운다. 탭바는 이 화면 밖(NmTabScaffold)이라
        // 여기서는 콘텐츠 하단 기준 16 이면 같은 자리가 된다.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .shadow(FabElevation, CircleShape, spotColor = FabShadow, ambientColor = FabShadow)
                .size(FabSize)
                .clip(CircleShape)
                .background(NmColor.Primary.C500, CircleShape)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_plus),
                contentDescription = "타이머 추가",
                tint = NmColor.Neutral.C0,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun Header(timers: List<CareTimer>) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("타이머", style = TitleStyle, color = colors.textPrimary)
        // 정본은 빈 상태에서 Count 줄이 통째로 없다 — "진행 중 0"을 보여 주지 않는다.
        if (timers.isNotEmpty()) {
            Text(
                text = timerCountLabel(
                    running = timers.count { it.state == TimerState.RUNNING },
                    paused = timers.count { it.state == TimerState.PAUSED },
                    ringing = timers.count { it.state == TimerState.RINGING }
                ),
                style = CountStyle,
                color = colors.textSecondary
            )
        }
    }
}

/** 빈 상태 — 정본 `타이머 / C1 리스트 — 빈 상태`. FAB 과 별개로 큰 CTA 를 가운데 둔다. */
@Composable
private fun TimerListEmpty(onStart: () -> Unit) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ContentPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(EmptyIconBox)
                .clip(CircleShape)
                .background(NmColor.Neutral.C100, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_timer),
                contentDescription = null,
                tint = NmColor.Neutral.C400,
                modifier = Modifier.size(44.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("진행 중인 타이머가 없어요", style = EmptyTitle, color = colors.textPrimary)
            Text(
                text = "프리셋을 눌러 바로 시작할 수 있어요",
                style = EmptySub,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
        Row(
            modifier = Modifier
                .clip(CtaShape)
                .background(NmColor.Primary.C500, CtaShape)
                .clickable(onClick = onStart)
                .padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_plus),
                contentDescription = null,
                tint = NmColor.Neutral.C0,
                modifier = Modifier.size(18.dp)
            )
            Text("타이머 시작하기", style = CtaStyle, color = NmColor.Neutral.C0)
        }
    }
}

// 정본 수치 — `타이머 / C1 리스트`
private val ContentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
private val FabSize = 56.dp
private val FabElevation = 8.dp
private val FabShadow = Color(0x660284C7)
private val EmptyIconBox = 96.dp
private val CtaShape = RoundedCornerShape(12.dp)

private val TitleStyle = NmTypography.heading2.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
private val CountStyle = NmTypography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal)
private val EmptyTitle = NmTypography.title.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
private val EmptySub = NmTypography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal)
private val CtaStyle = NmTypography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
