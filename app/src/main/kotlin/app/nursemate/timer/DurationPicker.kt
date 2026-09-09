package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 시·분·초 휠 — 정본 `타이머 / C3 프리셋 편집` 의 `시간 Picker`.
 *
 * 높이 180 에 항목 36 이 다섯 줄, 가운데 한 줄이 선택 칸이다. 가운데만 20/600 으로 키우고
 * 나머지는 15/normal `neutral-400` 으로 죽인다.
 *
 * ## 왜 직접 만드나
 * Compose 에 휠 피커가 없다. `LazyColumn` 에 스냅 플링을 붙이고 위아래를 두 칸씩 비워
 * **첫 보이는 항목이 곧 가운데 항목**이 되게 했다 — 그래야 선택값을 스크롤 위치에서 바로 읽는다.
 */
@Composable
fun DurationPicker(seconds: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    Box(modifier.fillMaxWidth().height(PickerHeight), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ItemHeight)
                .clip(BandShape)
                .background(NmColor.Neutral.C100, BandShape)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            WheelColumn(
                values = HOURS,
                selected = hours,
                unit = "시간",
                onSelect = { onChange(it * 3600 + minutes * 60 + secs) },
                modifier = Modifier.weight(1f)
            )
            WheelColumn(
                values = MINUTES,
                selected = minutes,
                unit = "분",
                onSelect = { onChange(hours * 3600 + it * 60 + secs) },
                modifier = Modifier.weight(1f)
            )
            WheelColumn(
                values = MINUTES,
                selected = secs,
                unit = "초",
                onSelect = { onChange(hours * 3600 + minutes * 60 + it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WheelColumn(
    values: List<Int>,
    selected: Int,
    unit: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    val state = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(selected).coerceAtLeast(0))

    // 스크롤이 멎어 한 칸에 딱 맞았을 때만 값을 올린다. 스크롤 중에 올리면 지나가는 값이
    // 전부 저장돼 버린다.
    LaunchedEffect(state, values) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                if (offset == 0) values.getOrNull(index)?.let(onSelect)
            }
    }

    LazyColumn(
        modifier = modifier.height(PickerHeight),
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        // 위아래를 두 칸씩 비워 첫 보이는 항목이 가운데로 온다.
        contentPadding = PaddingValues(vertical = ItemHeight * 2),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(values, key = { it }) { value ->
            val active = value == selected
            Row(
                modifier = Modifier.height(ItemHeight),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (unit == "시간") "$value" else "%02d".format(value),
                    style = if (active) ActiveValue else IdleValue,
                    color = if (active) colors.textPrimary else NmColor.Neutral.C400
                )
                if (active) {
                    Text(unit, style = UnitStyle, color = colors.textPrimary)
                }
            }
        }
    }
}

// 정본 수치 — `시간 Picker`
private val PickerHeight = 180.dp
private val ItemHeight = 36.dp
private val BandShape = RoundedCornerShape(8.dp)

private val HOURS = (0..23).toList()
private val MINUTES = (0..59).toList()

private val ActiveValue = NmTypography.body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
private val IdleValue = NmTypography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal)
private val UnitStyle = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
