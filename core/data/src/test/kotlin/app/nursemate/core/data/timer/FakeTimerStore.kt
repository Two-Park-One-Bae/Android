package app.nursemate.core.data.timer

import app.nursemate.core.model.AlertMode
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.DEFAULT_TIMER_PRESETS
import app.nursemate.core.model.TimerPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 메모리 저장소 — DataStore 없이 [TimerRepository] 규칙만 본다. */
class FakeTimerStore : TimerStore {

    private val timerState = MutableStateFlow<List<CareTimer>>(emptyList())
    private val presetState = MutableStateFlow(DEFAULT_TIMER_PRESETS)
    private val alertState = MutableStateFlow(AlertMode.SOUND)

    /** 테스트가 직접 읽고 쓰는 창구. Flow 프로퍼티와 이름이 겹치지 않게 따로 둔다. */
    var savedTimers: List<CareTimer>
        get() = timerState.value
        set(value) {
            timerState.value = value
        }

    var savedPresets: List<TimerPreset>
        get() = presetState.value
        set(value) {
            presetState.value = value
        }

    override val timers: Flow<List<CareTimer>> get() = timerState
    override val presets: Flow<List<TimerPreset>> get() = presetState
    override val alertMode: Flow<AlertMode> get() = alertState

    override suspend fun currentTimers(): List<CareTimer> = timerState.value
    override suspend fun currentPresets(): List<TimerPreset> = presetState.value
    override suspend fun currentAlertMode(): AlertMode = alertState.value

    override suspend fun updateTimers(value: List<CareTimer>) {
        timerState.value = value
    }
    override suspend fun updatePresets(value: List<TimerPreset>) {
        presetState.value = value
    }
    override suspend fun updateAlertMode(value: AlertMode) {
        alertState.value = value
    }
}
