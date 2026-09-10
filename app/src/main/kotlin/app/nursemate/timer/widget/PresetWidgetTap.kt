package app.nursemate.timer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import app.nursemate.core.model.TimerPreset

/**
 * 위젯을 눌렀을 때 무엇을 할지.
 *
 * - 슬롯이 비었으면 → 지정 화면. spec 은 "설정 방법을 안내"라고 했지만 안내로 끝내면
 *   위젯 편집을 다시 찾아 들어가야 한다 — 잠금화면에 놓은 위젯은 그 길이 멀다.
 * - 관문이 남았으면(권한·울림 방식) → 앱(타이머 탭). 관문이 뜨고, 다 지나면 그 프리셋이
 *   그대로 시작된다 — 위젯으로 되돌아갈 필요가 없다.
 * - 둘 다 지났으면 → 앱을 열지 않고 **브로드캐스트로 바로 시작**한다.
 */
internal object PresetWidgetTap {

    fun pendingIntent(context: Context, appWidgetId: Int, preset: TimerPreset?, ready: Boolean): PendingIntent = when {
        preset == null -> PendingIntent.getActivity(
            context,
            appWidgetId,
            PresetWidgetConfigActivity.intent(context, appWidgetId),
            FLAGS
        )

        !ready -> PendingIntent.getActivity(
            context,
            appWidgetId + IN_APP_OFFSET,
            PresetWidgetLaunch.startInApp(context, preset.id),
            FLAGS
        )

        else -> PendingIntent.getBroadcast(
            context,
            appWidgetId + START_OFFSET,
            Intent(context, PresetWidgetReceiver::class.java).apply {
                action = ACTION_START
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_PRESET_ID, preset.id)
            },
            FLAGS
        )
    }

    const val ACTION_START = "app.nursemate.timer.widget.START"
    const val EXTRA_PRESET_ID = "preset_id"

    /**
     * 요청 코드는 위젯마다 달라야 한다 — 같으면 시스템이 하나로 합쳐 **다른 위젯을 눌러도
     * 먼저 만든 인텐트가 날아간다.** 셋이 겹치지 않게 구간을 나눈다.
     */
    private const val IN_APP_OFFSET = 100_000
    private const val START_OFFSET = 200_000

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
