package app.nursemate.timer.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.flow.first

/** 놓여 있는 위젯을 모두 다시 그린다. */
internal object PresetWidgetRefresher {

    suspend fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PresetWidgetReceiver::class.java))
        if (ids.isEmpty()) return

        val entry = context.timerWidgetEntryPoint()
        val ready = entry.canStartWithoutApp()
        val presets = entry.presetRepository().presets.first()
        val slots = entry.slotStore().slots.first()
        ids.forEach { id ->
            PresetWidgetRenderer.render(context, id, presets.firstOrNull { it.id == slots[id] }, ready)
        }
    }
}
