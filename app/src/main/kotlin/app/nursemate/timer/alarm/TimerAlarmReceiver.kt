package app.nursemate.timer.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.nursemate.core.data.timer.TimerRepository
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerState
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
        val pending = goAsync()
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID)

        scope.launch {
            try {
                when (intent.action) {
                    // 진행 중 알림은 묶음이라 어느 타이머인지 없다.
                    ACTION_ONGOING_DISMISSED -> restoreOngoing(context)

                    ACTION_ALARM_DISMISSED -> timerId?.let { restoreAlarm(context, it) }

                    ACTION_FIRE -> timerId?.let { fire(context, it) }

                    ACTION_COMPLETE, ACTION_STOP -> timerId?.let { repository.remove(it) }

                    ACTION_PAUSE -> timerId?.let { repository.pause(it) }

                    ACTION_RESUME -> timerId?.let { repository.resume(it) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "알람 처리 실패 ($timerId)", t)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 알림창의 [지우기] 로 사라진 표시를 되돌린다.
     *
     * `setOngoing(true)` 는 **스와이프만** 막는다(실기기 확인). [지우기] 버튼은 그것까지
     * 걷어 가는데, 우리 알림은 지워지고 나면 **다음 상태 변화까지 다시 뜨지 않는다** —
     * 목록이 바뀔 때만 그리기 때문이다. 2시간짜리가 돌고 있으면 2시간 동안 앱 밖에
     * 아무 표시가 없다.
     *
     * 만료 알림은 더하다. spec 이 "[완료] 를 누를 때까지 지속"이라고 못박았는데 [지우기]
     * 한 번에 사라지면 그대로 놓친다.
     */
    @SuppressLint("MissingPermission")
    private suspend fun restoreOngoing(context: Context) {
        if (!canPostNotifications(context)) return
        val timers = repository.timers.first()
        val notification = TimerOngoingNotification.build(context, timers, System.currentTimeMillis())
        if (notification == null) return
        Log.i(TAG, "[지우기] 로 사라진 진행 중 표시를 되돌린다 (${timers.size}개)")
        NotificationManagerCompat.from(context).notify(TimerOngoingNotification.ID, notification)
    }

    /** 아직 울리는 중이면 만료 알림을 되돌린다. 이미 [완료] 됐으면 그대로 둔다. */
    private suspend fun restoreAlarm(context: Context, timerId: String) {
        val timer = repository.timers.first().firstOrNull { it.id == timerId } ?: return
        if (timer.state != TimerState.RINGING) return
        Log.i(TAG, "[지우기] 로 사라진 만료 알림을 되돌린다 ($timerId)")
        notify(context, timer)
    }

    private suspend fun fire(context: Context, timerId: String) {
        repository.markRinging(timerId)
        // 저장소에 조회 함수를 늘리지 않고 기존 흐름에서 한 번만 읽는다.
        val timer = repository.timers.first().firstOrNull { it.id == timerId } ?: return
        // 울림으로 올라가지 않았다면 아직 만료 전이다 — 만료 직전에 [+1분] 을 눌러 `endAt` 이
        // 밀렸는데 옛 알람이 뒤늦게 발화한 경우다. 알림을 띄우면 안 된다.
        if (timer.state != TimerState.RINGING) return
        notify(context, timer)
    }

    // 권한은 바로 아래에서 검사한다. lint 가 호출 지점을 따라가지 못해 오탐을 낸다.
    @SuppressLint("MissingPermission")
    private suspend fun notify(context: Context, timer: CareTimer) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "알림 권한이 없어 만료를 알리지 못했다 (${timer.id})")
            return
        }
        val channel = TimerAlarmChannels.channelFor(repository.alertMode.first())

        val complete = actionPendingIntent(context, timer.id, ACTION_COMPLETE)

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
            // [지우기] 로 사라지면 되돌린다 — 끄는 길은 [완료] 하나여야 한다.
            .setDeleteIntent(dismissPendingIntent(context, timer.id))
            // 잠금화면 풀스크린 — **알람 전용 화면**을 띄운다(spec §만료·알람 "잠금: 풀스크린").
            // 여기에 `MainActivity` 를 넘기면 `showWhenLocked` 가 없어 시스템이 조용히
            // 헤드업으로 낮춘다. Android 14+ 는 권한도 있어야 한다.
            .setFullScreenIntent(fullScreenPendingIntent(context, timer), true)
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

        /** 진행 중 알림의 조작 — spec §앱 밖 진행 중 표시(앱을 열지 않고 다룬다). */
        const val ACTION_PAUSE = "app.nursemate.timer.PAUSE"
        const val ACTION_RESUME = "app.nursemate.timer.RESUME"
        const val ACTION_STOP = "app.nursemate.timer.STOP"

        /** 알림창의 [지우기] 로 사라졌을 때 시스템이 보내 준다. */
        const val ACTION_ONGOING_DISMISSED = "app.nursemate.timer.ONGOING_DISMISSED"
        const val ACTION_ALARM_DISMISSED = "app.nursemate.timer.ALARM_DISMISSED"

        const val EXTRA_TIMER_ID = "timer_id"

        /** 잠금화면을 덮는 알람 화면을 여는 인텐트. */
        private fun fullScreenPendingIntent(context: Context, timer: CareTimer): PendingIntent =
            PendingIntent.getActivity(
                context,
                AlarmManagerTimerScheduler.requestCode(timer.id) + FULL_SCREEN_OFFSET,
                TimerAlarmActivity.intent(context, timer.id, timer.alarmTitle),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                // 워치에서 이것 없이 `BAL_BLOCK` 으로 막혔다. 폰은 지금 뜨지만 같은 규칙
                // 아래 있으므로 함께 열어 둔다 — Android 14+ 는 `PendingIntent` 로 액티비티를
                // 띄울 때 **만든 쪽의 명시적 허용**을 요구한다.
                backgroundLaunchOptions()
            )

        private fun backgroundLaunchOptions(): Bundle? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
            } else {
                null
            }

        /** 만료 알림이 [지우기] 로 사라졌을 때 되돌리기 위한 인텐트. */
        fun dismissPendingIntent(context: Context, timerId: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            AlarmManagerTimerScheduler.requestCode(timerId) + DISMISS_OFFSET,
            Intent(context, TimerAlarmReceiver::class.java).apply {
                action = ACTION_ALARM_DISMISSED
                putExtra(EXTRA_TIMER_ID, timerId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /** 진행 중 알림(묶음)이 사라졌을 때. 타이머 하나를 가리키지 않아 고정 코드를 쓴다. */
        fun ongoingDismissPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            ONGOING_DISMISS_REQUEST_CODE,
            Intent(context, TimerAlarmReceiver::class.java).setAction(ACTION_ONGOING_DISMISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /**
         * 액션마다 `PendingIntent` 요청 코드를 달리 준다.
         *
         * 같은 코드를 쓰면 시스템이 **하나로 합쳐** 나중 것이 앞 것을 덮는다 — 일시정지를
         * 눌렀는데 정지가 되는 식이다.
         */
        fun actionPendingIntent(context: Context, timerId: String, action: String): PendingIntent {
            val offset = when (action) {
                ACTION_COMPLETE -> COMPLETE_OFFSET
                ACTION_PAUSE -> PAUSE_OFFSET
                ACTION_RESUME -> RESUME_OFFSET
                else -> STOP_OFFSET
            }
            return PendingIntent.getBroadcast(
                context,
                AlarmManagerTimerScheduler.requestCode(timerId) + offset,
                Intent(context, TimerAlarmReceiver::class.java).apply {
                    this.action = action
                    putExtra(EXTRA_TIMER_ID, timerId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private const val TAG = "TimerAlarm"
        private const val COMPLETE_LABEL = "완료"
        private const val COMPLETE_HINT = "완료를 누르면 알람이 꺼집니다"

        /** [완료] PendingIntent 가 발화용과 같은 요청 코드를 쓰지 않도록 띄운다. */
        private const val COMPLETE_OFFSET = 1
        private const val PAUSE_OFFSET = 2
        private const val RESUME_OFFSET = 3
        private const val STOP_OFFSET = 4
        private const val DISMISS_OFFSET = 5
        private const val FULL_SCREEN_OFFSET = 6

        /** 타이머 요청 코드와 겹치지 않도록 멀리 띄운다. */
        private const val ONGOING_DISMISS_REQUEST_CODE = 990_001
    }
}
