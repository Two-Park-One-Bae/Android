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
import app.nursemate.core.model.CareTimerTransitions
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
 * ## 만료 화면은 시스템이 그린다
 * 정본 「W3 만료」를 우리 액티비티로 띄우려 했지만, 화면이 꺼진 워치에서는 백그라운드
 * 액티비티 시작(BAL)이 막혀 되지 않는다. 옵트인·리시버에서 호출·`setAlarmClock`·
 * `fullScreenIntent` 를 전부 시도했고 전부 `BAL_BLOCK` 이었다(`docs/KNOWN-ISSUES.md` ⑥).
 *
 * 대신 **Wear SysUI 가 자기 전체화면 팝업으로 그린다**(`DataForFullPopup` →
 * `wnotification.detail2.activity.DetailActivity2`, `BAL_ALLOW_ALLOWLISTED_COMPONENT`).
 * 시스템이 자기 권한으로 띄우므로 상태를 가리지 않는다.
 *
 * ⚠️ **포그라운드 서비스 알림에 [OngoingActivity] 를 붙이면 안 된다.** 붙이면 Wear 가
 * `FILTERED - ONGOING_ACTIVITY_TYPE` 으로 알림 대상에서 빼는데, 울리는 동안 그 알림이
 * **맨 앞 타이머의 만료 알림 그 자체**라 결과적으로 아무것도 안 알려진다. WO-V4 의 진행 중
 * 표시는 도는 동안 [syncOngoing] 이 `ONGOING_ID` 로 맡는다 — 울릴 때는 만료가 먼저다.
 *
 * ⚠️ **그래서 `setFullScreenIntent` 를 붙이면 안 된다.** 붙어 있으면 Wear 가 그것을 띄우는
 * 것으로 알림 표시를 대신하는데, 그 시작이 막혀 **아무것도 안 나온다.** 빼 두어야 시스템
 * 팝업 경로를 탄다 — 실기기에서 붙였을 때 0건, 뺐을 때 정상으로 갈렸다.
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
        // ⚠️ **정렬해서 쓴다.** `repository.timers` 는 복제 레코드 순서, 곧 UUID 순이다.
        // 맨 앞이 「가장 오래 놓친 것」이어야 그것이 스와이프 불가 알림(`FOREGROUND_ID`)과
        // 진동을 가져간다. 화면도 같은 함수를 쓴다(`WearTimerViewModel`).
        val ordered = CareTimerTransitions.projectedAndOrdered(timers, System.currentTimeMillis())
        val ringing = ordered.filter { it.state == TimerState.RINGING }

        // 더 이상 울리지 않는 것은 걷는다. 이게 없으면 폰에서 완료해도 워치에 남는다.
        //
        // ⚠️ **우리가 id 를 정해 둔 둘은 여기서 걷지 않는다.** 진행 중 표시(`ONGOING_ID`)는
        // 아래 [syncOngoing] 이 맞추고, 포그라운드 서비스 알림(`FOREGROUND_ID`)은 서비스가
        // 자기 수명으로 관리한다 — 여기서 지우면 서비스는 살아 있는데 알림만 사라진다.
        val others = ringing.drop(1)
        manager.activeNotifications
            .filter { it.id != 0 && it.id != ONGOING_ID && it.id != FOREGROUND_ID }
            .filter { active -> others.none { timer -> notificationId(timer.id) == active.id } }
            .forEach { manager.cancel(it.id) }

        // **한 타이머에 알림 하나.** 맨 앞의 것은 포그라운드 서비스 알림이 대신하므로 여기서
        // 다시 올리지 않는다 — 둘 다 [base] 로 만들어 제목도 [완료] 액션도 같아서, 올리면
        // 손목에 같은 만료가 둘 뜬다.
        others.forEach { timer -> manager.notify(notificationId(timer.id), build(timer)) }
        // 맨 앞이 바뀌면(먼저 울린 것을 완료하면) 서비스 알림도 새 것으로 갈아 끼운다.
        ringing.firstOrNull()?.let { manager.notify(FOREGROUND_ID, foregroundNotification(it)) }
        syncOngoing(manager, ordered)
    }

    /**
     * 도는 중인 타이머를 **워치 페이스에서** 보이게 한다.
     *
     * 여기가 없으면 손목을 들었을 때 아무것도 안 보인다 — 앱을 열어야만 남은 시간을 알 수
     * 있었다. 폰은 잠금화면 알림이 그 몫을 하는데(`TimerOngoingNotification`) 워치에는
     * 대응물이 없었다.
     *
     * ## 남은 시간은 시스템이 센다
     * [Status.TimerPart] 에 만료 시각을 넘기면 워치 페이스 쪽에서 카운트다운이 흐른다 —
     * 우리가 매초 깨어나 다시 그릴 필요가 없다(폰에서 크로노미터를 쓴 것과 같은 이유).
     * 일시정지 중에는 셀 것이 없으므로 글자로 바꾼다.
     */
    @SuppressLint("MissingPermission")
    private fun syncOngoing(manager: NotificationManagerCompat, ordered: List<CareTimer>) {
        // `ordered` 는 [sync] 가 이미 정렬해 넘긴 것이다 — 맨 앞이 가장 임박한 것이다.
        val running = ordered.filter { it.state != TimerState.RINGING }
        val lead = running.firstOrNull()
        if (lead == null) {
            manager.cancel(ONGOING_ID)
            return
        }
        ensureOngoingChannel()

        val open = PendingIntent.getActivity(
            context,
            ONGOING_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.nm_ic_timer)
            .setContentTitle(lead.label)
            .setContentText(othersLabel(running.size))
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)

        val status = Status.Builder()
            .addTemplate(STATUS_TEMPLATE)
            .addPart("label", Status.TextPart(lead.label))
            .addPart(
                "remaining",
                if (lead.state == TimerState.PAUSED) {
                    Status.TextPart(PAUSED_LABEL)
                } else {
                    Status.TimerPart(lead.endAtEpochMillis)
                }
            )
            .build()

        OngoingActivity.Builder(context, ONGOING_ID, builder)
            .setStaticIcon(R.drawable.nm_ic_timer)
            .setTouchIntent(open)
            .setStatus(status)
            .build()
            .apply(context)

        manager.notify(ONGOING_ID, builder.build())
    }

    /** 「외 2개」 — 하나뿐이면 붙이지 않는다. */
    private fun othersLabel(count: Int): String? = if (count > 1) "외 ${count - 1}개 진행 중" else null

    /** 진행 중 표시는 조용해야 한다 — 손목을 울리는 것은 만료뿐이다. */
    private fun ensureOngoingChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>()
        if (manager != null && manager.getNotificationChannel(ONGOING_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ONGOING_CHANNEL_ID,
                    "진행 중인 타이머",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "도는 동안 남은 시간을 워치 페이스에 보여 줍니다"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
    }

    /**
     * 포그라운드 서비스가 띄우는 알림 — 여기에 진행 중 표시([OngoingActivity])를 얹는다.
     *
     * Wear 품질요건 WO-V4 — 1분 넘게 이어지는 일은 워치 페이스와 최근 목록에 보여야 한다.
     * 만료 알람이 [완료] 까지 지속되므로 여기 해당한다.
     */
    fun foregroundNotification(timer: CareTimer): Notification {
        ensureChannel()
        // 울리는 동안에는 이것이 **맨 앞 타이머의 만료 알림 그 자체**다 — [sync] 가 맨 앞을
        // 따로 올리지 않는다. 그래서 알려질 수 있어야 하고, 알려져야 시스템이 전체화면
        // 팝업을 띄운다(`docs/KNOWN-ISSUES.md` ⑥).
        return base(timer, ongoing = true).build()
    }

    private fun build(timer: CareTimer) = base(timer, ongoing = false).build()

    /**
     * 알림을 **눌렀을 때** 여는 우리 화면.
     *
     * 만료를 덮는 화면은 이것이 아니라 **Wear SysUI 가 그린다** — 자세한 사정은 이 클래스
     * KDoc 의 「만료 화면은 시스템이 그린다」 절에 있다. 여기는 사용자가 알림 본문을 눌러
     * 더 보려 할 때의 목적지다.
     */
    private fun alarmScreenIntent(context: Context, timer: CareTimer): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId(timer.id),
        WearAlarmActivity.intent(context, timer.id, timer.alarmTitle),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * @param ongoing 포그라운드 서비스 알림만 `true` 다.
     *
     * 만료 알림에서는 **끈다.** Wear 가 ongoing 알림을 알릴 대상에서 빼기 때문이다
     * (로그: `FILTERED - ONGOING_ACTIVITY_TYPE`). 채널 진동·`fullScreenIntent` 미사용과
     * 함께 **세 전제 중 하나**이고, 하나만 어긋나도 손목에는 진동만 남는다
     * (`docs/KNOWN-ISSUES.md` ⑥).
     *
     * ⚠️ 끈 대가로 **스와이프로 지울 수 있다.** [sync] 는 `repository.timers` 가 값을 낼
     * 때만 도는데 스와이프는 타이머를 바꾸지 않으므로 **다시 뜨지 않는다.** 다만 맨 앞의
     * 것은 `FOREGROUND_ID`(ongoing) 가 맡아 지워지지 않고, 진동도 [완료] 까지 이어진다.
     */
    private fun base(timer: CareTimer, ongoing: Boolean) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.nm_ic_bell_ring)
        // spec §만료·알람 — title = `❗ [분류] 라벨`. 폰과 같은 문구를 쓴다.
        .setContentTitle(timer.alarmTitle)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(ongoing)
        .setAutoCancel(false)
        // 폰 알림으로 다시 브리징되지 않게 못박는다. 시스템이 이미 끊지만, 기본 동작에
        // 기대면 워치 앱을 지웠다 깔 때 같은 만료가 두 번 울릴 여지가 남는다.
        .setLocalOnly(true)
        .addAction(0, COMPLETE_LABEL, WearTimerActionReceiver.completeIntent(context, timer.id))
        // ⚠️ **`setFullScreenIntent` 를 붙이지 않는다.** 붙어 있으면 Wear 가 그것을 띄우는
        // 것으로 알림 표시를 대신하는데, 그 시작이 BAL 에 막혀 아무것도 안 나온다. 빼면
        // Wear 가 자기 전체화면 팝업(`DataForFullPopup` → SysUI `DetailActivity2`)을 쓰고,
        // 그건 시스템이 자기 권한으로 띄우므로 BAL 과 무관하다. 실기기에서 붙였을 때 이
        // 경로를 탄 횟수 0건, 뺐을 때 정상으로 갈렸다(`docs/KNOWN-ISSUES.md` ⑥).
        .setContentIntent(alarmScreenIntent(context, timer))

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
                    // ⚠️ **켜 둬야 한다.** Wear 는 소리도 진동도 없는 알림을 "조용한 알림"으로
                    // 보고 **표시 자체를 건너뛴다** — 실기기 로그에 그대로 찍힌다:
                    //   [WearSdkAlertingProcessor] Not alerting: Notification is not noisy
                    //   ... shouldVibrate=false, hasSound=false
                    // 그러면 손목을 들어도 만료가 안 보인다. 소리는 spec 이 막으므로(워치는
                    // 항상 햅틱) 진동으로 켠다.
                    //
                    // 대가로 시작할 때 시스템 햅틱이 한 번 겹친다 — [WearAlarmService] 가 모는
                    // 파형과 같은 리듬으로 줘서 티가 덜 나게 한다.
                    enableVibration(true)
                    vibrationPattern = CHANNEL_VIBRATION
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
         * 채널 id 의 `_v3` — 진동을 바꾸려면 번호를 올려야 한다(위 주석 참고).
         *
         * - `_v1` 3회짜리(2초) · `_v2` 폰과 같은 60초 패턴 — **둘 다 한 번만 울렸다.**
         *   Wear 가 채널 패턴을 무시하고 자기 햅틱을 한 번 재생한다. 그래서 지속 진동은
         *   [WearAlarmService] 가 직접 몬다.
         * - 그 뒤 채널 진동을 **껐더니** Wear 가 알림을 "조용한 알림"으로 보고 표시 자체를
         *   건너뛰었다. `_v3` 은 다시 켠 것이다 — 지속이 아니라 **표시 자격**을 얻기 위해서다.
         */
        private const val CHANNEL_ID = "wear_timer_alarm_v3"

        /** 채널 햅틱은 어차피 한 번만 울린다 — 서비스가 모는 파형의 첫 마디와 같은 길이로 준다. */
        private val CHANNEL_VIBRATION = longArrayOf(0L, 800L)

        /** 진행 중 표시가 쓰는 고정 id. 가장 임박한 하나만 띄운다(폰과 같은 규칙). */
        private const val ONGOING_ID = 444_002
        private const val ONGOING_CHANNEL_ID = "wear_timer_ongoing_v1"
        private const val PAUSED_LABEL = "일시정지"

        /** 워치 페이스에 올라가는 한 줄. 자리 이름은 `addPart` 의 키와 맞아야 한다. */
        private const val STATUS_TEMPLATE = "#label# #remaining#"
    }
}
