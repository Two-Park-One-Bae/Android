package app.nursemate.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerCommand
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerSnapshot
import app.nursemate.wear.sync.WearTimerStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 워치 화면의 상태.
 *
 * ## 폰이 보낸 것만 보여 준다
 * 시작·정지를 눌러도 여기서 목록을 고치지 않는다. 명령을 보내고 **다음 스냅샷을 기다린다**
 * (spec §명령). 미리 바꿔 두면 폰이 거절했을 때(권한 없음·프리셋 삭제) 두 화면이 갈라진다.
 *
 * 왕복 동안 화면이 멈춰 보이는 구간이 생기는데, spec 이 "그 구간을 어떻게 보여줄지는
 * 플랫폼이 정한다"고 열어 뒀다 — [pending] 으로 눌린 항목을 표시한다.
 */
@HiltViewModel
class WearTimerViewModel @Inject constructor(private val store: WearTimerStore) : ViewModel() {

    val snapshot: StateFlow<TimerSnapshot?> = store.snapshot

    /** 남은 시간 계산의 기준. 폰과 같은 이유로 1초마다 화면만 다시 그린다. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** 명령을 보내고 스냅샷을 기다리는 중인 항목. 같은 것을 두 번 누르지 않게 막는다. */
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** 마지막 명령이 폰에 닿지 못했다. 화면이 안내한다. */
    private val _undelivered = MutableStateFlow(false)
    val undelivered: StateFlow<Boolean> = _undelivered.asStateFlow()

    init {
        viewModelScope.launch { store.restore() }
        viewModelScope.launch {
            while (isActive) {
                _now.value = System.currentTimeMillis()
                delay(TICK_MS)
            }
        }
        // 스냅샷이 새로 오면 기다리던 명령이 처리된 것이다.
        viewModelScope.launch {
            snapshot.collect { _pending.value = null }
        }
    }

    /** 화면에 보여 줄 순서 — 폰과 같은 규칙(만료 먼저). */
    fun ordered(snapshot: TimerSnapshot, now: Long): List<CareTimer> = CareTimerTransitions.ordered(
        snapshot.timers.map { if (it.isExpiredAt(now)) CareTimerTransitions.ring(it) else it },
        now
    )

    fun start(preset: TimerPreset) = send(preset.id, TimerCommand.Start(preset.id))

    fun complete(timer: CareTimer) = send(timer.id, TimerCommand.Remove(timer.id))

    private fun send(key: String, command: TimerCommand) {
        _pending.value = key
        _undelivered.value = false
        viewModelScope.launch {
            if (!store.send(command)) {
                // 폰이 꺼져 있거나 연결이 끊겼다. 기다리게 두면 영영 안 풀린다.
                _pending.value = null
                _undelivered.value = true
            }
        }
    }

    private companion object {
        const val TICK_MS = 1000L
    }
}
