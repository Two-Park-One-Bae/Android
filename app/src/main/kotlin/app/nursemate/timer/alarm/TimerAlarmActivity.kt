package app.nursemate.timer.alarm

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.nursemate.core.data.timer.TimerRepository
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.TimerState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 잠금화면을 덮는 만료 알람 화면 — 정본 「타이머 / 시스템 알람 — 잠금화면」.
 *
 * ## 왜 알림만으로는 안 되는가
 * iOS 는 시스템이 전체 화면 알람을 그려 준다. Android 에는 그런 것이 없어서 **우리가
 * 액티비티를 만들어 `setFullScreenIntent` 로 띄운다.**
 *
 * ⚠️ 그리고 **`showWhenLocked` 가 없으면 잠금화면 위로 못 올라온다.** 예전에는 이 자리에
 * `MainActivity` 를 넘겼는데, 그 액티비티에는 그 속성이 없어 시스템이 조용히 헤드업 알림으로
 * 낮췄다 — 잠금화면에서는 아무것도 안 보였다(실기기에서 확인하고 이 화면을 만들었다).
 *
 * 앱 전체를 잠금화면 위에 띄우지 않는 이유도 있다. 병동에서 남의 눈에 환자 정보가 닿으면
 * 안 되므로, 이 화면은 **처치 키워드와 [완료] 하나**만 보여 준다.
 */
@AndroidEntryPoint
class TimerAlarmActivity : ComponentActivity() {

    @Inject lateinit var repository: TimerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 매니페스트 속성만으로는 이미 켜진 화면에서 안 통한다 — 코드로도 켠다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContent {
            // ⚠️ **인텐트에 실린 타이머 하나에 매달리지 않는다.** 그러면 나중에 울린 것이
            // 앞 것을 덮고, [완료] 를 누르면 화면이 닫혀 **아직 울리는 앞 알람이 화면 없이
            // 남는다.** spec §만료·알람은 "하나씩 순서대로"를 요구한다.
            val timers by repository.timers.collectAsStateWithLifecycle(emptyList())
            val ringing = remember(timers) {
                timers.filter { it.state == TimerState.RINGING }.sortedBy { it.endAtEpochMillis }
            }
            val current = ringing.firstOrNull()

            LaunchedEffect(current) {
                if (current == null) finish()
            }

            if (current != null) {
                AlarmScreen(title = current.alarmTitle, onComplete = { complete(current.id) })
            }
        }
    }

    /** 화면은 여기서 닫지 않는다 — 지워지면 목록이 바뀌고, 남은 것이 있으면 그것으로 이어진다. */
    private fun complete(timerId: String) {
        lifecycleScope.launch { repository.remove(timerId) }
    }

    companion object {
        const val EXTRA_TIMER_ID = "timer_id"
        const val EXTRA_TITLE = "title"

        fun intent(context: Context, timerId: String, title: String): Intent =
            Intent(context, TimerAlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TIMER_ID, timerId)
                .putExtra(EXTRA_TITLE, title)
    }
}

/** 정본 프레임 그대로 — 배경 `#0F172A`, padding `[100, 24, 64, 24]`. */
@Composable
private fun AlarmScreen(title: String, onComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(start = 24.dp, end = 24.dp, top = 100.dp, bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NmColor.Primary.C500),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(DsR.drawable.nm_ic_timer),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text("널스메이트", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppName)
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TitleColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(BellBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_bell_ring),
                contentDescription = null,
                tint = NmColor.Warning.C500,
                modifier = Modifier.size(52.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(NmColor.Warning.C600)
                    .clickable(onClick = onComplete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(DsR.drawable.nm_ic_check),
                    contentDescription = "완료",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.size(8.dp))
            Text("완료", fontSize = 13.sp, color = AppName)
        }
    }
}

/** 정본 `#0F172A` — 앱의 밝은 배경과 달리 알람은 어둡다(밤 근무에 눈이 부시지 않게). */
private val Background = Color(0xFF0F172A)
private val TitleColor = Color(0xFFF8FAFC)
private val AppName = Color(0xFF94A3B8)

/** 정본 `#422006` — 종 뒤의 원. */
private val BellBackground = Color(0xFF422006)
