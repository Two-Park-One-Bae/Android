package app.nursemate.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.timer.TimerPresetRepository
import app.nursemate.core.data.timer.TimerRepository
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * C1 타이머 리스트 화면의 상태.
 *
 * ## 1초마다 화면만 다시 그린다
 * 남은 시간은 저장하지 않고 `endAt − 지금`으로 계산한다(도메인 규칙). 그래서 **틱은 저장소를
 * 건드리지 않고** 화면이 다시 그려질 구실만 만든다 — 1초마다 디스크에 쓰면 안 된다.
 *
 * 만료 판정도 여기서 하지 않는다. 알람 리시버가 [TimerRepository.markRinging] 으로 올리고,
 * 앱이 꺼져 있던 경우는 `restore()` 가 맞춘다. 화면이 자체 판단하면 알람과 어긋난다.
 */
@HiltViewModel
class TimerListViewModel @Inject constructor(
    private val repository: TimerRepository,
    presetRepository: TimerPresetRepository
) : ViewModel() {

    val timers: StateFlow<List<CareTimer>> = repository.timers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val presets: StateFlow<List<TimerPreset>> = presetRepository.presets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** 남은 시간 계산의 기준 시각. 1초마다 갱신돼 화면을 다시 그린다. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** 메모를 인라인으로 펼친 타이머. 카드가 확장된다. */
    private val _memoEditing = MutableStateFlow<String?>(null)
    val memoEditing: StateFlow<String?> = _memoEditing.asStateFlow()

    /** C3 프리셋 시트가 떠 있는가. FAB·빈 상태 CTA 가 같은 시트를 연다. */
    private val _presetSheetOpen = MutableStateFlow(false)
    val presetSheetOpen: StateFlow<Boolean> = _presetSheetOpen.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _now.value = System.currentTimeMillis()
                delay(TICK_MS)
            }
        }
    }

    fun openPresetSheet() {
        _presetSheetOpen.value = true
    }

    fun closePresetSheet() {
        _presetSheetOpen.value = false
    }

    /** 프리셋을 누르면 곧바로 시작하고 시트를 닫는다(정본 C3 부제). */
    fun start(preset: TimerPreset) = launchIo {
        repository.start(preset)
        _presetSheetOpen.value = false
    }

    fun pauseOrResume(timer: CareTimer) = launchIo {
        if (timer.state == TimerState.PAUSED) {
            repository.resume(timer.id)
        } else {
            repository.pause(timer.id)
        }
    }

    fun extend(timerId: String) = launchIo { repository.extend(timerId) }

    /** 완료·정지 모두 삭제다(spec §생성 → 실행). */
    fun remove(timerId: String) = launchIo { repository.remove(timerId) }

    fun toggleMemo(timerId: String) {
        _memoEditing.value = if (_memoEditing.value == timerId) null else timerId
    }

    fun saveMemo(timerId: String, memo: String) = launchIo {
        repository.setMemo(timerId, memo)
        _memoEditing.value = null
    }

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val TICK_MS = 1000L
        const val STOP_TIMEOUT_MS = 5000L
    }
}
