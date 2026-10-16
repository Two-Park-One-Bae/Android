package app.nursemate.timer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2ButtonShape
import app.nursemate.core.designsystem.v2.NmV2RaisedButton
import app.nursemate.core.designsystem.v2.NmV2Tag
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * C3 프리셋 시트 — 정본 `타이머 / C3 프리셋 시트` · `— 편집 모드`.
 *
 * ## 누르면 곧바로 시작한다
 * 정본 부제가 "누르면 타이머가 바로 시작됩니다"다. 확인 단계를 넣지 않는다 — 처치 중에
 * 한 손으로 쓰는 화면이라 탭 수가 곧 비용이다. 잘못 눌러도 카드에서 바로 정지할 수 있다.
 *
 * ## 편집 모드는 같은 시트를 갈아입힌다
 * 정본이 프레임을 둘로 그렸지만 목록·행 생김새가 같고 부제와 행 양끝만 바뀐다.
 * 화면을 새로 띄우면 "어디로 갔지"가 생기므로 자리에서 바꾼다.
 *
 * ⚠️ 편집 모드에서 행을 눌러도 **시작하지 않는다** — 수정 시트가 열린다.
 */
@Composable
fun TimerPresetSheet(
    presets: List<TimerPreset>,
    editing: Boolean,
    onStart: (TimerPreset) -> Unit,
    onToggleEditing: () -> Unit,
    onEditPreset: (TimerPreset) -> Unit,
    onDeletePreset: (TimerPreset) -> Unit,
    onReorder: (List<TimerPreset>) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NmTheme.semanticColors
    // 순서를 끄는 동안은 시트와 목록 스크롤을 멈춘다 — 안 그러면 손잡이를 아래로 끌 때
    // 행이 아니라 시트가 따라 내려가 닫혀 버린다.
    var reordering by remember { mutableStateOf(false) }
    val listScroll = rememberScrollState()
    // 시트가 화면을 다 먹지 않도록 **최대 높이를 못박는다.** 이게 없으면 시트가 자식에게 높이
    // 제약을 주지 않아 Column 이 프리셋 전부를 담은 제 높이를 가져가고, 스크롤 범위가 0 이 돼
    // **쓸어도 제자리로 돌아온다**(프리셋 11개·`maxValue=0` 으로 확인).
    val density = LocalDensity.current
    val screenHeightPx = LocalWindowInfo.current.containerSize.height
    val sheetMaxHeight = with(density) { (screenHeightPx * SHEET_MAX_RATIO).toDp() }

    TimerSheet(onDismiss = onDismiss, handleGap = SheetGap, sheetGesturesEnabled = !reordering) {
        // 제목과 「프리셋 추가」는 **고정**이고 목록만 스크롤한다. 스크롤을 이 바깥 Column 에
        // 걸면 안 된다 — 그러면 제목까지 함께 밀려 올라간다.
        Column(
            modifier = Modifier
                .heightIn(max = sheetMaxHeight)
                .fillMaxWidth()
                // 3버튼 내비게이션에서는 하단 인셋이 커서, 흡수하지 않으면 마지막 프리셋이 가린다.
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(SheetGap)
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("프리셋", style = TimerSheetTitle, color = colors.textPrimary)
                    Text(
                        text = if (editing) "완료" else "편집",
                        style = if (editing) DoneStyle else EditStyle,
                        color = NmColor.Primary.C600,
                        modifier = Modifier
                            .clip(NmV2ButtonShape)
                            .clickable(role = Role.Button, onClick = onToggleEditing)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = when {
                        presets.isEmpty() -> "프리셋을 추가하면 원탭으로 시작할 수 있어요"
                        editing -> "프리셋을 눌러 수정 · ≡ 드래그로 순서 변경"
                        else -> "누르면 타이머가 바로 시작됩니다"
                    },
                    style = NmV2Type.rowSubtitle,
                    color = colors.textSecondary
                )
            }

            // 남는 높이만 받아 그 안에서 스크롤한다. `fill = false` 라 프리셋이 적으면
            // 예전처럼 내용 높이만큼만 차지해 시트가 작게 열린다.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(listScroll, enabled = !reordering)
            ) {
                ReorderablePresets(
                    presets = presets,
                    editing = editing,
                    onReorder = onReorder,
                    onStart = onStart,
                    onEditPreset = onEditPreset,
                    onDeletePreset = onDeletePreset,
                    onReorderingChange = { reordering = it }
                )
            }

            // 정본은 「프리셋 추가」를 편집 모드에서만 보여 준다 — 평소에는 시작만 하는 시트다.
            //
            // ⚠️ **프리셋이 하나도 없으면 평소에도 보여 준다.** spec 이 "생성 = 프리셋 원탭,
            // 키워드 직접 입력 생성은 없다"고 못박아서, 프리셋이 0개면 타이머를 시작할 길이
            // 아예 없다. 그런데 전부 지우는 건 막지 않았으므로(자기 것만 쓰려는 선택은 정당하다)
            // 여기서 빠져나갈 길을 열어 둔다. 정본에 0개 상태 프레임이 없어 충돌하지 않는다.
            if (editing || presets.isEmpty()) {
                NmV2RaisedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), contentPadding = AddPadding) {
                    Icon(
                        painterResource(NmSymbols.Add),
                        contentDescription = null,
                        tint = NmColor.Primary.C700,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("프리셋 추가", style = AddStyle, color = NmColor.Primary.C700, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 손잡이를 끌어 순서를 바꾼다 — 정본 `C3 프리셋 시트 — 편집 모드`.
 *
 * ## 왜 직접 만드나
 * Compose 에 재정렬 목록이 없다. 다행히 이 목록은 프리셋 몇 개짜리 `Column` 이라
 * **모든 행의 높이가 같다** — 끌린 거리를 행 높이로 나누면 몇 칸 옮겼는지가 바로 나온다.
 *
 * ## 끄는 동안은 내 목록을 보여 준다
 * 저장까지 기다리면 손가락과 화면이 어긋난다. 손을 떼는 순간 한 번만 저장한다
 * ([onReorder]) — 끄는 내내 디스크에 쓰면 한 번 옮길 때마다 저장이 수십 번 일어난다.
 *
 * ## 손잡이에서만 끈다
 * 행 전체를 끌게 하면 "눌러서 수정"과 부딪힌다. 정본이 손잡이를 따로 그린 이유다.
 */
@Composable
private fun ReorderablePresets(
    presets: List<TimerPreset>,
    editing: Boolean,
    onReorder: (List<TimerPreset>) -> Unit,
    onStart: (TimerPreset) -> Unit,
    onEditPreset: (TimerPreset) -> Unit,
    onDeletePreset: (TimerPreset) -> Unit,
    onReorderingChange: (Boolean) -> Unit
) {
    val gapPx = with(LocalDensity.current) { RowGap.toPx() }
    val state = remember { PresetReorderState() }
    LaunchedEffect(presets) { state.sync(presets) }

    // 되돌림 애니메이션. 제스처 블록(`AwaitPointerEventScope`)은 제한된 스코프라
    // 그 안에서 애니메이션을 돌릴 수 없어 밖으로 뺀다.
    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf<Job?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        state.order.forEachIndexed { index, preset ->
            // ⚠️ **`key` 로 감싸야 한다.** `remember` 는 컴포지션 자리에 묶이는데, 순서가 바뀌면
            // 자리도 바뀐다. 감싸지 않으면 밀려난 행의 애니메이션 상태가 서로 뒤바뀐다.
            key(preset.id) {
                ReorderableRow(
                    preset = preset,
                    index = index,
                    editing = editing,
                    held = preset.id == state.heldId,
                    dragOffset = state.offset,
                    step = state.step,
                    onMeasured = { height -> state.step = height + gapPx },
                    onClick = { if (editing) onEditPreset(preset) else onStart(preset) },
                    onDelete = { onDeletePreset(preset) },
                    dragModifier = Modifier.reorderHandle(
                        key = preset.id,
                        onStart = {
                            // 앞선 되돌림이 아직 돌고 있으면 새 드래그를 덮어쓴다.
                            settling?.cancel()
                            state.cancelSettle()
                            state.start(preset.id)
                            onReorderingChange(true)
                        },
                        onDelta = state::drag,
                        onEnd = {
                            val result = state.finish()
                            onReorderingChange(false)
                            onReorder(result)
                            settling = scope.launch { state.settle() }
                        }
                    )
                )
            }
        }
    }
}

/**
 * 한 행. 쥔 행은 손가락을 따르고, **밀려난 행은 옛 자리에서 새 자리로 흐른다.**
 *
 * 자리만 바꿔 다시 그리면 툭툭 끊긴다. 인덱스가 바뀐 순간 옛 자리로 되돌려 놓고 0 까지
 * 애니메이션해, 이웃이 비켜 주는 것처럼 보이게 한다.
 */
@Composable
private fun ReorderableRow(
    preset: TimerPreset,
    index: Int,
    editing: Boolean,
    held: Boolean,
    dragOffset: Float,
    step: Float,
    onMeasured: (Int) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier
) {
    val slide = remember { Animatable(0f) }
    var previous by remember { mutableIntStateOf(index) }

    LaunchedEffect(index, step) {
        val from = previous
        previous = index
        if (from != index && step > 0f) {
            // 옛 자리로 되돌려 놓고 새 자리까지 흐르게 한다.
            slide.snapTo((from - index) * step)
            slide.animateTo(0f, SlideSpec)
        }
    }

    PresetRow(
        preset = preset,
        editing = editing,
        onClick = onClick,
        onDelete = onDelete,
        dragModifier = dragModifier,
        modifier = Modifier
            // 끌리는 행이 이웃 위로 떠야 가려지지 않는다.
            .zIndex(if (held) 1f else 0f)
            .graphicsLayer { translationY = if (held) dragOffset else slide.value }
            // 쥔 행만 살짝 들어 올린다 — 무엇을 잡고 있는지 손끝으로만 알 수는 없다.
            .shadow(if (held) HeldElevation else 0.dp, NmV2ButtonShape)
            .onSizeChanged { onMeasured(it.height) }
    )
}

/**
 * 순서 변경 손잡이의 제스처.
 *
 * ## `detectDragGestures` 를 쓰지 않는다
 * 그건 터치 슬롭을 넘겨야 비로소 이벤트를 소비한다. 그 사이에 **시트와 목록 스크롤이
 * 포인터를 먼저 가져가** 손잡이를 끌면 행이 아니라 시트가 움직인다. 여기서는 누른 즉시
 * 소비해 포인터를 붙잡고, 슬롭 없이 바로 따라오게 한다.
 *
 * @param key `pointerInput` 키. **프리셋 id 같은 고정값**이어야 한다. 목록이나 인덱스를
 *            주면 순서가 바뀌는 순간 다시 시작돼, 끌던 제스처가 [onEnd] 없이 사라진다.
 */
private fun Modifier.reorderHandle(
    key: Any,
    onStart: () -> Unit,
    onDelta: (Float) -> Unit,
    onEnd: () -> Unit
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onStart()

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || change.changedToUpIgnoreConsumed()) break
            onDelta(change.positionChange().y)
            change.consume()
        }

        onEnd()
    }
}

/**
 * 프리셋 한 줄 — 정본 `Preset Row`(볼록 보조 버튼 면). 편집 모드에서는 왼쪽에 삭제, 오른쪽에 손잡이가 붙는다.
 * 홈의 [app.nursemate.core.designsystem.v2.NmV2PresetRow] 와 같은 면이지만 편집 모드 · 손잡이가 있어 여기서 짠다.
 */
@Composable
private fun PresetRow(
    preset: TimerPreset,
    editing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    NmV2RaisedButton(onClick = onClick, modifier = modifier.fillMaxWidth(), contentPadding = RowPadding) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                Icon(
                    painter = painterResource(NmSymbols.Delete),
                    contentDescription = "${preset.label} 삭제",
                    tint = NmColor.Error.C500,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDelete)
                        .size(20.dp)
                )
            }

            // 왼쪽 묶음이 남은 폭을 받고, 그 안에서 라벨만 줄어든다.
            // 라벨에 weight 를 직접 주면 시간·손잡이가 라벨 길이에 따라 좌우로 움직인다.
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.label,
                    style = NmV2Type.rowTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // fill = false 라 짧은 라벨은 태그를 오른쪽으로 끌고 가지 않는다.
                    modifier = Modifier.weight(1f, fill = false)
                )
                NmV2Tag(preset.category.label, tone = preset.category.v2Tone)
            }

            // 정본 간격 — 평소 10(시간 · 재생), 편집 모드 8(시간 · 손잡이).
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (editing) 8.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(preset.durationSeconds),
                    style = NmV2Type.chip,
                    color = colors.textSecondary,
                    maxLines = 1
                )
                if (editing) {
                    // 아이콘은 18 이다. 패딩으로 누르는 영역을 넓히면 행이 그만큼 커져 정본(행 높이 그대로)과
                    // 어긋난다. 대신 Compose 의 최소 터치 영역(48) 확장에 맡긴다 — `pointerInput` 이 붙은
                    // 작은 노드는 주변 48 안의 손가락도 받는다(실기기에서 아이콘 밖 12dp 를 끌어 확인).
                    Icon(
                        painter = painterResource(NmSymbols.Menu),
                        contentDescription = "${preset.label} 순서 변경",
                        tint = NmColor.Neutral.C400,
                        modifier = dragModifier.size(18.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(NmSymbols.PlayCircle),
                        contentDescription = null,
                        tint = NmColor.Primary.C500,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// 정본 수치 — `타이머 / C3 프리셋 시트`
private val SheetGap = 14.dp
private val RowGap = 8.dp
private val RowPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
private val AddPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private val HeldElevation = 6.dp

/** 비켜 주는 행의 움직임. 튕기지 않게 damping 을 높였다. */
private val SlideSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

private val EditStyle = NmV2Type.menu
private val DoneStyle = NmV2Type.menu.copy(fontWeight = FontWeight.SemiBold)
private val AddStyle = NmV2Type.menu.copy(fontWeight = FontWeight.SemiBold)

/** 시트가 차지할 화면 비율의 상한. 넘치면 목록만 스크롤한다. */
private const val SHEET_MAX_RATIO = 0.6f
