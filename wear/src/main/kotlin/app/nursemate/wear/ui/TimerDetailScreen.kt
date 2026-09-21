package app.nursemate.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.Text
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerState
import app.nursemate.core.model.formatDuration
import app.nursemate.core.model.formatRemaining
import app.nursemate.wear.R
import kotlin.math.sqrt

/**
 * W2 조작 — 정본 `타이머 워치 / W2 조작`.
 *
 * spec §워치 — "카드 탭으로 진입: [일시정지/재개] [정지(취소)]".
 * **[완료] 는 없다.** 완료는 만료 뒤에만 가능하고, 그건 W1 의 만료 카드에서 한다.
 *
 * ## 정본 배치를 그대로 옮기면 원형에서 깨진다
 * 정본은 애플워치(사각 396×484)라 폭이 어디서나 같다. 이 워치는 **원형 203dp** 라,
 * 좌우 여백 11dp 를 뺀 181dp 폭이 확보되는 구간이 **화면 중심 위아래 46dp** 뿐이다.
 * 정본대로 버튼 둘을 아래에 쌓았더니 [정지] 가 곡면에 잘렸다(실기기 확인).
 *
 * [EdgeButton] 으로 내려도 봤지만 그러면 **스크롤해야 [정지] 가 보인다** — 조작 화면에서
 * 정지 버튼이 있는지조차 모르는 건 치명적이다(헤더 20 + 링 84 + 버튼 52 + 곡면버튼 50 =
 * 206dp 로 203dp 를 넘는다).
 *
 * 그래서 **두 버튼을 가로로** 놓았다. 원형 화면의 중앙부는 폭이 넓어 둘이 들어가고, 세로로
 * 한 줄만 쓰므로 전부 한 눈에 들어온다. 정본의 세로 배치는 원형에서 성립하지 않는다 —
 * 정본 개정 요청 대상이다.
 */
@Composable
fun TimerDetailScreen(
    timer: CareTimer,
    now: Long,
    onBack: () -> Unit,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit
) {
    val paused = timer.state == TimerState.PAUSED

    // 곡면 여백은 **화면 폭에 비례**해야 한다. 203dp 기기에 맞춰 11dp·20dp 로 고정해 두었더니
    // 더 작은 워치에서 좌우가 곡면에 먹혔고 Play 가 거부했다(2026-09-21 「시계 모양」).
    // 203dp 에서는 같은 값(11dp·31dp)이 나오므로 이 기기의 그림은 그대로다.
    val edge = roundSafeHorizontal(HORIZONTAL_PADDING_FRACTION)
    val clock = roundSafeHorizontal(CLOCK_BAND_FRACTION)
    val lowRow = actionRowInset(edge)

    // 링은 화면 폭에도 갇힌다 — 좌우 여백을 뺀 폭보다 크면 곡면에 닿는다.
    // 최종 지름은 `weight` 가 주는 높이와 이 상한 중 작은 쪽이 된다.
    val ringSize = minOf(RingMaxSize, LocalConfiguration.current.screenWidthDp.dp - edge * 2)

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 위쪽은 시스템 시계(TimeText)가 쓰는 자리라 비워 둔다 — 앱이 못 옮긴다.
            // 시계 높이도 화면에 비례한다(작은 워치에서 24dp 는 과하다).
            .padding(start = edge, end = edge, top = clock, bottom = edge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
    ) {
        DetailHeader(timer, onBack)
        // ⚠️ **링은 남은 높이를 받아간다.** 고정 지름을 주면 작은 워치에서 버튼을 화면 밖으로
        // 밀어낸다 — [RingMaxSize] 주석 참고. `weight` 가 헤더·버튼을 먼저 놓고 남은 것을 준다.
        // `size()` 만으로는 안 된다 — 남은 높이가 모자라면 높이만 깎여 **타원**이 된다.
        // 남은 높이를 읽어 정사각 지름을 직접 정한다.
        BoxWithConstraints(
            modifier = Modifier.weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            ProgressRing(timer, now, paused, minOf(ringSize, maxHeight))
        }
        // ⚠️ **버튼 행은 화면 폭을 다 쓰면 안 된다.** 원형이라 이 높이(화면 중심 아래
        // 약 73dp)에서 쓸 수 있는 폭은 142dp 뿐인데 `fillMaxWidth` 는 181dp 를 쓴다 —
        // 좌우 끝이 곡면에 먹힌다(실기기 확인). 곡면에 맞춰 좁힌다.
        //
        // 바깥 Column 이 이미 `edge` 를 먹었으므로 그 차이만 더한다.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = lowRow - edge)
        ) {
            ActionButton(
                icon = if (paused) R.drawable.nm_ic_play else R.drawable.nm_ic_pause,
                label = if (paused) "재개" else "일시정지",
                tint = WearTimerColors.OnBackground,
                onClick = onPauseOrResume,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                icon = R.drawable.nm_ic_square,
                label = "정지",
                tint = WearTimerColors.Danger,
                onClick = onStop,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 조작 버튼.
 *
 * ⚠️ **아이콘과 글자를 한 줄에 둔다.** 위아래로 쌓으면 48dp 가 되어 링을 그만큼 깎아야
 * 했다(84dp 까지 내려갔었다). 한 줄이면 30dp — 정본 버튼 높이(`padding [16, 0]`)와 같고,
 * 링도 정본 100dp 를 그대로 쓸 수 있다.
 *
 * 폭은 (203 − 좌우여백 22 − 사이 6) ÷ 2 ≈ 87dp. 가장 긴 「일시정지」가 아이콘 13 +
 * 간격 4 + 글자 4자(12sp ≈ 48dp) = 65dp 라 들어간다.
 *
 * Wear `Button` 을 쓰지 않는 이유는 최소 높이 52dp 를 바깥에서 강제해 못 낮추기 때문이다.
 */
@Composable
private fun ActionButton(icon: Int, label: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 보이는 알약은 37dp 지만 **누르는 영역은 그보다 넓다**(WO-V2 는 48dp 를 권한다). 그래서
    // `clickable` 은 바깥 Box 가 갖고 배경·모서리는 안쪽 Row 가 갖는다 — 한 덩어리로 만들면
    // 둘 중 하나를 포기하게 된다.
    //
    // 실기기 측정으로는 **44dp** 가 나온다(y 320~414px @340dpi). 확장분이 아래로만 붙어서
    // 48dp 를 다 못 채운다 — 위쪽은 링이 차지한 자리라 더 못 넓힌다. 늘리려면 링을 깎아야 한다.
    Box(
        modifier = modifier.heightIn(min = MinTouchTarget).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 아이콘·글자 사이와 좌우 여백은 최소로 둔다. 192dp 워치에서 「일시정지」가
            // 들어갈 폭이 68dp 뿐이라 여기서 4dp 를 아끼지 않으면 말줄임이 난다.
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .clip(ActionShape)
                .background(WearTimerColors.Card)
                .padding(horizontal = 2.dp, vertical = 7.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = WearTimerType.ActionCompact,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 정본 헤더의 왼쪽 꺾쇠 — **누를 수 있어야 한다.**
 *
 * Wear 의 표준 뒤로 가기는 오른쪽 스와이프지만(WO-V3), 그리기만 하고 안 눌리면 죽은 표시가
 * 된다. 장갑 낀 손으로는 스와이프가 잘 안 먹기도 해서 둘 다 둔다.
 */
@Composable
private fun DetailHeader(timer: CareTimer, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(contentColor = WearTimerColors.Primary),
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_chevron_left),
                contentDescription = "뒤로",
                tint = WearTimerColors.Primary,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = timer.label,
            style = WearTimerType.DetailLabel,
            color = WearTimerColors.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = timer.category.label,
            style = WearTimerType.Category,
            color = categoryColor(timer.category),
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@Composable
private fun ProgressRing(timer: CareTimer, now: Long, paused: Boolean, size: Dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        TimerRing(
            fraction = timer.ringFractionAt(now),
            color = if (paused) WearTimerColors.Muted else WearTimerColors.Primary,
            trackColor = WearTimerColors.Track,
            // 정본 `innerRadius: 0.9` — 지름 대비 두께 5%.
            strokeWidth = size * RING_STROKE_FRACTION,
            modifier = Modifier.size(size)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatRemaining(timer.remainingAt(now)),
                style = WearTimerType.DetailRemaining,
                color = WearTimerColors.OnBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (paused) "일시정지" else formatDuration(timer.durationSeconds),
                style = WearTimerType.DetailTotal,
                color = WearTimerColors.Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 진행 링 지름의 **상한** — 정본 `Progress Ring 200`(=100dp).
 *
 * ⚠️ **고정값이 아니다.** 예전엔 `100.dp` 로 못박고 세로 예산을 이렇게 맞춰 두었다:
 * `24(시계) + 20(헤더) + 5 + 100 + 5 + 30(버튼) + 8 = 192dp ≤ 203dp`.
 * 그 계산은 **203dp 워치 하나에만** 성립한다. 192dp(Pixel Watch)에서는 예산을 넘겨
 * 버튼이 아래로 밀리고 「일시정지」가 `일시정…` 으로 잘렸다 — Play 가 이걸
 * 「Wear 앱 품질 가이드라인: 시계 모양」으로 거부했다(2026-09-21).
 *
 * 지금은 링이 **남은 높이를 받아간다**([Modifier.weight]). 이 값은 그 위의 뚜껑일 뿐이라,
 * 큰 워치에서 링만 커지는 일이 없고 작은 워치에서는 알아서 줄어든다. 어떤 지름에서도
 * 넘칠 수가 없다 — 남은 것을 쓰기 때문이다.
 */
private val RingMaxSize = 100.dp

/** 링 두께 — 정본 `innerRadius: 0.9`, 즉 지름의 5%. 지름을 따라가야 비율이 유지된다. */
private const val RING_STROKE_FRACTION = 0.05f

/**
 * 버튼 행이 곡면에 닿지 않도록 좌우에서 물러설 거리 — **원의 기하로 직접 구한다.**
 *
 * 예전엔 「화면 폭의 15.3%」 같은 **고정 비율**이었다. 그 값은 링이 100dp 로 고정이던 시절
 * 버튼이 화면 중심 아래 73dp 에 놓인다는 전제에서 나왔다. 링이 남은 높이를 받아가게 되면서
 * 작은 워치에서는 버튼이 더 **위로** 올라가는데, 인셋만 그대로라 쓸 수 있는 폭을 과하게
 * 깎아 「일시정지」가 `일시정…` 으로 잘렸다(192dp 에서 확인).
 *
 * 반지름 `r` 인 원에서 중심으로부터 `d` 만큼 아래에 있는 가로줄의 반현(半弦)은
 * `√(r² − d²)` 다. 버튼 행의 중심은 아래 여백과 자기 높이의 절반만큼 올라온 자리이므로
 * `d = r − edge − 높이/2` 다. 물러설 거리는 `r − 반현`.
 *
 * 이렇게 두면 어떤 지름에서도 **실제로 쓸 수 있는 폭**이 나온다. 비율표를 손으로 맞출 일이 없다.
 */
@Composable
private fun actionRowInset(edge: Dp): Dp {
    val radius = LocalConfiguration.current.screenWidthDp / 2f
    val belowCenter = radius - edge.value - MinTouchTarget.value / 2f
    val halfChord = sqrt((radius * radius - belowCenter * belowCenter).coerceAtLeast(0f))
    return (radius - halfChord).dp
}

/**
 * 위쪽에 비워 두는 시계(`TimeText`) 자리의 비율.
 *
 * 203dp 에서 쓰던 24dp 를 비율로 옮긴 값(24/203)이다. 작은 워치에서 24dp 를 그대로 두면
 * 남는 높이가 그만큼 더 줄어 버튼이 밀린다.
 */
private const val CLOCK_BAND_FRACTION = 0.118f

/** 정본 버튼 `cornerRadius: 30`. */
private val MinTouchTarget = 48.dp
private val ActionShape = RoundedCornerShape(15.dp)
