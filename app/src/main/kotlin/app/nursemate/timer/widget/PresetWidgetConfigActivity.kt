package app.nursemate.timer.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.timer.TimerPresetRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 위젯 슬롯에 프리셋을 지정하는 화면 — spec §위젯 "사용자가 슬롯에 프리셋 하나를 지정해 둔다".
 *
 * ## 두 갈래로 들어온다
 * - **위젯을 놓는 순간** — 시스템이 `android:configure` 를 보고 띄운다. 여기서 취소하면
 *   위젯이 놓이지 않아야 해서 [RESULT_CANCELED] 로 먼저 답해 둔다.
 * - **빈 슬롯을 눌렀을 때** — 위젯 편집을 다시 찾아 들어가지 않아도 되게 같은 화면을 연다.
 *   이쪽은 시스템이 결과를 기다리지 않아 [RESULT_CANCELED] 가 아무 영향을 주지 않는다.
 *
 * 잠금화면에서 눌렀다면 시스템이 먼저 잠금을 풀린다 — 액티비티라 그렇다.
 * 시작(=위젯 탭)은 잠금을 풀지 않지만, **지정은 설정 행위**라 그 편이 맞다.
 */
@AndroidEntryPoint
class PresetWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var presetRepository: TimerPresetRepository

    @Inject lateinit var slotStore: PresetWidgetSlotStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        setResult(RESULT_CANCELED, result(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        setContent {
            NurseMateTheme {
                val presets by presetRepository.presets.collectAsStateWithLifecycle(emptyList())
                PresetWidgetConfigScreen(
                    presets = presets,
                    onPick = { assign(appWidgetId, it) },
                    onClose = ::finish
                )
            }
        }
    }

    private fun assign(appWidgetId: Int, preset: TimerPreset) {
        lifecycleScope.launch {
            slotStore.assign(appWidgetId, preset.id)
            // RemoteViews 는 동기라 여기서 그리면 곧바로 붙는다 — Glance 처럼 WorkManager 를
            // 기다리지 않는다(사정은 [PresetWidgetRenderer]).
            PresetWidgetRenderer.render(
                context = this@PresetWidgetConfigActivity,
                appWidgetId = appWidgetId,
                preset = preset,
                ready = ready()
            )
            setResult(RESULT_OK, result(appWidgetId))
            finish()
        }
    }

    private suspend fun ready(): Boolean = this@PresetWidgetConfigActivity.timerWidgetEntryPoint().canStartWithoutApp()

    private fun result(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {

        fun intent(context: Context, appWidgetId: Int): Intent = Intent(context, PresetWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
