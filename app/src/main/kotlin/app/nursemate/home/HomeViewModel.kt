package app.nursemate.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.pill.UsageHolder
import app.nursemate.core.timer.TimerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 홈이 보여줄 남은 식별 횟수와 활성 타이머 개수.
 *
 * 식별 횟수는 [UsageHolder] 가 앱 전체와 공유한다 — 여기서는 화면이 뜰 때 갱신만 시킨다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(private val usageHolder: UsageHolder, timerRepository: TimerRepository) :
    ViewModel() {

    val usage = usageHolder.usage

    /**
     * 상태칩 「활성 타이머 N」 — spec §진입점.
     *
     * **개수는 목록 크기 그대로다.** 상태가 실행 중·일시정지·울림 셋뿐이고 완료·취소는
     * 상태가 아니라 **즉시 삭제**라(domain-model §상태머신), 저장소에 남아 있다는 것이 곧
     * 활성이다. 상태별로 거를 것이 없다.
     */
    val activeTimerCount = timerRepository.timers
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MILLIS), 0)

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
