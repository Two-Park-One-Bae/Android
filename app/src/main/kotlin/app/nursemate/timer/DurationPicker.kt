package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 *
 * ## 끝없이 감긴다
 * 정본이 「시」 열 위에 22·23 을 보여 준다 — 0 에서 위로 올리면 23 으로 이어지는 순환 휠이다.
 * 목록을 [LOOPS] 벌 이어 붙이고 한가운데서 시작해 흉내 낸다. 사용자가 실제로 끝에 닿으려면
 * 수백 바퀴를 돌려야 해서 경계가 드러나지 않는다.
 *
 * ## 단위는 고정이다
 * `시간`·`분`·`초` 는 숫자와 함께 구르지 않고 제자리에 머문다. 숫자 자리를 [NumberWidth] 로
 * 못박고 단위를 그 오른쪽에 겹쳐 그려, 굴러가는 숫자와 항상 같은 줄에 선다.
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
        Row(Modifier.fillMaxWidth()) {
            WheelColumn(
                count = HOUR_COUNT,
                selected = hours,
                unit = "시간",
                pad = false,
                onSelect = { onChange(it * 3600 + minutes * 60 + secs) },
                modifier = Modifier.weight(1f)
            )
            WheelColumn(
                count = SEXAGESIMAL,
                selected = minutes,
                unit = "분",
                pad = true,
                onSelect = { onChange(hours * 3600 + it * 60 + secs) },
                modifier = Modifier.weight(1f)
            )
            WheelColumn(
                count = SEXAGESIMAL,
                selected = secs,
                unit = "초",
                pad = true,
                onSelect = { onChange(hours * 3600 + minutes * 60 + it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 한 열.
 *
 * @param count 값의 가짓수. 값은 언제나 `0 until count` 다.
 * @param pad 분·초처럼 두 자리로 맞출지. 「시간」은 정본이 `0` 으로 쓴다.
 */
@Composable
private fun WheelColumn(
    count: Int,
    selected: Int,
    unit: String,
    pad: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    // ⚠️ **콜백을 최신으로 갱신해야 한다.** `onSelect` 는 형제 열의 값(시·분·초)을 클로저로
    // 잡는데, 아래 `LaunchedEffect` 는 키가 그대로면 다시 시작하지 않아 **처음 컴포지션 때의
    // 값**을 계속 쓴다. 그러면 시를 바꾼 뒤 초를 굴릴 때 시가 옛 값으로 되돌아간다.
    val select by rememberUpdatedState(onSelect)
    val total = count * LOOPS
    // 한가운데 벌에서 시작한다 — 위아래 어느 쪽으로도 수백 바퀴가 남는다.
    val origin = remember(count) { count * (LOOPS / 2) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = origin + selected)

    // 스크롤이 멎어 한 칸에 딱 맞았을 때만 값을 올린다. 스크롤 중에 올리면 지나가는 값이
    // 전부 저장돼 버린다.
    LaunchedEffect(state, count) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> if (offset == 0) select(index % count) }
    }

    Box(modifier.height(PickerHeight)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            // 위아래를 두 칸씩 비워 첫 보이는 항목이 가운데로 온다.
            contentPadding = PaddingValues(vertical = ItemHeight * 2),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(total) { index ->
                val value = index % count
                val active = value == selected
                Row(
                    modifier = Modifier.height(ItemHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (pad) "%02d".format(value) else "$value",
                        style = if (active) ActiveValue else IdleValue,
                        color = if (active) colors.textPrimary else NmColor.Neutral.C400,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(NumberWidth)
                    )
                    // 단위가 들어갈 자리를 비워 둔다 — 숫자만 이 폭 안에서 구른다.
                    Spacer(Modifier.width(UnitGap + UnitWidth))
                }
            }
        }

        // 고정 단위. 목록 위에 겹쳐 그리되 자리는 항목과 똑같이 잡아 줄을 맞춘다.
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(NumberWidth + UnitGap))
            Text(
                text = unit,
                style = UnitStyle,
                color = colors.textPrimary,
                modifier = Modifier.width(UnitWidth)
            )
        }
    }
}

// 정본 수치 — `시간 Picker`
private val PickerHeight = 180.dp
private val ItemHeight = 36.dp
private val BandShape = RoundedCornerShape(8.dp)

/** 20sp SemiBold 로 `23` 이 들어가는 폭. */
private val NumberWidth = 30.dp
private val UnitGap = 4.dp

/** 13sp SemiBold 로 `시간` 이 들어가는 폭. */
private val UnitWidth = 32.dp

private const val HOUR_COUNT = 24
private const val SEXAGESIMAL = 60

/** 목록을 몇 벌 이어 붙일지. 짝수여야 한가운데 벌이 정확히 잡힌다. */
private const val LOOPS = 1000

private val ActiveValue = NmTypography.body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
private val IdleValue = NmTypography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal)
private val UnitStyle = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
