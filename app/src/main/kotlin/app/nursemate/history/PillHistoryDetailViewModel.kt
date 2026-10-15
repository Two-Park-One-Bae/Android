package app.nursemate.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.pill.PillHistoryNotFound
import app.nursemate.core.data.pill.PillHistoryRepository
import app.nursemate.core.model.PillHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 기록 상세 조회 상태. 식별 시각은 요약으로 받은 값이라 어느 상태에서든 바로 보인다. */
sealed interface HistoryDetailState {
    /** 조회 중 — 알약 수만큼 자리 표시 행. */
    data object Loading : HistoryDetailState

    data object Failed : HistoryDetailState

    data class Loaded(val history: PillHistory) : HistoryDetailState
}

/** 상세가 화면 밖으로 알릴 일. */
sealed interface HistoryDetailEvent {
    /** 없어진 기록 — 들어온 화면으로 돌아가 `기록을 찾을 수 없어요` 를 잠깐 띄운다. */
    data object NotFound : HistoryDetailEvent

    /** 지웠다 — 들어온 화면으로 돌아간다. */
    data object Deleted : HistoryDetailEvent

    /** 지우지 못했다 — 화면에 남는다. */
    data object DeleteFailed : HistoryDetailEvent
}

/**
 * 기록 상세 — 열 때 한 건을 조회한다(목록 · 홈 카드는 요약만 받는다). spec §식별 기록 — 기록 상세.
 */
@HiltViewModel
class PillHistoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PillHistoryRepository
) : ViewModel() {

    val historyId: String = checkNotNull(savedStateHandle[ARG_HISTORY_ID])

    private val _state = MutableStateFlow<HistoryDetailState>(HistoryDetailState.Loading)
    val state: StateFlow<HistoryDetailState> = _state.asStateFlow()

    private val _events = Channel<HistoryDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 삭제 요청 중 — 확인 모달의 버튼을 다시 누르지 못하게 한다. */
    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = HistoryDetailState.Loading
        viewModelScope.launch {
            repository.get(historyId).fold(
                onSuccess = { _state.value = HistoryDetailState.Loaded(it) },
                onFailure = { e ->
                    if (e is PillHistoryNotFound) {
                        // 목록 · 홈 카드에서도 빠졌을 것이다 — 다시 불러오게 알린다(spec §없어진 기록).
                        repository.invalidate()
                        _events.send(HistoryDetailEvent.NotFound)
                    } else {
                        _state.value = HistoryDetailState.Failed
                    }
                }
            )
        }
    }

    fun delete() {
        if (_deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            val result = repository.delete(historyId)
            _deleting.value = false
            _events.send(if (result.isSuccess) HistoryDetailEvent.Deleted else HistoryDetailEvent.DeleteFailed)
        }
    }

    companion object {
        const val ARG_HISTORY_ID = "historyId"
    }
}
