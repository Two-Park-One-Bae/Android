package app.nursemate.timer.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * 만료 알람용 알림 채널.
 *
 * ## 왜 채널이 방식마다 하나씩인가
 * 안드로이드는 **채널을 만든 뒤에 소리·진동을 코드로 바꿀 수 없다** — 사용자만 시스템 설정에서
 * 바꾼다. 그래서 울림 방식을 하나의 채널로 전환할 수 없고, 방식마다 채널을 따로 두고
 * **어느 채널로 낼지**를 고르는 방식으로 만든다.
 *
 * ## '소리'는 기기 무음을 무시한다
 * spec 이 "기기 무음·벨소리 스위치와 무관하게 항상 소리로 울린다(시계 알람과 동일)"고
 * 요구한다. `USAGE_ALARM` 으로 **알람 스트림**을 쓰면 무음 모드에서도 울린다 — 미디어·알림
 * 스트림을 쓰면 무음에서 조용해져 요구를 못 지킨다.
 *
 * ## '진동'은 Android 에만 있다
 * spec 본문은 소리·무음 2가지인데, 그 근거는 iOS 제약 #7(시스템 알람이 항상 진동하고 끌 수
 * 없어 '진동'과 '무음'이 구분되지 않는다)이다. Android 는 진동이 채널 속성이라 소리와 따로
 * 끄고 켠다 — 제약이 없어 3가지를 준다(2026-09-09 결정, spec 본문 개정 요청 대상).
 */
object TimerAlarmChannels {

    /**
     * 만료 알람 채널 — **하나뿐이고 조용하다.**
     *
     * 전에는 울림 방식마다 채널을 두고(`소리`·`진동`·`무음`) 채널의 소리·진동에 맡겼는데,
     * 그건 **링어 모드에 걸려 무음에서 통째로 막힌다.** spec §만료·알람이 "기기 무음과
     * 무관하게"를 요구하므로 채널로는 지킬 수 없다. 지금은 [TimerAlarmService] 가 알람
     * 스트림·알람 진동으로 직접 내고, 채널은 **표시만** 맡는다.
     *
     * ⚠️ 채널에 소리나 진동을 켜지 않는다 — 켜면 같은 만료가 두 번 울린다.
     *
     * id 의 `_v3` — 채널 설정은 만든 뒤 못 바꾸고, 지웠다 같은 id 로 다시 만들면 시스템이
     * 옛 설정을 되살린다. 방식별 채널(`_v2`)에서 넘어오며 올렸다.
     */
    const val ALARM_ID = "timer_alarm_v3"

    /**
     * 진행 중 표시용 — 만료 알람과 **채널이 달라야 한다.**
     *
     * 이건 종일 떠 있는 조용한 알림이라 헤드업이 없어야 한다. 만료 알람과 같은 채널에 두면
     * 사용자가 한쪽을 끄려다 다른 쪽까지 끈다.
     */
    const val ONGOING_ID = "timer_ongoing_v1"

    private val LEGACY_IDS = listOf(
        "timer_alarm_sound",
        "timer_alarm_silent",
        "timer_alarm_sound_v2",
        "timer_alarm_vibrate_v2",
        "timer_alarm_silent_v2"
    )

    /**
     * 채널을 만든다. 이미 있으면 시스템이 무시하므로 매번 불러도 된다.
     *
     * 앱 시작 시 한 번 부른다 — 알람이 울리는 시점에 만들면 늦다.
     */
    fun ensure(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        LEGACY_IDS.forEach(manager::deleteNotificationChannel)

        val alarm = NotificationChannel(
            ALARM_ID,
            "처치 타이머 알람",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "타이머가 끝나면 알립니다."
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }

        // 진행 중 표시는 조용해야 한다 — IMPORTANCE_LOW 라 헤드업으로 튀어나오지 않는다.
        val ongoing = NotificationChannel(
            ONGOING_ID,
            "진행 중인 타이머",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "타이머가 도는 동안 남은 시간을 보여 줍니다."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        listOf(alarm, ongoing).forEach(manager::createNotificationChannel)
    }
}
