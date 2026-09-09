package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.TimerPreset
import kotlin.math.roundToInt

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
@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = !reordering,
        // 프리셋 6개면 절반 높이(partial)를 넘긴다. 거기서 멈추면 마지막 항목이 시트 밖으로
        // 밀려 잘린 채 보이므로, 처음부터 콘텐츠 높이만큼 펼친다.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 3버튼 내비게이션에서는 하단 인셋이 커서, 흡수하지 않으면 마지막 프리셋이 가린다.
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState(), enabled = !reordering)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("프리셋", style = SheetTitle, color = colors.textPrimary)
                    Text(
                        text = if (editing) "완료" else "편집",
                        style = if (editing) DoneStyle else EditStyle,
                        color = NmColor.Primary.C600,
                        modifier = Modifier.clickable(onClick = onToggleEditing)
                    )
                }
                Text(
                    text = if (editing) {
                        "프리셋을 눌러 수정 · 휴지통으로 삭제"
                    } else {
                        "누르면 타이머가 바로 시작됩니다"
                    },
                    style = SheetSubStyle,
                    color = colors.textSecondary
                )
            }

            ReorderablePresets(
                presets = presets,
                editing = editing,
                onReorder = onReorder,
                onStart = onStart,
                onEditPreset = onEditPreset,
                onDeletePreset = onDeletePreset,
                onReorderingChange = { reordering = it }
            )

            // 정본은 「프리셋 추가」를 편집 모드에서만 보여 준다 — 평소에는 시작만 하는 시트다.
            if (editing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RowShape)
                        .border(1.dp, NmColor.Neutral.C300, RowShape)
                        .clickable(onClick = onAdd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(DsR.drawable.nm_ic_plus),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "프리셋 추가",
                        style = AddStyle,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
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
    // 끄는 동안만 쓰는 내 목록. 저장이 돌아오면 같은 순서라 튀지 않는다.
    var order by remember(presets) { mutableStateOf(presets) }
    // ⚠️ **인덱스가 아니라 id 로 들고 있는다.** 끄는 도중 자리가 바뀌면 인덱스는 매번 달라져,
    // 어느 행을 쥐고 있는지 놓친다.
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var step by remember { mutableFloatStateOf(0f) }

    // 제스처 블록은 한 번만 만들어지므로 그 안에서 최신 목록을 읽으려면 이게 필요하다.
    val currentOrder by rememberUpdatedState(order)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        order.forEach { preset ->
            val held = preset.id == draggingId
            PresetRow(
                preset = preset,
                editing = editing,
                onClick = { if (editing) onEditPreset(preset) else onStart(preset) },
                onDelete = { onDeletePreset(preset) },
                modifier = Modifier
                    // 끌리는 행이 이웃 위로 떠야 가려지지 않는다.
                    .zIndex(if (held) 1f else 0f)
                    .graphicsLayer { translationY = if (held) dragOffset else 0f }
                    .onSizeChanged { step = it.height + gapPx },
                dragModifier = Modifier.reorderHandle(
                    key = preset.id,
                    onStart = {
                        draggingId = preset.id
                        dragOffset = 0f
                        onReorderingChange(true)
                    },
                    onDelta = { delta ->
                        dragOffset += delta
                        val from = currentOrder.indexOfFirst { it.id == preset.id }
                        if (from >= 0 && step > 0f) {
                            val shift = (dragOffset / step).roundToInt()
                            val to = (from + shift).coerceIn(0, currentOrder.lastIndex)
                            if (to != from) {
                                order = currentOrder.toMutableList().apply { add(to, removeAt(from)) }
                                // 옮긴 칸 수만큼 빼야 손가락과 행이 계속 붙어 있다.
                                dragOffset -= (to - from) * step
                            }
                        }
                    },
                    onEnd = {
                        draggingId = null
                        dragOffset = 0f
                        onReorderingChange(false)
                        onReorder(currentOrder)
                    }
                )
            )
        }
    }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(NmColor.Neutral.C50, RowShape)
            .border(1.dp, colors.border, RowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editing) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_trash_2),
                contentDescription = "${preset.label} 삭제",
                tint = NmColor.Error.C500,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete)
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
                style = PresetLabel,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // fill = false 라 짧은 라벨은 태그를 오른쪽으로 끌고 가지 않는다.
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = preset.category.label,
                style = TagStyle,
                color = tagForeground(preset.category),
                maxLines = 1,
                modifier = Modifier
                    .clip(TagShape)
                    .background(tagBackground(preset.category), TagShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(preset.durationSeconds),
                style = DurationStyle,
                color = colors.textSecondary,
                maxLines = 1
            )
            if (editing) {
                // 아이콘은 18 이지만 터치 영역은 패딩으로 넓힌다 — 손잡이가 작으면 못 잡는다.
                Icon(
                    painter = painterResource(DsR.drawable.nm_ic_menu),
                    contentDescription = "${preset.label} 순서 변경",
                    tint = NmColor.Neutral.C400,
                    modifier = dragModifier
                        .padding(HandleTouchPadding)
                        .size(18.dp)
                )
            } else {
                Icon(
                    painter = painterResource(DsR.drawable.nm_ic_circle_play),
                    contentDescription = null,
                    tint = NmColor.Primary.C500,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// 정본 수치 — `타이머 / C3 프리셋 시트`
private val RowShape = RoundedCornerShape(12.dp)
private val RowGap = 8.dp
private val HandleTouchPadding = 10.dp
private val TagShape = RoundedCornerShape(6.dp)

private val SheetTitle = NmTypography.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
private val SheetSubStyle = NmTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)
private val EditStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
private val DoneStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val PresetLabel = NmTypography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val TagStyle = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
private val DurationStyle = NmTypography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
private val AddStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
