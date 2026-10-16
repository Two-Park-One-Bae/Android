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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2ButtonFloating
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerState

/**
 * C1 타이머 리스트 — 정본 `타이머 / C1 리스트`.
 *
 * 타이머 탭의 루트다. 프리셋 시트(C3)까지가 하나의 화면 단위라 여기서 함께 띄운다.
 *
 * @param startPresetId 위젯에서 시작하려다 권한에 막혀 넘어온 프리셋(NM-443). 관문을 열고,
 *                      권한을 받으면 **그 타이머가 그대로 시작된다** — 위젯으로 되돌아갈 필요가 없다.
 */
@Composable
fun TimerListRoute(
    startPresetId: String? = null,
    onStartPresetHandled: () -> Unit = {},
    openPresetSheet: Boolean = false,
    onPresetSheetOpened: () -> Unit = {},
    viewModel: TimerListViewModel = hiltViewModel()
) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val memoEditing by viewModel.memoEditing.collectAsStateWithLifecycle()
    val presetSheet by viewModel.presetSheetOpen.collectAsStateWithLifecycle()
    val gate by viewModel.startGate.state.collectAsStateWithLifecycle()
    val editor = viewModel.presetEditor
    val editing by editor.editing.collectAsStateWithLifecycle()
    val presetForm by editor.form.collectAsStateWithLifecycle()
    val deletingPreset by editor.deleting.collectAsStateWithLifecycle()

    // 홈 `새 타이머` 로 들어오면 프리셋 시트를 연다 — 타이머 탭 추가 버튼과 같은 시트다(spec home).
    LaunchedEffect(openPresetSheet) {
        if (!openPresetSheet) return@LaunchedEffect
        onPresetSheetOpened()
        viewModel.setPresetSheet(true)
    }

    // 프리셋 목록을 읽기 전에는 id 를 맞춰 볼 수 없다. 빈 목록은 "아직 안 읽혔다"와 구분되지
    // 않지만, 정말 비어 있으면 시작할 프리셋 자체가 없어 어느 쪽이든 할 일이 같다.
    LaunchedEffect(startPresetId, presets) {
        val id = startPresetId ?: return@LaunchedEffect
        if (presets.isEmpty()) return@LaunchedEffect
        // 지워진 프리셋이면 시작하지 않되 요청은 소비한다 — 안 그러면 계속 다시 시도한다.
        onStartPresetHandled()
        presets.firstOrNull { it.id == id }?.let(viewModel.startGate::start)
    }

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
            editing = editing,
            onStart = { preset ->
                viewModel.setPresetSheet(false)
                viewModel.startGate.start(preset)
            },
            onToggleEditing = editor::toggleEditing,
            onEditPreset = editor::edit,
            onDeletePreset = editor::askDelete,
            onReorder = editor::reorder,
            onAdd = editor::add,
            onDismiss = {
                viewModel.setPresetSheet(false)
                editor.reset()
            }
        )
    }

    presetForm?.let { form ->
        TimerPresetEditSheet(
            preset = form.preset,
            onSave = editor::save,
            onDelete = form.preset?.let { { editor.askDelete(it) } },
            onDismiss = editor::closeForm
        )
    }

    deletingPreset?.let {
        PresetDeleteDialog(onConfirm = editor::confirmDelete, onCancel = editor::cancelDelete)
    }

    TimerGateHost(
        gate = gate,
        permissions = viewModel.startGate.permissions,
        onAdvance = viewModel.startGate::advance,
        onAsked = viewModel.startGate::markAsked,
        onConfirmAlertMode = viewModel.startGate::confirmAlertMode,
        onDismiss = viewModel.startGate::dismiss,
        onPermissionResult = viewModel::trackAlarmPermission
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
    // 만료를 화면에서도 판정한다.
    //
    // 저장 상태를 RINGING 으로 올리는 건 알람 리시버 몫이지만, 알람이 못 오는 경우가 있다 —
    // 정확 알람 권한이 꺼졌거나, 예약이 실패했거나. 그때 화면만 믿고 있으면 카드가 「진행 중
    // 00:00」으로 굳어 사용자가 손댈 수 없다. 보이는 것만이라도 만료로 바꿔 [완료]를 준다.
    val ordered = remember(timers, now) { CareTimerTransitions.projectedAndOrdered(timers, now) }

    // 바탕(흰색)은 탭 셸(`NmTabScaffold`)이 칠한다 — 탭 바 뒤까지 한 색이어야 해서 거기 한 곳에 둔다.
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ⚠️ **투영본을 넘긴다.** raw 를 세면 알람이 못 온 타이머가 카드로는 만료인데
            // 헤더에는 「진행 중」으로 남아, 투영을 넣은 이유와 앞뒤가 맞지 않는다.
            Header(ordered)
            if (ordered.isEmpty()) {
                TimerListEmpty(onStart = onAdd)
            } else {
                LazyColumn(
                    // ⚠️ **키보드를 여기서 먹는다.** 메모 칸(`TimerCard`)이 이 목록 안에 있는데,
                    // 앱이 edge-to-edge 라 키보드가 떠도 창이 줄지 않는다 — 아무도 ime 를
                    // 먹지 않으면 목록은 제 높이를 유지한 채 키보드가 **그 위를 덮고**, 아래쪽
                    // 카드의 메모 칸은 키보드 뒤로 들어가 보이지 않는다.
                    // 여기서 먹으면 목록이 그만큼 줄어 Compose 가 칸을 스크롤로 올려 준다.
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = ContentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ordered, key = { it.id }) { timer ->
                        if (timer.state == TimerState.RINGING) {
                            ExpiredTimerCard(timer = timer, now = now, onComplete = { onRemove(timer.id) })
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
        NmV2ButtonFloating(
            icon = NmSymbols.Add,
            contentDescription = "타이머 추가",
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
        )
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
        Text("타이머", style = NmV2Type.screenTitle, color = colors.textPrimary)
        // 정본은 빈 상태에서 Count 줄이 통째로 없다 — "진행 중 0"을 보여 주지 않는다.
        if (timers.isNotEmpty()) {
            Text(
                text = timerCountLabel(
                    running = timers.count { it.state == TimerState.RUNNING },
                    paused = timers.count { it.state == TimerState.PAUSED },
                    ringing = timers.count { it.state == TimerState.RINGING }
                ),
                style = NmV2Type.cardBody,
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
                painter = painterResource(NmSymbols.Timer),
                contentDescription = null,
                tint = NmColor.Neutral.C400,
                modifier = Modifier.size(44.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("진행 중인 타이머가 없어요", style = NmV2Type.stateTitle, color = colors.textPrimary)
            Text(
                text = "프리셋을 눌러 바로 시작할 수 있어요",
                style = NmV2Type.cardBody,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
        // 정본은 이 버튼만 볼록이 아니라 `primary-500` 평면이다 — 화면 가운데 하나뿐인 동작이라
        // 그림자 없이도 눈에 띈다. 정본을 따른다.
        Row(
            modifier = Modifier
                .clip(CtaShape)
                .background(NmColor.Primary.C500, CtaShape)
                .clickable(role = Role.Button, onClick = onStart)
                .padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(NmSymbols.Add),
                contentDescription = null,
                tint = NmColor.Neutral.C0,
                modifier = Modifier.size(18.dp)
            )
            Text("타이머 시작하기", style = NmV2Type.button, color = NmColor.Neutral.C0)
        }
    }
}

// 정본 수치 — `타이머 / C1 리스트`
private val ContentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
private val EmptyIconBox = 96.dp
private val CtaShape = RoundedCornerShape(NmRadius.md)
