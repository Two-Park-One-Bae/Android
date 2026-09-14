package app.nursemate.timer.alarm

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.CareTimer

/**
 * 만료 알람 알림 — [TimerAlarmService] 가 자기 포그라운드 알림으로 올린다.
 *
 * ## 소리·진동은 여기서 내지 않는다
 * 알림 채널의 소리·진동은 **링어 모드에 걸려 무음에서 통째로 막힌다**(실기기 확인).
 * spec §만료·알람은 "기기 무음·벨소리 스위치와 무관하게" 울리기를 요구하므로 채널로는
 * 지킬 수 없다. 그래서 채널은 조용히 두고([TimerAlarmChannels]) 서비스가 직접 낸다 —
 * 워치가 쓰는 길과 같다(`WearAlarmService`).
 *
 * ## 하나씩 순서대로
 * 여러 개가 함께 울려도 알림은 **맨 앞 하나**뿐이다(spec §만료·알람). 서비스가 자기 알림을
 * 갈아 끼우므로 id 가 하나로 고정된다 — 각자 올리면 상단 배너가 쌓이고 **맨 위가 나중에
 * 울린 것**이 되어 [완료] 순서가 뒤집힌다.
 */
object TimerAlarmNotification {

    /** 서비스가 쓰는 고정 id. 맨 앞이 바뀌면 같은 id 를 갈아 끼운다. */
    const val ID = 442_001

    /**
     * @param alertAgain 헤드업·풀스크린을 **다시** 띄울지. 맨 앞이 바뀌었거나 사용자가
     *   [지우기] 로 지운 것을 되돌릴 때만 `true` 다. 늦게 만료한 다른 타이머 때문에 같은
     *   알림을 다시 올리는 경우까지 띄우면 **이미 본 배너가 또 튀어나온다**(실기기 확인).
     */
    fun build(context: Context, timer: CareTimer, alertAgain: Boolean): Notification {
        val complete = TimerAlarmReceiver.actionPendingIntent(
            context,
            timer.id,
            TimerAlarmReceiver.ACTION_COMPLETE
        )
        val open = PendingIntent.getActivity(
            context,
            AlarmManagerTimerScheduler.requestCode(timer.id),
            TimerAlarmIntents.openTimerTab(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, TimerAlarmChannels.ALARM_ID)
            .setSmallIcon(DsR.drawable.nm_ic_timer)
            // spec §만료·알람 — title = `❗ [분류] 라벨`
            .setContentTitle(timer.alarmTitle)
            .setContentText(COMPLETE_HINT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            // 포그라운드 서비스 알림이라 스와이프로 지워지지 않는다 — 끄는 길은 [완료] 하나다.
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, COMPLETE_LABEL, complete)
            // ⚠️ **첫 게시에는 절대 붙이면 안 된다.** 붙이면 알림 판정 자체가 죽어 소리도
            // 헤드업도 나오지 않았다(실기기: 게시는 됐는데 판정 로그가 없었다).
            .setOnlyAlertOnce(!alertAgain)
            // [지우기] 로 사라지면 되돌린다 — 끄는 길은 [완료] 하나여야 한다.
            .setDeleteIntent(TimerAlarmReceiver.dismissPendingIntent(context, timer.id))
            // 잠금화면 풀스크린 — **알람 전용 화면**을 띄운다(spec §만료·알람 "잠금: 풀스크린").
            // `MainActivity` 를 넘기면 `showWhenLocked` 가 없어 시스템이 조용히 헤드업으로
            // 낮춘다. Android 14+ 는 권한도 있어야 한다.
            .setFullScreenIntent(fullScreenIntent(context, timer), true)
            .build()
    }

    /** 잠금화면을 덮는 알람 화면을 여는 인텐트. */
    private fun fullScreenIntent(context: Context, timer: CareTimer): PendingIntent = PendingIntent.getActivity(
        context,
        AlarmManagerTimerScheduler.requestCode(timer.id) + TimerAlarmReceiver.FULL_SCREEN_OFFSET,
        TimerAlarmActivity.intent(context, timer.id, timer.alarmTitle),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        // Android 14+ 는 `PendingIntent` 로 액티비티를 띄울 때 **만든 쪽의 명시적 허용**을
        // 본다. 없으면 화면이 꺼져 있을 때 `BAL_BLOCK` 으로 막힌다.
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

    private const val COMPLETE_LABEL = "완료"
    private const val COMPLETE_HINT = "완료를 누르면 알람이 꺼집니다"
}
