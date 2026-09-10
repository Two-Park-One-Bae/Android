package app.nursemate.wear.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerState
import app.nursemate.wear.MainActivity
import app.nursemate.wear.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 워치에서 만료를 알린다 — spec §워치 "W3 만료 알림".
 *
 * ## 워치가 자기 알림을 띄워야 한다
 * spec 은 "폰 알람이 워치까지 전달되는 플랫폼이라면 워치 자체 알림을 예약하지 않는다"고 했다.
 * **Android 는 그런 플랫폼이 아니다.** 같은 패키지의 워치 앱이 설치되면 시스템이 폰 알림
 * 브리징을 자동으로 끊는다 — 워치 앱이 알아서 띄울 거라고 보기 때문이다. 실기기에서
 * 확인했다(폰 알림 9건, 워치 0건). 그래서 워치가 안 띄우면 **아무 일도 일어나지 않는다.**
 *
 * 중복 걱정은 없다. 브리징이 이미 끊겨 있어 한 번만 울린다.
 *
 * ## 진동은 여기서 걸지 않는다
 * spec §워치 — "울림 방식 설정과 무관하게 워치는 항상 햅틱". 그런데 **Wear 는 알림 채널의
 * 진동 패턴을 무시하고 자기 햅틱을 한 번만 재생한다**(실기기 확인). 그래서 진동은
 * [WearAlarmService] 가 `Vibrator` 로 직접 몬다. 채널에 진동을 켜 두면 그 위에 한 번 더
 * 겹쳐 울린다.
 */
@Singleton
class WearTimerNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    /**
     * 울리는 타이머에 맞춰 알림을 맞춘다.
     *
     * 스냅샷이 올 때마다 부른다 — 새로 울린 것은 띄우고, 사라진 것은 걷는다. 폰에서 [완료]를
     * 눌러 타이머가 지워져도 다음 스냅샷에서 자동으로 꺼진다.
     */
    @SuppressLint("MissingPermission")
    fun sync(timers: List<CareTimer>) {
        if (!canPost()) {
            Log.w(TAG, "알림 권한이 없어 만료를 알리지 못했다")
            return
        }
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)
        val ringing = timers.filter { it.state == TimerState.RINGING }

        // 더 이상 울리지 않는 것은 걷는다. 이게 없으면 폰에서 완료해도 워치에 남는다.
        manager.activeNotifications
            .filter { it.id != 0 && ringing.none { timer -> notificationId(timer.id) == it.id } }
            .forEach { manager.cancel(it.id) }

        ringing.forEach { timer -> manager.notify(notificationId(timer.id), build(timer)) }
    }

    /**
     * 포그라운드 서비스가 띄우는 알림 — 여기에 진행 중 표시([OngoingActivity])를 얹는다.
     *
     * Wear 품질요건 WO-V4 — 1분 넘게 이어지는 일은 워치 페이스와 최근 목록에 보여야 한다.
     * 만료 알람이 [완료] 까지 지속되므로 여기 해당한다.
     */
    fun foregroundNotification(timer: CareTimer): Notification {
        ensureChannel()
        val builder = base(timer)
        val touch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        OngoingActivity.Builder(context, FOREGROUND_ID, builder)
            // 정적 아이콘과 터치 인텐트는 **필수**다 — 없으면 IllegalArgumentException.
            .setStaticIcon(R.drawable.nm_ic_bell_ring)
            .setTouchIntent(touch)
            .setStatus(Status.forPart(Status.TextPart(timer.alarmTitle)))
            .build()
            .apply(context)
        return builder.build()
    }

    private fun build(timer: CareTimer) = base(timer).build()

    private fun base(timer: CareTimer) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.nm_ic_bell_ring)
        // spec §만료·알람 — title = `❗ [분류] 라벨`. 폰과 같은 문구를 쓴다.
        .setContentTitle(timer.alarmTitle)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true)
        .setAutoCancel(false)
        // 폰 알림으로 다시 브리징되지 않게 못박는다. 시스템이 이미 끊지만, 기본 동작에
        // 기대면 워치 앱을 지웠다 깔 때 같은 만료가 두 번 울릴 여지가 남는다.
        .setLocalOnly(true)
        .addAction(0, COMPLETE_LABEL, WearTimerActionReceiver.completeIntent(context, timer.id))

    /**
     * ⚠️ **채널은 만들고 나면 진동 설정을 못 바꾼다.** 지우고 같은 id 로 다시 만들어도 옛
     * 설정이 되살아난다 — 바꿔야 하면 id 에 붙은 번호를 올린다(폰에서 겪은 일이다).
     */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>()
        if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "처치 타이머 만료", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "타이머가 끝나면 손목을 울립니다"
                    setSound(null, null)
                    // ⚠️ 끈다. 켜 두면 서비스가 모는 진동 위에 시스템 햅틱이 한 번 더 겹친다.
                    enableVibration(false)
                }
            )
        }
    }

    private fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    companion object {
        /** 타이머 id 를 알림 id 로 — 폰의 `AlarmManagerTimerScheduler` 와 같은 방식이다. */
        fun notificationId(timerId: String): Int = timerId.hashCode() and Int.MAX_VALUE

        /** 포그라운드 서비스가 쓰는 고정 id. 울리는 것이 여럿이어도 대표 하나만 띄운다. */
        const val FOREGROUND_ID = 444_001

        private const val TAG = "NM444"
        private const val COMPLETE_LABEL = "완료"

        /**
         * 채널 id 의 `_v2` — 진동을 바꾸려면 번호를 올려야 한다(위 주석 참고).
         *
         * `_v1` 은 3회짜리(2초), `_v2` 는 폰과 같은 60초 패턴이었다. **둘 다 한 번만 울렸다** —
         * Wear 가 채널 패턴을 무시한다. 이제 채널은 진동을 끄고 서비스가 직접 몬다.
         */
        private const val CHANNEL_ID = "wear_timer_alarm_v2"
    }
}
