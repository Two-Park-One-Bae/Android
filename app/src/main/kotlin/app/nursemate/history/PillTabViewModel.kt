package app.nursemate.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.pill.PillHistoryRepository
import app.nursemate.core.data.pill.UsageHolder
import app.nursemate.core.model.PillHistorySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 알약 탭 기록 목록의 상태. */
sealed interface HistoryListState {
    data object Loading : HistoryListState

    /** 첫 묶음을 불러오지 못했다 — `기록을 불러오지 못했어요` · `다시 시도`. */
    data object Failed : HistoryListState

    /**
     * 받은 기록. [nextCursor] 가 null 이면 끝이다(`최근 100건까지 보관해요`).
     * [moreFailed] 는 이어서 불러오기 실패 — 받은 목록은 그대로 두고 끝에 `다시 시도` 를 둔다.
     */
    data class Loaded(
        val items: List<PillHistorySummary>,
        val nextCursor: String?,
        val loadingMore: Boolean = false,
        val moreFailed: Boolean = false
    ) : HistoryListState
}

/**
 * 알약 탭 — 촬영 버튼 + 식별 기록 목록(spec §식별 기록 · NM-555).
 *
 * 기록이 저장 · 삭제되면([PillHistoryRepository.version]) 처음부터 다시 불러온다 — 방금 저장한 기록이
 * 맨 위에 보여야 한다.
 */
@HiltViewModel
class PillTabViewModel @Inject constructor(
    private val repository: PillHistoryRepository,
    private val usageHolder: UsageHolder
) : ViewModel() {

    val usage = usageHolder.usage

    private val _state = MutableStateFlow<HistoryListState>(HistoryListState.Loading)
    val state: StateFlow<HistoryListState> = _state.asStateFlow()

    /** 처음부터 받기 · 이어서 받기를 따로 쥔다 — 하나로 쥐면 서로를 덮어써 취소가 엇나간다. */
    private var reloadJob: Job? = null
    private var moreJob: Job? = null

    init {
        reload()
        viewModelScope.launch { repository.version.drop(1).collect { reload() } }
    }

    /** 화면에 돌아올 때마다 남은 횟수를 새로 받는다 — 홈 카드와 같은 값이어야 한다. */
    fun refreshUsage() {
        viewModelScope.launch { usageHolder.refresh() }
    }

    /** 처음부터 다시. 실패 화면의 `다시 시도` 도 이것이다. */
    fun reload() {
        reloadJob?.cancel()
        // 이어서 받던 것은 버린다 — 처음부터 다시 받으니 경계가 바뀐다. 「받는 중」 표시도 같이 내린다.
        moreJob?.cancel()
        _state.update { if (it is HistoryListState.Loaded) it.copy(loadingMore = false) else HistoryListState.Loading }
        reloadJob = viewModelScope.launch {
            repository.page().fold(
                onSuccess = { _state.value = HistoryListState.Loaded(it.items, it.nextCursor) },
                // 이미 보이던 목록이 있으면 지우지 않는다 — 다시 불러오다 실패해도 보던 것은 남긴다.
                onFailure = { if (_state.value !is HistoryListState.Loaded) _state.value = HistoryListState.Failed }
            )
        }
    }

    /** 스크롤이 끝에 닿으면 다음 묶음. 이미 받는 중이거나 끝이면 아무것도 안 한다. */
    fun loadMore() {
        val current = _state.value as? HistoryListState.Loaded
        val cursor = current?.nextCursor
        if (current == null || cursor == null || current.loadingMore) return
        _state.value = current.copy(loadingMore = true, moreFailed = false)
        moreJob = viewModelScope.launch {
            repository.page(cursor).fold(
                onSuccess = { page ->
                    _state.update {
                        val now = it as? HistoryListState.Loaded ?: return@update it
                        // 같은 기록이 두 번 오지 않게 ID 로 거른다 — 그 사이 새 기록이 저장되면 경계가 밀린다.
                        val seen = now.items.mapTo(HashSet()) { item -> item.historyId }
                        now.copy(
                            items = now.items + page.items.filterNot { item ->
                                item.historyId in seen
                            },
                            nextCursor = page.nextCursor,
                            loadingMore = false
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        (it as? HistoryListState.Loaded)?.copy(loadingMore = false, moreFailed = true)
                            ?: it
                    }
                }
            )
        }
    }
}
