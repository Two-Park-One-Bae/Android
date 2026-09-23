package app.nursemate.wear.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import app.nursemate.wear.ui.WearTimerType.wrapKorean
import app.nursemate.wear.ui.roundSafeHorizontal
import app.nursemate.wear.ui.scaled
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
        // `turnScreenOn` 은 **켜기만** 한다. 이게 없으면 화면 시간초과가 그대로 걸려,
        // 울리는 중인데 화면만 다시 꺼진다(워치는 기본 15초라 특히 짧다).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
 * 정본 「W3 만료」.
 *
 * ⚠️ **정본 치수를 그대로 쓰면 원형에서 잘린다.** 정본 프레임은 사각 242dp 라 폭이
 * 어디서나 같지만 이 화면은 원이다. 고정 폭 160dp 완료 버튼을 화면 아래쪽에 두었더니
 * 192dp 워치에서 **원 밖으로 나갔다**(실측 1.0 에서 17화소, 글꼴 1.24 에서 75화소).
 * 상세 화면이 두 번 거부당한 것과 같은 항목이다(WO-V16).
 *
 * 그래서 [TimerDetailScreen] 과 같은 규칙을 쓴다:
 * - 내용을 **원에 내접하는 정사각형** 안에 둔다([ALARM_INSET_FRACTION]).
 * - 완료 버튼은 고정 폭이 아니라 그 정사각형의 폭을 쓴다.
 * - 종 아이콘이 남는 높이를 받아간다 — 글꼴이 커져 제목이 두 줄이 되면 종이 먼저 양보한다.
 *   제목과 버튼은 못 줄인다(읽어야 하고 눌러야 한다).
 */
@Composable
private fun WearAlarmScreen(title: String, onComplete: () -> Unit) {
    val inset = roundSafeHorizontal(ALARM_INSET_FRACTION)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearTimerColors.Background)
            .padding(inset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val bell = minOf(BellSize.scaled(), maxHeight)
            Box(
                modifier = Modifier.size(bell).clip(CircleShape).background(WearTimerColors.ExpiredSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_bell_ring),
                    contentDescription = null,
                    tint = WearTimerColors.Warning,
                    modifier = Modifier.size(bell * BELL_ICON_RATIO)
                )
            }
        }
        Text(
            text = title,
            style = WearTimerType.AlarmTitle.scaled().wrapKorean(),
            color = WearTimerColors.OnBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(TitleButtonGap.scaled()))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 정사각형 모서리는 원에 닿아 있다 — 버튼이 그 폭을 꽉 채우면 둥근 모서리
                // 덕에 간신히 들어간다. 눈이 아니라 실측으로 확인한다.
                .padding(horizontal = ButtonSideInset.scaled())
                .clip(RoundedCornerShape(CompleteRadius.scaled()))
                .background(WearTimerColors.WarningStrong)
                .clickable(onClick = onComplete)
                .padding(vertical = 10.dp.scaled()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_check),
                contentDescription = null,
                tint = WearTimerColors.OnWarning,
                modifier = Modifier.size(12.dp.scaled())
            )
            Spacer(Modifier.size(4.dp.scaled()))
            Text("완료", style = WearTimerType.Action.scaled(), color = WearTimerColors.OnWarning)
        }
    }
}

/**
 * 원에 내접하는 정사각형까지 들이는 비율 — `androidx.wear.widget.BoxInsetLayout` 의
 * `FACTOR = 0.146447f  // (1 - sqrt(2)/2)/2` 와 같은 값이다.
 *
 * 스크롤하지 않는 화면의 안전 영역이다. 상세 화면은 가장자리 진행 링이 있어 그 안쪽에
 * 내접시키지만, 이 화면에는 링이 없어 화면 원 기준이면 된다.
 */
private const val ALARM_INSET_FRACTION = 0.146447f

/** 종 아이콘이 그 원 안에서 차지하는 비율 — 정본 44 / 92. */
private const val BELL_ICON_RATIO = 0.478f

/** 제목과 완료 버튼 사이. */
private val TitleButtonGap = 12.dp

/** 완료 버튼이 정사각형 좌우에서 한 번 더 들어가는 거리. */
private val ButtonSideInset = 2.dp

/** 정본 92px ÷2. 작은 워치에서는 남는 높이에 맞춰 이보다 줄어든다. */
private val BellSize = 46.dp

/** 정본 `cornerRadius 34` ÷2. */
private val CompleteRadius = 17.dp
