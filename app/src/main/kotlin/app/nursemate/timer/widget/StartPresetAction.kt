package app.nursemate.timer.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import app.nursemate.core.model.TimerPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 위젯을 눌러 타이머를 시작한다 — spec §위젯 "앱을 열지 않고 타이머를 시작".
 *
 * ## [TimerRepository.start] 를 그대로 통과시킨다
 * 울림 방식·알람 예약·저장이 전부 그 안에 묶여 있다. 위젯이 자기 경로로 타이머를 만들면
 * 전역 설정이 빠지거나 예약이 누락된다 — iOS 가 실제로 그 버그를 냈다(NM-301 티켓).
 *
 * ## 시작한 뒤 잠깐 「시작됨」을 보여 준다
 * 위젯은 눌러도 화면이 안 바뀐다. 표시가 없으면 안 눌린 줄 알고 한 번 더 눌러 타이머가 둘
 * 생긴다 — spec 이 막으라고 한 바로 그 경우다. 정본에 이 상태 프레임은 없다(iOS 도 미구현).
 */
class StartPresetAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = context.timerWidgetEntryPoint()
        val preset = startable(entry, parameters[PRESET_ID_PARAM])

        if (preset == null) {
            // 시작할 수 없다 — 설정에서 권한을 껐거나 프리셋이 지워졌다. 조용히 넘기지 않고
            // 위젯을 다시 그린다. 그러면 다음 탭이 앱(권한 관문)이나 지정 화면으로 간다.
            // 그 한 번은 헛탭이지만 "울릴 줄 알았는데 안 울린" 것보다 낫다.
            Log.w(TAG, "위젯 시작을 중단하고 다시 그린다 (${parameters[PRESET_ID_PARAM]})")
            PresetWidget().update(context, glanceId)
        } else {
            entry.timerRepository().start(preset)
            showStarted(context, glanceId)
        }
    }

    /**
     * 지금 이 프리셋으로 시작해도 되는가.
     *
     * 권한 판정은 위젯을 그릴 때 이미 했다(없으면 이 콜백 대신 앱이 열린다). 여기서 한 번 더
     * 보는 것은 **그린 뒤에 꺼졌을 수 있어서**다 — 위젯은 앱과 달리 오래 그대로 붙어 있다.
     */
    private suspend fun startable(entry: PresetWidgetEntryPoint, presetId: String?): TimerPreset? {
        if (presetId == null || !entry.permissions().allGranted()) return null
        return entry.presetRepository().presets.first().firstOrNull { it.id == presetId }
    }

    /**
     * 「시작됨」을 켰다가 [FEEDBACK_MS] 뒤에 되돌린다.
     *
     * ⚠️ **여기서 기다리는 동안 브로드캐스트 리시버를 붙잡고 있다.** 포그라운드 브로드캐스트의
     * ANR 한계가 10초라 2초는 안전하지만, 이 값을 크게 늘리면 안 된다. 되돌리는 갱신을 놓쳐도
     * 화면이 굳지 않는 것은 저장한 값이 **시각**이라서다(그릴 때 지났는지 다시 본다).
     */
    private suspend fun showStarted(context: Context, glanceId: GlanceId) {
        val widget = PresetWidget()
        updateAppWidgetState(context, glanceId) { it[PresetWidgetSlot.STARTED_AT] = System.currentTimeMillis() }
        widget.update(context, glanceId)

        delay(FEEDBACK_MS)

        updateAppWidgetState(context, glanceId) { it.remove(PresetWidgetSlot.STARTED_AT) }
        widget.update(context, glanceId)
    }

    private companion object {
        const val TAG = "NM443"
    }
}

/** 「시작됨」이 머무는 시간. 리시버를 붙잡고 있는 시간이기도 하다 — 늘리지 말 것. */
internal const val FEEDBACK_MS = 2_000L

internal val PRESET_ID_PARAM = ActionParameters.Key<String>("preset_id")

internal fun startPresetParameters(presetId: String) = actionParametersOf(PRESET_ID_PARAM to presetId)
