package app.nursemate.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerState
import app.nursemate.core.model.formatDuration
import app.nursemate.core.model.formatRemaining
import app.nursemate.wear.R

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
    pending: Boolean,
    onBack: () -> Unit,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit
) {
    val paused = timer.state == TimerState.PAUSED

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 위쪽은 시스템 시계(TimeText)가 쓰는 자리라 비워 둔다 — 앱이 못 옮긴다.
            .padding(start = 11.dp, end = 11.dp, top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
    ) {
        DetailHeader(timer, onBack)
        ProgressRing(timer, now, paused)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ActionButton(
                icon = if (paused) R.drawable.nm_ic_play else R.drawable.nm_ic_pause,
                label = if (paused) "재개" else "일시정지",
                tint = WearTimerColors.OnBackground,
                enabled = !pending,
                onClick = onPauseOrResume,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                icon = R.drawable.nm_ic_square,
                label = "정지",
                tint = WearTimerColors.Danger,
                enabled = !pending,
                onClick = onStop,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 조작 버튼.
 *
 * ⚠️ Wear `Button` 은 최소 높이 52dp 를 바깥에서 강제해 못 낮춘다. 둘을 세로로 쌓으면
 * 104dp 라 링이 설 자리가 없어, 정본 높이(30dp)를 지키려면 직접 그리는 수밖에 없다.
 * 가로 배치라 각 버튼 폭이 86dp 로 넉넉해 터치는 문제없다.
 */
@Composable
private fun ActionButton(
    icon: Int,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(ActionShape)
            .background(WearTimerColors.Card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(text = label, style = WearTimerType.Action, color = tint, maxLines = 1)
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
private fun ProgressRing(timer: CareTimer, now: Long, paused: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RingSize)) {
        CircularProgressIndicator(
            progress = { timer.progressAt(now) },
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = if (paused) WearTimerColors.Muted else WearTimerColors.Primary,
                trackColor = WearTimerColors.Track
            ),
            // 정본 `innerRadius: 0.9` — 지름 대비 두께 5%.
            strokeWidth = 4.dp,
            gapSize = 2.dp,
            modifier = Modifier.size(RingSize)
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

/** 정본 링 200(=100dp)을 화면 비율(203/242)만큼 줄인 값. */
private val RingSize = 84.dp

/** 정본 버튼 `cornerRadius: 30`. */
private val ActionShape = RoundedCornerShape(15.dp)
