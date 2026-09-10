package app.nursemate.wear.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.getSystemService
import app.nursemate.core.model.CareTimer
import app.nursemate.core.timer.TimerAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 워치가 **자기 만료 알람을 직접 건다** — 폰이 알려 주기를 기다리지 않는다.
 *
 * ## 왜 워치가 자기 알람을 갖나
 * 안드로이드는 **같은 패키지의 워치 앱이 깔리면 폰 알림 브리징을 끊는다**(실기기 확인).
 * 폰이 울려도 손목으로 넘어오는 게 없다. 게다가 연결이 끊기면 폰이 알려 줄 방법 자체가
 * 없어, 워치만 차고 있는 동안 만료를 통째로 놓친다. 같은 갤럭시 워치의 삼성 기본 타이머도
 * 자기 알람을 건다(`EXPLICIT_TIMER_ALERT`, `window=0`).
 *
 * ## 폰과 다른 점 둘
 * - **[AlarmManager.ELAPSED_REALTIME_WAKEUP] 을 쓴다.** 폰은 `setAlarmClock`(벽시계)인데,
 *   워치는 시계가 폰과 어긋날 수 있다. 남은 시간으로 환산해 걸면 시계 보정에 흔들리지 않는다.
 *   삼성 기본 타이머도 이 방식이다.
 * - **상태바 알람 아이콘용 `showIntent` 가 없다.** 워치에는 그 자리가 없다.
 *
 * ## 권한을 묻지 않는다
 * 매니페스트가 `USE_EXACT_ALARM` 을 선언하고, 그건 자동 허용이다. 폰처럼 사용자에게
 * 설정을 열게 하지 않는다 — 손목에서 할 일이 아니다.
 */
@Singleton
class WearTimerAlarmScheduler @Inject constructor(@param:ApplicationContext private val context: Context) :
    TimerAlarmScheduler {

    private val alarmManager: AlarmManager? get() = context.getSystemService()

    /**
     * `endAt` 을 **남은 시간**으로 바꿔 건다.
     *
     * 이미 지난 시각이면 0 으로 눌러 즉시 울린다 — 음수로 넘기면 시스템이 조용히 버린다.
     * 같은 요청 코드라 다시 부르면 이전 예약을 덮어쓴다.
     */
    // 린트는 `SCHEDULE_EXACT_ALARM` 만 찾고 `USE_EXACT_ALARM` 을 인정하지 않는다.
    // 실기기에서 실제로 허용되는 것을 확인했다 —
    // `exactAllowReason=policy_permission`, `window=0`(삼성 기본 워치 타이머와 같은 값).
    @SuppressLint("MissingPermission")
    override fun schedule(timer: CareTimer) {
        val manager = alarmManager ?: return
        val remaining = (timer.endAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0)
        manager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + remaining,
            firePendingIntent(timer.id)
        )
    }

    override fun cancel(timerId: String) {
        alarmManager?.cancel(firePendingIntent(timerId))
        clearNotification(timerId)
    }

    override fun dismiss(timerId: String) = cancel(timerId)

    /** 고아 알림을 걷는다. 판단 근거는 폰 구현과 같다 — 무엇이 떠 있는지 알 수 없어 통째로. */
    override fun dismissAllAlarms() {
        val manager = context.getSystemService<NotificationManager>()
        Log.i(TAG, "만료 알림 정리 (manager=${manager != null})")
        manager?.cancelAll()
    }

    private fun clearNotification(timerId: String) {
        context.getSystemService<NotificationManager>()
            ?.cancel(WearTimerNotifier.notificationId(timerId))
    }

    private fun firePendingIntent(timerId: String): PendingIntent {
        val intent = Intent(context, WearTimerAlarmReceiver::class.java).apply {
            action = WearTimerAlarmReceiver.ACTION_FIRE
            putExtra(WearTimerAlarmReceiver.EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            context,
            timerId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val TAG = "NM445"
    }
}
