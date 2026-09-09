package app.nursemate.timer.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import app.nursemate.core.data.timer.TimerAlarmScheduler
import app.nursemate.core.model.CareTimer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `AlarmManager` 로 만료 알람을 예약한다.
 *
 * ## `setAlarmClock` 을 쓴다
 * spec 이 **"앱을 완전히 종료한 상태에서도 만료 시각에 알린다"** 를 타이머의 최소 요건으로
 * 못박았다. 후보 중 이걸 지키는 건 `setAlarmClock` 과 `setExactAndAllowWhileIdle` 인데,
 * `setAlarmClock` 을 골랐다:
 * - Doze 를 완전히 관통한다(`setExactAndAllowWhileIdle` 은 저전력 상태에서 창이 늘어날 수 있다)
 * - 시스템이 **상태바에 알람 아이콘**을 띄워 준다 — 사용자가 "예약돼 있다"를 앱 밖에서 확인한다
 * - 의미상으로도 사용자가 인지하는 알람이라 이 API 가 맞다
 *
 * ## 정확 알람 권한이 없으면 예약하지 않는다
 * Android 12+ 는 `SCHEDULE_EXACT_ALARM` 을 요구하고, 사용자가 끌 수 있다. 권한이 없는데
 * 부정확 알람으로 대체하면 **"울릴 줄 알았는데 늦게 울리는"** 상태가 되는데, spec 은 그런
 * 반쯤 동작하는 알람을 더 위험하다고 본다. 그래서 조용히 대체하지 않고 예약을 건너뛰고,
 * 화면이 [canScheduleExact] 로 상태를 물어 권한 안내를 띄운다.
 */
@Singleton
class AlarmManagerTimerScheduler @Inject constructor(@param:ApplicationContext private val context: Context) :
    TimerAlarmScheduler {

    private val alarmManager: AlarmManager? get() = context.getSystemService()

    /** 정확 알람을 예약할 수 있는가. 화면이 권한 안내를 띄울지 판단하는 데 쓴다. */
    fun canScheduleExact(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else {
        true
    }

    override fun schedule(timer: CareTimer) {
        val manager = alarmManager ?: return
        if (!canScheduleExact()) return

        val pending = firePendingIntent(timer.id)
        // 같은 요청 코드·같은 액션이라 재예약하면 이전 것을 덮어쓴다.
        manager.setAlarmClock(
            AlarmManager.AlarmClockInfo(timer.endAtEpochMillis, showIntent(timer.id)),
            pending
        )
    }

    override fun cancel(timerId: String) {
        alarmManager?.cancel(firePendingIntent(timerId))
        clearNotification(timerId)
    }

    override fun dismiss(timerId: String) {
        alarmManager?.cancel(firePendingIntent(timerId))
        clearNotification(timerId)
    }

    /** 울리고 있던 알림을 내린다. 예약만 지우면 이미 뜬 알림은 남는다. */
    private fun clearNotification(timerId: String) {
        context.getSystemService<NotificationManager>()?.cancel(notificationId(timerId))
    }

    private fun firePendingIntent(timerId: String): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_FIRE
            putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(timerId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 상태바 알람 아이콘을 눌렀을 때 열리는 화면. */
    private fun showIntent(timerId: String): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(timerId),
        TimerAlarmIntents.openTimerTab(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        /**
         * 타이머 id(UUID 문자열)를 요청 코드·알림 id 로 쓴다.
         *
         * `hashCode` 라 충돌 가능성이 0은 아니지만, 동시에 도는 타이머가 수십 개를 넘지 않아
         * 실질적으로 부딪히지 않는다. 부딪히면 두 알람이 서로를 덮어쓰므로, 문제가 되면
         * 별도 정수 id 를 도메인에 넣어야 한다.
         */
        fun requestCode(timerId: String): Int = timerId.hashCode()

        fun notificationId(timerId: String): Int = timerId.hashCode()
    }
}
