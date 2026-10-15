package app.nursemate.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.pill.UsageHolder
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerState
import app.nursemate.core.timer.TimerRepository
import app.nursemate.core.timer.clockTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 홈이 보여줄 남은 식별 횟수 · 타이머 목록 · 지금 시각.
 *
 * 식별 횟수는 [UsageHolder] 가 앱 전체와 공유한다 — 여기서는 화면이 뜰 때 갱신만 시킨다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(private val usageHolder: UsageHolder, timerRepository: TimerRepository) :
    ViewModel() {

    val usage = usageHolder.usage

    /**
     * 타이머 탭 목록 그대로 — 진행 중 · 일시정지 · 만료를 모두 센다(spec home §처치 타이머 카드).
     *
     * 완료 · 취소는 상태가 아니라 **즉시 삭제**라(domain-model §상태머신) 저장소에 남아 있으면 센다.
     */
    val timers: StateFlow<List<CareTimer>> = timerRepository.timers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MILLIS), emptyList())

    /**
     * 남은 시간을 초 단위로 줄이려고 흐르는 시각. **타이머가 있을 때만** 1초마다 흐른다 — 없으면
     * 한 번만 낸다. StateFlow 로 붙잡지 않는다 — 화면에 돌아왔을 때 붙잡아 둔 옛 시각으로 첫 장을
     * 그리지 않게, 모으는 쪽이 지금 시각을 처음값으로 쓴다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val now: Flow<Long> = timerRepository.timers
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { ticking -> if (ticking) clockTicks() else flowOf(System.currentTimeMillis()) }

    /** 한도에 걸렸다고 확인된 경우에만 true. 조회에 실패해 모르는 상태면 막지 않는다. */
    fun blocked(): Boolean = usageHolder.blocked

    fun refresh() {
        viewModelScope.launch { usageHolder.refresh() }
    }

    private companion object {
        /** 화면 회전처럼 짧게 끊기는 구독은 다시 잇는다. */
        const val SUBSCRIBE_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * 진행 중인 타이머 카드의 순서 — **만료 → 진행 중(남은 시간이 짧은 순) → 일시정지**(spec home).
 *
 * 만료는 울리는 중(`RINGING`)이거나, 아직 울림으로 안 올라갔지만 끝 시각을 지난 진행 중 타이머다.
 * 같은 무리 안에서는 남은 시간이 짧은 쪽이 먼저다(만료는 모두 0 이라 들어온 순서를 지킨다).
 */
fun homeTimerOrder(timers: List<CareTimer>, now: Long): List<CareTimer> = timers.sortedWith(
    compareBy<CareTimer>(
        { timer ->
            when {
                timer.state == TimerState.RINGING || timer.isExpiredAt(now) -> 0
                timer.state == TimerState.RUNNING -> 1
                else -> 2
            }
        },
        { it.remainingAt(now) }
    )
)
