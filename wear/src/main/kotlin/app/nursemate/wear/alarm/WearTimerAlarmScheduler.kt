package app.nursemate.wear.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import app.nursemate.core.model.CareTimer
import app.nursemate.core.timer.TimerAlarmScheduler
import app.nursemate.wear.MainActivity
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
 * ## 폰과 같은 `setAlarmClock` 을 쓴다
 *
 * 한때 경과시간 기준 `setExactAndAllowWhileIdle` 을 썼다. 예약해 둔 사이에 시계가 보정돼도
 * 발화가 안 밀리는 이점이 있고 삼성 기본 타이머도 그 방식이다. 그런데도 바꾼 이유는 둘이다.
 *
 * 1. **폰과 같은 모델로 둔다.** 두 표면이 같은 API 를 쓰면 복제·재예약·복구를 한 가지로
 *    설명할 수 있다. 갈라 두면 "워치만 왜 다른가"를 매번 따져야 한다.
 * 2. **알람과 화면이 같은 시계를 본다.** `endAt` 은 벽시계 값이고 화면도 그것으로 남은
 *    시간을 센다(`CareTimer.remainingAt`). 경과시간으로 걸면 둘의 기준이 갈려, 시계가
 *    보정되면 숫자와 울림이 그 폭만큼 어긋났다. 이제는 어긋날 수 없다.
 *
 * 대가로 예약 뒤의 시계 보정에 노출된다. 워치가 폰과 시각을 맞출 때 생기는 폭이라 작고,
 * 위 2번이 그 폭을 화면에도 똑같이 반영해 준다.
 *
 * ⚠️ **알람 화면이 뜨는 것과는 무관하다.** `setAlarmClock` 이면 만료 때 액티비티가 뜨리라
 * 보고 바꿔 봤지만 아니었다 — `RTC_WAKEUP flags=0x3` 으로 폰과 똑같이 걸어도 앱이 직접
 * 부르는 시작은 `BAL_BLOCK` 이다. 화면은 **알림이 알려질 때 시스템이** 띄운다
 * ([WearTimerNotifier] 참고).
 *
 * ## 권한을 묻지 않는다
 * 매니페스트가 `USE_EXACT_ALARM` 을 선언하고, 그건 자동 허용이다. 폰처럼 사용자에게
 * 설정을 열게 하지 않는다 — 손목에서 할 일이 아니다.
 *
 * ## Doze 쿼터에 걸리지 않는다
 * `setExactAndAllowWhileIdle` 은 Doze 중 앱당 발화 횟수 제한을 받는다. 예전 상수인
 * **「9분에 한 번」을 걱정할 수 있는데, 그건 `allow_while_idle_compat_quota` 로 targetSdk 가
 * 낮은 앱에만 적용된다.** 이 기기의 값은 이렇다(실기기 `dumpsys alarm`):
 *
 *     allow_while_idle_quota        = 72   / 1시간
 *     allow_while_idle_compat_quota = 7    / 1시간
 *
 * 병동에서 서너 개를 같이 돌리는 게 이 기능의 전제라 실제로 확인했다 — 깊은 Doze(`IDLE`)
 * 상태에서 8초 간격 세 개를 걸었더니 **하나도 밀리지 않고 전부 제때 울렸다.**
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
        manager.setAlarmClock(
            AlarmManager.AlarmClockInfo(timer.endAtEpochMillis, showPendingIntent()),
            firePendingIntent(timer.id)
        )
    }

    /** 알람 시계 자리를 눌렀을 때 열 화면. 워치엔 그 자리가 없지만 API 가 요구한다. */
    private fun showPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

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
