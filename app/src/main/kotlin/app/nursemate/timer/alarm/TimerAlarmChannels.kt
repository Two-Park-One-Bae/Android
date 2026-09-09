package app.nursemate.timer.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.content.getSystemService
import app.nursemate.core.model.AlertMode

/**
 * 만료 알람용 알림 채널.
 *
 * ## 왜 채널이 두 개인가
 * 안드로이드는 **채널을 만든 뒤에 소리를 코드로 바꿀 수 없다** — 사용자만 시스템 설정에서
 * 바꾼다. 그래서 울림 방식(소리·무음)을 하나의 채널로 전환할 수 없고, 방식마다 채널을
 * 따로 두고 **어느 채널로 낼지**를 고르는 방식으로 만든다.
 *
 * ## '소리'는 기기 무음을 무시한다
 * spec 이 "기기 무음·벨소리 스위치와 무관하게 항상 소리로 울린다(시계 알람과 동일)"고
 * 요구한다. `USAGE_ALARM` 으로 **알람 스트림**을 쓰면 무음 모드에서도 울린다 — 미디어·알림
 * 스트림을 쓰면 무음에서 조용해져 요구를 못 지킨다.
 */
object TimerAlarmChannels {

    const val SOUND_ID = "timer_alarm_sound"
    const val SILENT_ID = "timer_alarm_silent"

    fun channelFor(mode: AlertMode): String = if (mode == AlertMode.SOUND) SOUND_ID else SILENT_ID

    /**
     * 두 채널을 만든다. 이미 있으면 시스템이 무시하므로 매번 불러도 된다.
     *
     * 앱 시작 시 한 번 부른다 — 알람이 울리는 시점에 만들면 늦다.
     */
    fun ensure(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val sound = NotificationChannel(
            SOUND_ID,
            "처치 타이머 알람",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "타이머가 끝나면 소리로 알립니다."
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAudio)
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val silent = NotificationChannel(
            SILENT_ID,
            "처치 타이머 알람 (무음)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "타이머가 끝나면 소리 없이 알립니다."
            setSound(null, null)
            // '무음'은 알림을 끄는 게 아니라 소리만 끈 조용한 알림이다(spec §울림 방식).
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(sound)
        manager.createNotificationChannel(silent)
    }
}
