package app.nursemate.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerState
import app.nursemate.core.timer.TimerPresetRepository
import app.nursemate.core.timer.TimerRepository
import app.nursemate.telemetry.AnalyticsEvent
import app.nursemate.telemetry.AppAnalytics
import app.nursemate.timer.alarm.TimerPermissions
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
    permissions: TimerPermissions,
    presetRepository: TimerPresetRepository,
    private val analytics: AppAnalytics
) : ViewModel() {

    /** 프리셋 추가·수정·삭제. 시트 안에서만 사는 상태라 따로 둔다. */
    val presetEditor = TimerPresetEditor(presetRepository, viewModelScope, analytics)

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

    /** 시작 관문 — 권한·울림 방식 최초 선택. 화면이 시트를 띄우고 OS 절차를 밟는다. */
    val startGate = TimerStartGate(repository, permissions, viewModelScope)

    init {
        viewModelScope.launch {
            while (isActive) {
                _now.value = System.currentTimeMillis()
                delay(TICK_MS)
            }
        }
    }

    fun setPresetSheet(open: Boolean) {
        _presetSheetOpen.value = open
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

    /**
     * 알림 권한 팝업의 응답을 남긴다 — 어느 관문에서 얼마나 거부되는지 본다.
     *
     * `permission` 을 `alarm` 으로 보내는 건 **iOS 와 같은 축에 쌓기 위해서**다. iOS 는
     * 시스템 알람 권한 하나를 `alarm` 으로 찍는데, Android 에서 그 자리에 해당하는 것이
     * 알림 권한이다. 정확 알람·전체화면은 팝업이 아니라 설정 왕복이라 결과를 알 수 없어
     * 애초에 집계되지 않는다.
     */
    fun trackAlarmPermission(granted: Boolean) {
        analytics.track(
            AnalyticsEvent.PermissionResult(
                permission = "alarm",
                result = if (granted) "granted" else "denied",
                gate = "timer"
            )
        )
    }

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
