package app.nursemate.timer.alarm

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
}
