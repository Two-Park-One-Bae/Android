package app.nursemate.timer.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.nursemate.MainActivity

/** 알람 표면에서 앱으로 들어오는 경로. spec §만료·알람 — "알람 확인·탭 후 랜딩 = C1". */
object TimerAlarmIntents {

    const val EXTRA_OPEN_TIMER = "open_timer_tab"

    fun openTimerTab(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_TIMER, true)
    }

    /**
     * 알림에서 앱을 여는 `PendingIntent`.
     *
     * 요청 코드를 고정한다 — 진행 중 알림은 하나뿐이라 매번 새로 만들 필요가 없고,
     * `FLAG_UPDATE_CURRENT` 로 같은 것을 갱신해야 알림을 다시 그려도 대상이 안 바뀐다.
     */
    fun openTimerTabPending(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_TIMER_REQUEST,
        openTimerTab(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private const val OPEN_TIMER_REQUEST = 442_000
}
