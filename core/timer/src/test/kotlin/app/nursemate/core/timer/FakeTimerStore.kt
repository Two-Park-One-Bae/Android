package app.nursemate.core.timer

import app.nursemate.core.model.AlertMode
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.DEFAULT_TIMER_PRESETS
import app.nursemate.core.model.ORIGIN_PHONE
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerReplica
import app.nursemate.core.model.mergedWith
import app.nursemate.core.model.withTimers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 메모리 저장소 — DataStore 없이 [TimerRepository] 규칙만 본다.
 *
 * @param readDelayMillis 읽기를 일부러 늦춘다. 0 보다 크면 코루틴 둘이 겹칠 창이 생겨,
 *        읽기-수정-쓰기를 따로 하는 코드가 서로의 결과를 덮어쓰는 것을 드러낸다.
 */
class FakeTimerStore(private val readDelayMillis: Long = 0) : TimerStore {

    private val timerState = MutableStateFlow<List<CareTimer>>(emptyList())
    private val presetState = MutableStateFlow(DEFAULT_TIMER_PRESETS)
    private val alertState = MutableStateFlow(AlertMode.SOUND)
    private val chosenState = MutableStateFlow(false)
    private val mutex = Mutex()

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

    /**
     * 복제 장부는 타이머 목록에서 그때그때 만든다.
     *
     * 실제 저장소는 판번호를 이어 가지만, [TimerRepository] 규칙을 보는 데는 필요 없다 —
     * 복제 규칙 자체는 `TimerReplicaTest` 가 따로 못박는다.
     */
    override val replica: Flow<TimerReplica> get() = timerState.map { replicaOf(it) }

    override suspend fun mergeReplica(incoming: TimerReplica): Boolean {
        val merged = replicaOf(timerState.value).mergedWith(incoming, 0L)
        val changed = merged.timers != timerState.value
        timerState.value = merged.timers
        return changed
    }

    private fun replicaOf(timers: List<CareTimer>) = TimerReplica(ORIGIN_PHONE).withTimers(timers, 0L)
    override val presets: Flow<List<TimerPreset>> get() = presetState
    override val alertMode: Flow<AlertMode> get() = alertState

    override val alertModeChosen: Flow<Boolean> get() = chosenState

    /**
     * ⚠️ **읽은 뒤에 늦춘다.** 늦추고 읽으면 앞선 코루틴의 쓰기가 이미 반영돼 겹치지 않는다.
     * 스냅샷을 먼저 잡아야 둘이 같은 옛 목록을 들고 각자 쓰는 상황이 재현된다.
     */
    override suspend fun currentTimers(): List<CareTimer> {
        val snapshot = timerState.value
        if (readDelayMillis > 0) delay(readDelayMillis)
        return snapshot
    }
    override suspend fun currentPresets(): List<TimerPreset> = presetState.value
    override suspend fun currentAlertMode(): AlertMode = alertState.value

    /**
     * 진짜 저장소처럼 **읽기와 쓰기 사이를 열어 두지 않는다.**
     *
     * DataStore 의 `edit` 이 그렇듯 이 블록은 통째로 직렬화된다. 그래서 여기서 통과하면
     * 실제 저장소에서도 겹치지 않는다.
     */
    override suspend fun mutateTimers(transform: (List<CareTimer>) -> List<CareTimer>) {
        mutex.withLock {
            if (readDelayMillis > 0) delay(readDelayMillis)
            timerState.value = transform(timerState.value)
        }
    }

    override suspend fun updateTimers(value: List<CareTimer>) {
        timerState.value = value
    }
    override suspend fun updatePresets(value: List<TimerPreset>) {
        presetState.value = value
    }
    override suspend fun updateAlertMode(value: AlertMode) {
        chosenState.value = true
        alertState.value = value
    }
}
