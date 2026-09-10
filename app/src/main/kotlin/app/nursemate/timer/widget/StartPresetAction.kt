package app.nursemate.timer.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import app.nursemate.core.model.TimerPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 위젯을 눌러 타이머를 시작한다 — spec §위젯 "앱을 열지 않고 타이머를 시작".
 *
 * ## [TimerRepository.start] 를 그대로 통과시킨다
 * 울림 방식·알람 예약·저장이 전부 그 안에 묶여 있다. 위젯이 자기 경로로 타이머를 만들면
 * 전역 설정이 빠지거나 예약이 누락된다 — iOS 가 실제로 그 버그를 냈다(NM-301 티켓).
 *
 * ## 두 번 눌러 타이머가 둘 생기는 것을 **여기서** 막는다
 * spec §위젯이 막으라고 한 경우다. 위젯은 눌러도 화면이 안 바뀌니, 안 눌린 줄 알고 한 번 더
 * 누른다. 「시작됨」 표시는 그걸 **알릴 뿐 막지는 못한다** — 탭에서 런처가 새 RemoteViews 를
 * 그리기까지 수백 ms 가 걸리고, 다시 누르는 순간이 정확히 그 구간이라 위젯은 아직 예전
 * 액션을 들고 있다. 그리는 쪽 판정은 늘 늦는다.
 */
class StartPresetAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = context.timerWidgetEntryPoint()
        val preset = startable(entry, parameters[PRESET_ID_PARAM])

        when {
            preset == null -> {
                // 시작할 수 없다 — 설정에서 권한을 껐거나, 울림 방식을 아직 안 골랐거나,
                // 프리셋이 지워졌다. 조용히 넘기지 않고 위젯을 다시 그린다. 그러면 다음 탭이
                // 앱(관문)이나 지정 화면으로 간다. 그 한 번은 헛탭이지만 "울릴 줄 알았는데
                // 안 울린" 것보다 낫다.
                Log.w(TAG, "위젯 시작을 중단하고 다시 그린다 (${parameters[PRESET_ID_PARAM]})")
                PresetWidget().update(context, glanceId)
            }

            !claimStart(context, glanceId) ->
                Log.i(TAG, "방금 시작해 이 탭은 넘긴다 (${parameters[PRESET_ID_PARAM]})")

            else -> startAndShow(context, glanceId, entry, preset)
        }
    }

    /**
     * "지금부터 [FEEDBACK_MS] 동안은 내가 시작한다"를 **한 번의 원자적 갱신으로** 선점한다.
     *
     * ⚠️ **읽고 → 판단하고 → 쓰기를 따로 하면 안 된다.** 탭 하나가 브로드캐스트 하나고 각자
     * 자기 코루틴에서 돈다. 빠르게 두 번 누르면 둘 다 "아직 안 시작했다"를 읽고 둘 다 통과해
     * **타이머가 둘 생긴다** — 표시가 뜨기 전 구간이라 화면으로는 구분도 안 된다.
     * `updateAppWidgetState` 의 블록은 DataStore 의 `edit` 안에서 돌아 직렬화된다.
     *
     * 찍는 값이 시각이라 [FEEDBACK_MS] 가 지나면 저절로 풀린다 — 되돌리는 갱신을 놓쳐도
     * (프로세스 사망 등) 위젯이 영영 막히지 않는다.
     *
     * @return 선점했으면 true. 이미 방금 시작했으면 false
     */
    private suspend fun claimStart(context: Context, glanceId: GlanceId): Boolean {
        var claimed = false
        updateAppWidgetState(context, glanceId) { prefs ->
            val now = System.currentTimeMillis()
            if (now - (prefs[PresetWidgetSlot.STARTED_AT] ?: 0L) >= FEEDBACK_MS) {
                prefs[PresetWidgetSlot.STARTED_AT] = now
                claimed = true
            }
        }
        return claimed
    }

    /**
     * 지금 이 프리셋으로 시작해도 되는가.
     *
     * 권한·울림 방식 판정은 위젯을 그릴 때 이미 했다(못 지났으면 이 콜백 대신 앱이 열린다).
     * 여기서 한 번 더 보는 것은 **그린 뒤에 바뀌었을 수 있어서**다 — 위젯은 앱과 달리 오래
     * 그대로 붙어 있다.
     */
    private suspend fun startable(entry: PresetWidgetEntryPoint, presetId: String?): TimerPreset? {
        if (presetId == null || !entry.canStartWithoutApp()) return null
        return entry.presetRepository().presets.first().firstOrNull { it.id == presetId }
    }

    /**
     * 시작하고 「시작됨」을 켠다. 시각은 [claimStart] 가 이미 찍었다.
     *
     * ⚠️ **되돌리기를 여기서 기다리면 안 된다.** 브로드캐스트는 같은 리시버에 **직렬로**
     * 전달돼서, 콜백이 2초를 붙잡고 있으면 **두 번째 탭이 그 2초 뒤에야 평가된다** —
     * 중복을 막으려고 둔 창이 정작 판정 시점에는 닫혀 있고, 그 사이 [claimStart] 가 찍은
     * 값도 지워져 있다. 실기기 로그로 확인했다(두 번째 수신이 정확히 +2102ms, `prev=0`).
     * 그래서 콜백은 곧바로 끝내고 되돌리기만 밖에서 재운다.
     */
    private suspend fun startAndShow(
        context: Context,
        glanceId: GlanceId,
        entry: PresetWidgetEntryPoint,
        preset: TimerPreset
    ) {
        entry.timerRepository().start(preset)
        PresetWidget().update(context, glanceId)
        scheduleRevert(context.applicationContext, glanceId)
    }

    /**
     * [FEEDBACK_MS] 뒤에 「시작됨」을 되돌린다.
     *
     * 프로세스가 그사이 죽으면 표시가 남지만, 판정이 시각 비교라 **탭이 막히지는 않는다**
     * (다음 갱신에서 그림도 돌아온다). 리시버를 붙잡는 것보다 이쪽이 낫다.
     */
    private fun scheduleRevert(appContext: Context, glanceId: GlanceId) {
        revertScope.launch {
            delay(FEEDBACK_MS)
            updateAppWidgetState(appContext, glanceId) { it.remove(PresetWidgetSlot.STARTED_AT) }
            PresetWidget().update(appContext, glanceId)
        }
    }

    private companion object {
        const val TAG = "NM443"
    }
}

/** 「시작됨」이 머무는 시간이자, 그동안 두 번째 탭을 막는 창. */
internal const val FEEDBACK_MS = 2_000L

/** 「시작됨」 되돌리기 전용. 리시버 수명 밖에서 재운다 — 이유는 [StartPresetAction] 참고. */
private val revertScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal val PRESET_ID_PARAM = ActionParameters.Key<String>("preset_id")

internal fun startPresetParameters(presetId: String) = actionParametersOf(PRESET_ID_PARAM to presetId)
