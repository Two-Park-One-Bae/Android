package app.nursemate.timer.alarm

import android.app.Notification
import android.content.Context
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerState
import java.util.Date

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
            .setContentText(others(ordered.drop(1)))
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
            ordered.take(MAX_LINES).forEach { style.addLine(it.line(context)) }
            if (ordered.size > MAX_LINES) style.setSummaryText(others(ordered.drop(MAX_LINES)))
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

    /**
     * 펼친 줄 — 진행 중인 것은 **끝나는 시각**으로 쓴다.
     *
     * ⚠️ **남은 시간을 쓰면 낡는다.** 크로노미터는 접힌 줄 하나에만 걸 수 있어서, 펼친
     * 줄은 그린 순간의 문자열로 굳는다. 5분 뒤에 펼치면 5분 전 숫자가 그대로 보인다.
     *
     * 끝나는 시각은 절대값이라 낡지 않고, 병동에서는 교대 시각과 대조하기도 쉽다.
     * spec 은 "남은 시간"으로 식별하라고 쓰지만 그건 낡지 않는 표면을 전제한 것이라,
     * 이 차이는 개정 요청 대상이다.
     */
    private fun CareTimer.line(context: Context): String = when (state) {
        TimerState.RINGING -> "$label · ${category.label} — 종료"
        TimerState.PAUSED -> "$label · ${category.label} — 일시정지"
        TimerState.RUNNING -> "$label · ${category.label} — ${endsAt(context)} 종료"
    }

    private fun CareTimer.endsAt(context: Context): String =
        DateFormat.getTimeFormat(context).format(Date(endAtEpochMillis))

    /**
     * 접힌 줄 아래에 남은 것들을 센다.
     *
     * ⚠️ 만료한 것을 "진행 중"으로 세면 안 된다 — 알람을 놓쳐 쌓인 것을 아직 도는 것처럼
     * 보이게 한다.
     */
    private fun others(rest: List<CareTimer>): String {
        if (rest.isEmpty()) return ""
        val ended = rest.count { it.state == TimerState.RINGING }
        val going = rest.size - ended
        return when {
            ended == 0 -> "외 ${going}개 진행 중"
            going == 0 -> "외 ${ended}개 종료"
            else -> "외 ${going}개 진행 중 · ${ended}개 종료"
        }
    }

    /** 펼쳐서 보여 줄 최대 줄 수 — 시스템이 그 이상은 자른다. */
    private const val MAX_LINES = 6
}
