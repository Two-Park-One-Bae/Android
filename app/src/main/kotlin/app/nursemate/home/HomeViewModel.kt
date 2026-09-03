package app.nursemate.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.pill.UsageHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 홈이 보여줄 남은 식별 횟수.
 *
 * 값 자체는 [UsageHolder] 가 앱 전체와 공유한다 — 여기서는 화면이 뜰 때 갱신만 시킨다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(private val usageHolder: UsageHolder) : ViewModel() {

    val usage = usageHolder.usage

    /** 한도에 걸렸다고 확인된 경우에만 true. 조회에 실패해 모르는 상태면 막지 않는다. */
    fun blocked(): Boolean = usageHolder.blocked

    fun refresh() {
        viewModelScope.launch { usageHolder.refresh() }
    }
}
