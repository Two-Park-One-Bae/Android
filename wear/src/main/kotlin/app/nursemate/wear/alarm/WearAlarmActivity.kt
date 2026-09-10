package app.nursemate.wear.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerState
import app.nursemate.core.timer.TimerRepository
import app.nursemate.wear.R
import app.nursemate.wear.ui.WearTimerColors
import app.nursemate.wear.ui.WearTimerType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 손목을 덮는 만료 알람 화면 — 정본 「타이머 워치 / W3 만료」.
 *
 * ## 알림만으로는 정본이 안 나온다
 * Wear 알림도 전체 화면을 쓰지만 **모양은 시스템이 정한다.** 정본은 종 아이콘과 가로로
 * 긴 [완료] 버튼을 요구하므로 우리가 액티비티를 그려 `setFullScreenIntent` 로 띄운다
 * (폰의 [TimerAlarmActivity] 와 같은 사정).
 *
 * ⚠️ `showWhenLocked` 가 없으면 화면이 꺼져 있을 때 올라오지 못한다. 워치는 대부분 꺼져
 * 있으므로 이게 없으면 사실상 아무 때도 안 뜬다.
 *
 * ## 상태는 폰이 바꾼다
 * [완료] 는 폰에 `Remove` 명령을 보낼 뿐이고, 실제로 지우는 것은 폰이다(spec §명령).
 * 명령이 전달되지 않으면 화면을 닫지 않는다 — 닫아 버리면 사용자는 처리된 줄 안다.
 */
@AndroidEntryPoint
class WearAlarmActivity : ComponentActivity() {

    @Inject lateinit var repository: TimerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent {
            // ⚠️ **인텐트에 실린 타이머 하나에 매달리지 않는다.** 그러면 나중에 울린 것이
            // `onNewIntent` 로 앞 것을 덮고, [완료] 를 누르면 화면이 닫혀 **아직 울리는 앞
            // 알람이 화면 없이 남는다.** spec §만료·알람은 "하나씩 순서대로"를 요구한다.
            //
            // 그래서 **울리는 목록을 구독해 먼저 만료한 것부터** 보여 준다. 하나를 완료하면
            // 다음 것으로 저절로 갈아 끼워지고, 다 끝나야 화면이 닫힌다.
            // ⚠️ **초기값은 빈 목록이 아니라 null 이다.** 빈 목록으로 두면 첫 컴포지션에서
            // "울릴 게 없다"로 읽혀 **화면이 뜨자마자 스스로 닫힌다** — 실기기에서 0.46초 만에
            // 사라지는 것으로 나타났다. 저장소를 읽기 전과 정말 없는 것을 구분해야 한다.
            val timers: List<CareTimer>? by repository.timers.collectAsStateWithLifecycle(initialValue = null)
            val ringing = remember(timers) {
                timers.orEmpty().filter { it.state == TimerState.RINGING }.sortedBy { it.endAtEpochMillis }
            }
            val current = ringing.firstOrNull()

            LaunchedEffect(timers, current) {
                if (timers != null && current == null) finish()
            }

            if (current != null) {
                WearAlarmScreen(
                    title = current.alarmTitle,
                    onComplete = { complete(current.id) }
                )
            }
        }
    }

    /**
     * 폰에 삭제 명령을 보낸다.
     *
     * 화면은 여기서 닫지 않는다 — 폰이 처리하면 스냅샷이 바뀌고, 남은 것이 있으면 그것으로
     * 갈아 끼워지고 없으면 위에서 닫는다. 명령이 전달되지 않으면 화면이 그대로 남아
     * 사용자가 다시 누를 수 있다.
     */
    private fun complete(timerId: String) {
        lifecycleScope.launch { repository.remove(timerId) }
    }

    companion object {
        const val EXTRA_TIMER_ID = "timer_id"
        const val EXTRA_TITLE = "title"

        fun intent(context: Context, timerId: String, title: String): Intent =
            Intent(context, WearAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TIMER_ID, timerId)
                .putExtra(EXTRA_TITLE, title)
    }
}

/**
 * 정본 프레임(396×484px ÷2) 그대로.
 *
 * 다만 **정본의 Spacer 55dp 는 그만큼 못 쓴다.** 정본 프레임은 242dp 인데 우리 화면은
 * 203dp 라 39dp 가 모자란다. 제목·종·버튼은 정본 크기를 지키고 그 사이 간격에서 줄인다.
 */
@Composable
private fun WearAlarmScreen(title: String, onComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearTimerColors.Background)
            .padding(horizontal = 11.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(BellSize)
                .clip(CircleShape)
                .background(WearTimerColors.ExpiredSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_bell_ring),
                contentDescription = null,
                tint = WearTimerColors.Warning,
                modifier = Modifier.size(BellIcon)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = WearTimerType.AlarmTitle,
            color = WearTimerColors.OnBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                // 정본 320px ÷2. `fillMaxWidth` 로 두면 181dp 가 되어 정본보다 넓다.
                .width(CompleteWidth)
                .clip(RoundedCornerShape(CompleteRadius))
                .background(WearTimerColors.WarningStrong)
                .clickable(onClick = onComplete)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_check),
                contentDescription = null,
                tint = WearTimerColors.OnWarning,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.size(4.dp))
            Text("완료", style = WearTimerType.Action, color = WearTimerColors.OnWarning)
        }
    }
}

/** 정본 92px ÷2. */
private val BellSize = 46.dp

/** 정본 44px ÷2. */
private val BellIcon = 22.dp

/** 정본 `cornerRadius 34` ÷2. */
private val CompleteRadius = 17.dp

/** 정본 완료 버튼 폭 320px ÷2. */
private val CompleteWidth = 160.dp
