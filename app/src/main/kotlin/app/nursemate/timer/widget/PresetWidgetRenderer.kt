package app.nursemate.timer.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import app.nursemate.R
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.formatDuration

/**
 * 위젯 한 장을 그려 시스템에 넘긴다.
 *
 * ## 왜 Glance 가 아닌가
 * Glance 는 컴포지션을 `SessionWorker`(WorkManager)로 돌린다. 그 작업이
 * `Requires: charging=true` 로 잡혀 **충전 중이 아니면 시스템이 실행을 미룬다** — 실측으로
 * 지정한 프리셋이 화면에 붙기까지 충전 중 673ms, 비충전 **61초** 였다.
 * `update`·`updateAll` 모두 같은 세션 경로라 Glance API 안에서는 피할 길이 없다
 * (`GlanceAppWidget.kt:266` — `updateAll` 은 각 id 마다 `update` 를 부르는 게 전부다).
 *
 * [AppWidgetManager.updateAppWidget] 은 **동기**라 그 큐를 지나지 않는다.
 */
internal object PresetWidgetRenderer {

    /**
     * 위젯 하나를 지금 그린다.
     *
     * [justStarted] 면 시간 자리에 「시작됨」을 띄운다 — spec §위젯이 "탭하면 시작됐다는 것이
     * 눈에 보여야 한다"고 요구한다. Glance 시절에는 그리기까지 492ms 가 걸려 실효가 없어
     * 걷어냈는데, RemoteViews 는 동기라 곧바로 뜬다.
     */
    fun render(context: Context, appWidgetId: Int, preset: TimerPreset?, ready: Boolean, justStarted: Boolean = false) {
        val slot = SlotLook.of(preset, justStarted)
        val views = RemoteViews(context.packageName, R.layout.nm_widget_preset)

        views.setTextViewText(R.id.widget_label, preset?.label ?: EMPTY_LABEL)
        views.setTextColor(R.id.widget_label, color(context, slot.labelColor))

        val value = slot.value(preset)
        views.setTextViewText(R.id.widget_value, value)
        views.setTextColor(R.id.widget_value, color(context, slot.valueColor))
        // 값이 길면 글자를 줄인다 — 프리셋은 초 단위까지 자유로워 `2시간 30분 30초` 가 나온다.
        // `maxLines=1` 은 그냥 잘라 버려서 **무엇을 맞춰 놨는지 알 수 없게 된다.**
        views.setTextViewTextSize(R.id.widget_value, COMPLEX_UNIT_SP, valueSize(value))

        views.setImageViewResource(R.id.widget_icon, slot.icon)
        views.setInt(R.id.widget_icon, "setColorFilter", color(context, slot.accent))
        views.setInt(R.id.widget_root, "setBackgroundResource", slot.background)

        views.setOnClickPendingIntent(
            R.id.widget_root,
            PresetWidgetTap.pendingIntent(context, appWidgetId, preset, ready)
        )

        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    /**
     * 위젯이 보여 주는 세 가지 모습. 분기를 한 곳에 모아 두면 색·아이콘이 서로 어긋나지 않는다.
     */
    private enum class SlotLook(
        val labelColor: Int,
        val valueColor: Int,
        val accent: Int,
        val icon: Int,
        val background: Int
    ) {
        EMPTY(
            labelColor = R.color.nm_widget_label_empty,
            valueColor = R.color.nm_widget_value_empty,
            accent = R.color.nm_widget_label_empty,
            icon = DsR.drawable.nm_ic_plus,
            background = R.drawable.nm_widget_card_empty
        ),
        STARTED(
            labelColor = R.color.nm_widget_label,
            valueColor = R.color.nm_widget_started,
            accent = R.color.nm_widget_started,
            icon = DsR.drawable.nm_ic_check,
            background = R.drawable.nm_widget_card
        ),
        READY(
            labelColor = R.color.nm_widget_label,
            valueColor = R.color.nm_widget_value,
            accent = R.color.nm_widget_accent,
            icon = DsR.drawable.nm_ic_timer,
            background = R.drawable.nm_widget_card
        );

        fun value(preset: TimerPreset?): String = when (this) {
            EMPTY -> EMPTY_VALUE
            STARTED -> STARTED_VALUE
            READY -> formatDuration(checkNotNull(preset).durationSeconds)
        }

        companion object {
            fun of(preset: TimerPreset?, justStarted: Boolean): SlotLook = when {
                preset == null -> EMPTY
                justStarted -> STARTED
                else -> READY
            }
        }
    }

    private fun color(context: Context, id: Int) = ContextCompat.getColor(context, id)

    /** `2시간 30분` = 7자. 그보다 길면 한 단계 더 줄인다. */
    private fun valueSize(text: String): Float = when {
        text.length > LONG_DURATION -> 13f
        text.length > MEDIUM_DURATION -> 15f
        else -> 17f
    }

    private const val COMPLEX_UNIT_SP = 2
    private const val MEDIUM_DURATION = 4
    private const val LONG_DURATION = 7

    private const val STARTED_VALUE = "시작됨"
    private const val EMPTY_LABEL = "프리셋 미지정"
    private const val EMPTY_VALUE = "지정하기"
}
