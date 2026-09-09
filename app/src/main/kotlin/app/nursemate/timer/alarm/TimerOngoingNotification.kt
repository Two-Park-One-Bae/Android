package app.nursemate.timer.alarm

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerState

/**
 * 진행 중인 타이머를 앱 밖에서 보여 주는 알림 — spec §앱 밖 진행 중 표시.
 *
 * ## 하나로 묶는다
 * spec 이 "여러 타이머를 하나로 묶은 요약이든 타이머별 개별 표면이든" 플랫폼에 맡겼다.
 * 병동에서는 서너 개가 함께 도는 게 흔한데, 그때마다 알림을 따로 띄우면 알림창을 우리 앱이
 * 점령한다. 그래서 **하나로 묶고**, 접힌 상태에서는 가장 임박한 것을, 펼치면 전체를 보여 준다
 * (iOS 정본의 「가장 임박 + 외 N개」와 같은 모양이다).
 *
 * ## 남은 시간은 시스템이 센다
 * [NotificationCompat.Builder.setUsesChronometer] 로 맡기면 **앱을 매초 깨우지 않아도**
 * 알림 안에서 카운트다운이 흐른다. 우리는 타이머가 시작·정지·만료될 때만 다시 그린다.
 *
 * ## 처치 키워드와 분류까지만 쓴다
 * spec §앱 밖 진행 중 표시 — 메모는 넣지 않는다. 잠금화면은 남이 볼 수 있다.
 */
object TimerOngoingNotification {

    const val ID = 1_441_442

    /** 보여 줄 것이 없으면 null — 호출자가 알림을 내린다. */
    fun build(context: Context, timers: List<CareTimer>, now: Long): Notification? {
        val ordered = CareTimerTransitions.ordered(timers, now)
        val lead = ordered.firstOrNull() ?: return null

        val builder = NotificationCompat.Builder(context, TimerAlarmChannels.ONGOING_ID)
            .setSmallIcon(DsR.drawable.nm_ic_timer)
            .setContentTitle(lead.headline())
            .setContentText(others(ordered.size))
            .setContentIntent(TimerAlarmIntents.openTimerTabPending(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)

        if (lead.state == TimerState.RUNNING) {
            // 시스템이 `when` 까지 남은 시간을 세어 준다.
            builder
                .setWhen(lead.endAtEpochMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
        }

        addActions(context, builder, lead)

        // 펼치면 전부 보인다 — 접힌 줄만으로는 나머지를 식별할 수 없다(spec §앱 밖 진행 중 표시).
        if (ordered.size > 1) {
            val style = NotificationCompat.InboxStyle()
            ordered.take(MAX_LINES).forEach { style.addLine(it.line(now)) }
            if (ordered.size > MAX_LINES) style.setSummaryText(others(ordered.size))
            builder.setStyle(style)
        }

        return builder.build()
    }

    /**
     * 조작 버튼 — spec §앱 밖 진행 중 표시. 앱을 열지 않고 다룰 수 있어야 한다.
     *
     * ## 가장 임박한 하나만 다룬다
     * 알림을 하나로 묶었으니 버튼이 어느 타이머를 가리키는지 정해야 한다. 접힌 줄에 이름이
     * 보이는 그것 — 가장 임박한 타이머 — 을 대상으로 삼는다. 다른 것을 다루려면 알림을 눌러
     * 앱으로 들어간다.
     *
     * 울리는 중이면 [완료] 하나다. 이미 끝난 것을 일시정지·연장할 이유가 없다.
     */
    private fun addActions(context: Context, builder: NotificationCompat.Builder, lead: CareTimer) {
        fun action(label: String, name: String) = builder.addAction(
            0,
            label,
            TimerAlarmReceiver.actionPendingIntent(context, lead.id, name)
        )

        when (lead.state) {
            TimerState.RINGING -> action("완료", TimerAlarmReceiver.ACTION_COMPLETE)

            TimerState.PAUSED -> {
                action("재개", TimerAlarmReceiver.ACTION_RESUME)
                action("정지", TimerAlarmReceiver.ACTION_STOP)
            }

            TimerState.RUNNING -> {
                action("일시정지", TimerAlarmReceiver.ACTION_PAUSE)
                action("정지", TimerAlarmReceiver.ACTION_STOP)
            }
        }
    }

    /** 접힌 줄의 제목 — `AST · 검사` 또는 만료 시 `AST · 검사 — 종료`. */
    private fun CareTimer.headline(): String = buildString {
        append(label)
        append(" · ")
        append(category.label)
        if (state == TimerState.RINGING) append(" — 종료")
    }

    /** 펼친 줄 — 제목에 남은 시간을 덧붙인다. 여기서는 크로노미터를 쓸 수 없다. */
    private fun CareTimer.line(now: Long): String = when (state) {
        TimerState.RINGING -> "$label · ${category.label} — 종료"
        TimerState.PAUSED -> "$label · ${category.label} — 일시정지"
        TimerState.RUNNING -> "$label · ${category.label} — ${clock(remainingAt(now))}"
    }

    private fun others(total: Int): String = if (total > 1) "외 ${total - 1}개 진행 중" else ""

    private fun clock(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val secs = s % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
    }

    /** 펼쳐서 보여 줄 최대 줄 수 — 시스템이 그 이상은 자른다. */
    private const val MAX_LINES = 6
}
