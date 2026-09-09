package app.nursemate.timer.alarm

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.nursemate.core.data.timer.TimerRepository
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.CareTimer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 만료 시각에 깨어나 알람을 띄운다. [완료] 를 받아 타이머를 지우기도 한다.
 *
 * ## [완료] 를 누를 때까지 지속시킨다
 * spec 이 "중간에 저절로 사라지거나 임의로 끄는 상태는 없다"고 요구한다. 알림 채널의 소리는
 * **한 번만** 재생되므로 그것만으로는 부족한데, 포그라운드 서비스로 소리를 반복 재생하는
 * 대신 **`FLAG_INSISTENT`** 를 쓴다 — 알림이 취소될 때까지 시스템이 소리를 반복해 준다.
 * 서비스·미디어 플레이어를 띄우지 않아 배터리·수명주기 문제가 없다.
 *
 * `setOngoing(true)` 로 스와이프 해제도 막는다. 끄는 길은 [완료] 하나뿐이다.
 */
@AndroidEntryPoint
class TimerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TimerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // goAsync() 를 쓰면 무슨 일이 있어도 finish() 에 도달해야 한다 — 안 그러면 시스템이
    // 리시버를 붙잡고 있다가 ANR 로 죽인다. 그래서 여기서는 넓게 잡는 게 맞다.
    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        val pending = goAsync()

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(context, timerId)
                    ACTION_COMPLETE -> repository.remove(timerId)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "알람 처리 실패 ($timerId)", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, timerId: String) {
        repository.markRinging(timerId)
        // 저장소에 조회 함수를 늘리지 않고 기존 흐름에서 한 번만 읽는다.
        val timer = repository.timers.first().firstOrNull { it.id == timerId } ?: return
        notify(context, timer)
    }

    private suspend fun notify(context: Context, timer: CareTimer) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "알림 권한이 없어 만료를 알리지 못했다 (${timer.id})")
            return
        }
        val channel = TimerAlarmChannels.channelFor(repository.alertMode.first())

        val complete = PendingIntent.getBroadcast(
            context,
            AlarmManagerTimerScheduler.requestCode(timer.id) + COMPLETE_OFFSET,
            Intent(context, TimerAlarmReceiver::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_TIMER_ID, timer.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val open = PendingIntent.getActivity(
            context,
            AlarmManagerTimerScheduler.requestCode(timer.id),
            TimerAlarmIntents.openTimerTab(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(DsR.drawable.nm_ic_timer)
            // spec §만료·알람 — title = `❗ [분류] 라벨`
            .setContentTitle(timer.alarmTitle)
            .setContentText(COMPLETE_HINT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            // 스와이프로 지워지지 않게 한다 — 끄는 길은 [완료] 하나다.
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, COMPLETE_LABEL, complete)
            // 잠금화면 풀스크린. Android 14+ 는 권한이 없으면 시스템이 헤드업으로 낮춰 표시한다.
            .setFullScreenIntent(open, true)
            .build()

        // 취소될 때까지 소리를 반복한다(spec: [완료] 까지 지속).
        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        NotificationManagerCompat.from(context)
            .notify(AlarmManagerTimerScheduler.notificationId(timer.id), notification)
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_FIRE = "app.nursemate.timer.ALARM_FIRE"
        const val ACTION_COMPLETE = "app.nursemate.timer.ALARM_COMPLETE"
        const val EXTRA_TIMER_ID = "timer_id"

        private const val TAG = "TimerAlarm"
        private const val COMPLETE_LABEL = "완료"
        private const val COMPLETE_HINT = "완료를 누르면 알람이 꺼집니다"

        /** [완료] PendingIntent 가 발화용과 같은 요청 코드를 쓰지 않도록 띄운다. */
        private const val COMPLETE_OFFSET = 1
    }
}
