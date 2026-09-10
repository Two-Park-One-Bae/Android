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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import app.nursemate.core.model.TimerCommand
import app.nursemate.core.model.TimerState
import app.nursemate.wear.R
import app.nursemate.wear.sync.WearTimerStore
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

    @Inject lateinit var store: WearTimerStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        render(intent)
        closeWhenSettled()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent?) {
        val timerId = intent?.getStringExtra(EXTRA_TIMER_ID)
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        setContent { WearAlarmScreen(title = title, onComplete = { complete(timerId) }) }
    }

    /**
     * 이 타이머가 더 이상 울리지 않으면 화면을 닫는다.
     *
     * [완료] 는 여기서만 눌리는 게 아니다 — 폰에서 완료하거나, 워치 알림의 [완료] 를 눌러도
     * 타이머는 사라진다. 그때 이 화면이 남아 있으면 **이미 끝난 알람이 손목을 계속 덮는다.**
     */
    private fun closeWhenSettled() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.snapshot.collect { snapshot ->
                    val id = intent?.getStringExtra(EXTRA_TIMER_ID) ?: return@collect
                    val stillRinging = snapshot?.timers.orEmpty()
                        .any { it.id == id && it.state == TimerState.RINGING }
                    if (!stillRinging) finish()
                }
            }
        }
    }

    private fun complete(timerId: String?) {
        if (timerId == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            if (store.send(TimerCommand.Remove(timerId))) finish()
        }
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
