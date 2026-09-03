package app.nursemate.pill

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.pill.PillRepository
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.network.api.PillCandidatesRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @param candidates 조건에 맞는 후보. **빈 목록은 오류가 아니다** — 조건이 좁아 아무것도
 *                   걸리지 않은 정상 응답이다(spec §후보 0개).
 * @param searched 한 번이라도 조회했는가. 조회 전의 빈 목록과 "0개"를 구분한다.
 * @param nextCursor 다음 장 커서. null 이면 더 없다.
 * @param loadingMore 다음 장을 받는 중. 첫 조회([loading])와 구분해야 목록을 지우지 않는다.
 */
data class CandidateUiState(
    val candidates: List<PillCandidate> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val failed: Boolean = false,
    val nextCursor: String? = null,
    val loadingMore: Boolean = false
) {
    val hasMore: Boolean get() = nextCursor != null
}

/**
 * 수정 화면의 후보 조회.
 *
 * ## 속성이 바뀔 때마다 다시 부른다
 * 스펙이 "속성 변경마다 재호출(실시간)"이다. 각인을 한 글자씩 칠 때도 불리므로
 * **이전 요청을 취소**하고 짧게 기다렸다 보낸다 — 안 그러면 늦게 도착한 옛 응답이
 * 최신 결과를 덮어쓴다.
 *
 * 후보 조회는 Gemini 를 쓰지 않아 **식별 횟수를 깎지 않는다.** 자주 불러도 된다.
 */
@HiltViewModel
class PillCandidateViewModel @Inject constructor(private val pillRepository: PillRepository) : ViewModel() {

    private val _state = MutableStateFlow(CandidateUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null
    private var moreJob: Job? = null

    /** 다음 장을 받으려면 그때 쓴 조건이 그대로 필요하다 — 커서만으로는 서버가 뭘 찾는지 모른다. */
    private var lastRequest: PillCandidatesRequest? = null

    fun search(attribute: PillAttribute?, faces: FaceInputs = FaceInputs()) {
        searchJob?.cancel()
        moreJob?.cancel()
        if (attribute == null) {
            lastRequest = null
            _state.value = CandidateUiState()
            return
        }

        searchJob = viewModelScope.launch {
            // 타이핑이 멈춘 뒤에 보낸다. 글자마다 왕복하면 서버도 화면도 요동친다.
            delay(DEBOUNCE_MS)
            _state.update { it.copy(loading = true, failed = false) }

            val request = attribute.toRequest(faces)
            lastRequest = request

            pillRepository.candidates(request)
                .onSuccess { page ->
                    Log.i(
                        TAG,
                        "후보 ${page.candidates.size}개" +
                            page.candidates.take(2).joinToString { " | ${it.pillCode} ${it.pillThumbnailUrl}" }
                    )
                    _state.value = CandidateUiState(
                        candidates = page.candidates,
                        loading = false,
                        searched = true,
                        nextCursor = page.nextCursor
                    )
                }
                .onFailure { throwable ->
                    Log.w(TAG, "후보 조회 실패", throwable)
                    _state.update { it.copy(loading = false, searched = true, failed = true) }
                }
        }
    }

    /**
     * 다음 장을 이어 붙인다.
     *
     * 목록 끝에 닿을 때마다 불리므로 **이미 받고 있으면 무시한다** — 안 그러면 같은 장을
     * 여러 번 받아 후보가 중복된다.
     */
    fun loadMore() {
        val request = lastRequest
        val cursor = _state.value.nextCursor
        // 조건이 없거나(아직 조회 전) 다음 장이 없거나 이미 받는 중이면 아무것도 하지 않는다.
        if (request == null || cursor == null || _state.value.loadingMore) return

        moreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            pillRepository.candidates(request.copy(cursor = cursor))
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            candidates = it.candidates + page.candidates,
                            nextCursor = page.nextCursor,
                            loadingMore = false
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.w(TAG, "다음 후보 장 실패", throwable)
                    // 목록은 그대로 두고 표시만 끈다 — 받아 둔 후보까지 잃을 이유가 없다.
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }

    private companion object {
        const val TAG = "NM393"
        const val DEBOUNCE_MS = 250L
    }
}

/**
 * 속성·각인 → 후보 검색 조건.
 *
 * 면 조건은 [FaceInput.toRequest] 가 만든다 — 요청의 null 이 "조건 제외"라 '없음'을 걸려면
 * 값을 명시해야 하고, 그 판단은 화면 입력값을 봐야 할 수 있는 일이다.
 */
private fun PillAttribute.toRequest(faces: FaceInputs) = PillCandidatesRequest(
    colors = colors.orEmpty(),
    isTransparent = isTransparent.takeIf { it },
    shape = shape,
    formulation = formulation,
    front = faces.front.toRequest(),
    back = faces.back.toRequest()
)
