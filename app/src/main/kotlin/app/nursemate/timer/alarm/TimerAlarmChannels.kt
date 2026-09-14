package app.nursemate.timer.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * 처치 타이머의 알림 채널.
 *
 * ## 채널은 **표시만** 맡는다
 * 소리도 진동도 채널이 내지 않는다. **채널의 소리·진동은 링어 모드에 걸려 무음에서 통째로
 * 막히는데**, spec §만료·알람은 "기기 무음·벨소리 스위치와 무관하게 울린다(시계 알람과
 * 동일)"를 요구한다. 그래서 [TimerAlarmService] 가 알람 스트림(`MediaPlayer`)과 알람
 * 진동(`Vibrator`)으로 직접 낸다. **울림 주체가 채널이 아니라 서비스라는 점**이 워치와
 * 같다 — 워치는 진동만 낸다(`WearAlarmService`).
 *
 * 채널이 하는 일은 알림을 어디에 어떤 중요도로 띄울지 정하는 것뿐이고, 그래서 **만료용
 * 하나([ALARM_ID])와 진행 중용 하나([ONGOING_ID])** 로 끝난다.
 *
 * ## 울림 방식이 3가지인 것은 채널과 무관하다
 * spec 본문은 소리·무음 2가지인데, 그 근거는 iOS 제약 #7(시스템 알람이 항상 진동하고 끌 수
 * 없어 '진동'과 '무음'이 구분되지 않는다)이다. Android 는 그 제약이 없어 3가지를 준다
 * (2026-09-09 결정, spec 본문 개정 요청 대상). 고른 방식은 [TimerAlarmService] 가 읽어
 * 무엇을 낼지 정한다 — 예전처럼 방식마다 채널을 두지 않는다.
 *
 * ## 채널은 만든 뒤에 못 바꾼다
 * 소리·진동 설정은 사용자만 시스템 설정에서 바꾼다. 앱이 바꾸려면 **id 를 올려 새로 만드는
 * 수밖에 없고**, 지웠다 같은 id 로 다시 만들면 시스템이 옛 설정을 되살린다. 방식별 채널
 * (`_v2` 셋)에서 넘어오며 [ALARM_ID] 를 `_v3` 으로 올린 이유다. [LEGACY_IDS] 가 옛 것을
 * 지운다 — 안 지우면 사용자 알림 설정에 죽은 채널이 남는다.
 */
object TimerAlarmChannels {

    /**
     * 만료 알람 채널 — **하나뿐이고 조용하다.**
     *
     * ⚠️ 채널에 소리나 진동을 켜지 않는다 — 켜면 같은 만료가 두 번 울린다.
     *
     * `_v3` 인 이유는 클래스 KDoc 「채널은 만든 뒤에 못 바꾼다」에 있다.
     */
    const val ALARM_ID = "timer_alarm_v3"

    /**
     * 진행 중 표시용 — 만료 알람과 **채널이 달라야 한다.**
     *
     * 이건 종일 떠 있는 조용한 알림이라 헤드업이 없어야 한다. 만료 알람과 같은 채널에 두면
     * 사용자가 한쪽을 끄려다 다른 쪽까지 끈다.
     */
    const val ONGOING_ID = "timer_ongoing_v1"

    /** 지난 빌드가 만든 채널. 지우지 않으면 사용자 알림 설정에 그대로 남는다. */
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
