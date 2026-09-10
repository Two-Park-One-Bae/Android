package app.nursemate.timer.widget

import android.content.Context
import android.content.Intent
import app.nursemate.timer.alarm.TimerAlarmIntents

/**
 * 권한이 없을 때 위젯이 앱을 여는 경로 — spec §위젯 "앱이 열리고 알람 권한 안내로 진입".
 *
 * 타이머 탭으로 보내는 부분은 알람과 완전히 같아서 [TimerAlarmIntents] 것을 그대로 쓰고,
 * "이 프리셋을 시작하려던 참이었다"만 얹는다. 앱은 그 프리셋으로 시작 관문을 열고,
 * 권한을 받고 나면 **누른 그 타이머가 그대로 시작된다** — 위젯으로 돌아가 다시 누르지 않는다.
 */
internal object PresetWidgetLaunch {

    const val EXTRA_PRESET_ID = "widget_start_preset"

    fun startInApp(context: Context, presetId: String): Intent =
        TimerAlarmIntents.openTimerTab(context).putExtra(EXTRA_PRESET_ID, presetId)
}
