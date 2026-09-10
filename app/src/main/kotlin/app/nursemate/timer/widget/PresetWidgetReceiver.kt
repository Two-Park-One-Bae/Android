package app.nursemate.timer.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 시스템이 위젯을 잡는 손잡이. 매니페스트에 등록되는 것은 [PresetWidget] 이 아니라 이쪽이다.
 *
 * Hilt 를 달지 않는다 — 여기에 `@AndroidEntryPoint` 를 붙여도 시스템이 따로 만드는
 * [PresetWidget]·`ActionCallback` 에는 닿지 않아, 주입 경로가 둘로 갈릴 뿐이다.
 * 위젯 쪽은 `PresetWidgetEntryPoint` 하나로 통일한다.
 */
class PresetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PresetWidget()
}
