package app.nursemate.pill

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.pill.PillRepository
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.model.PillConditions
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
 * @param ids 서버가 정렬한 **전체 순서**(최대 200). 21번째부터의 상세는 이 순서로 이어 받는다
 * @param truncated 하드 필터를 통과한 후보가 200개를 넘어 뒤가 잘렸는가 — 목록 끝 안내의 기준
 * @param loadingMore 뒤쪽을 받는 중. 첫 조회([loading])와 구분해야 목록을 지우지 않는다.
 */
data class CandidateUiState(
    val candidates: List<PillCandidate> = emptyList(),
    val ids: List<String> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val failed: Boolean = false,
    val truncated: Boolean = false,
    val loadingMore: Boolean = false
) {
    /** 아직 상세를 못 받은 후보가 남아 있는가. 커서가 아니라 **받은 개수**로 센다. */
    val hasMore: Boolean get() = candidates.size < ids.size
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

    fun search(conditions: PillConditions?, faces: FaceInputs = FaceInputs()) {
        searchJob?.cancel()
        moreJob?.cancel()

        // ⚠️ 조건이 하나도 없으면 **부르지 않는다.** 서버가 400 을 주고 화면에는 오류가 뜨는데,
        //    사용자는 아직 아무것도 입력하지 않았을 뿐이다(수동 추가 진입 직후 — 정본 ⑧-h).
        val request = conditions?.toRequest(faces)?.takeIf { it.hasCondition }
        if (request == null) {
            _state.value = CandidateUiState()
            return
        }

        // ⚠️ 기다리기 **전에** 표시를 켠다. 뒤에 켜면 화면이 그 사이 "아직 조건이 없다"는
        //    안내(⑧-h)를 잠깐 띄웠다 지운다 — 조회가 이미 예약된 상태인데 반대로 말하는 셈이다.
        _state.update { it.copy(loading = true, failed = false) }

        searchJob = viewModelScope.launch {
            // 타이핑이 멈춘 뒤에 보낸다. 글자마다 왕복하면 서버도 화면도 요동친다.
            delay(DEBOUNCE_MS)

            pillRepository.candidates(request)
                .onSuccess { result ->
                    Log.i(TAG, "후보 ${result.ids.size}개 (상세 ${result.candidates.size}) 잘림=${result.truncated}")
                    _state.value = CandidateUiState(
                        candidates = result.candidates,
                        ids = result.ids,
                        loading = false,
                        searched = true,
                        truncated = result.truncated
                    )
                }
                .onFailure { throwable ->
                    Log.w(TAG, "후보 조회 실패", throwable)
                    _state.update { it.copy(loading = false, searched = true, failed = true) }
                }
        }
    }

    /**
     * 아직 상세를 못 받은 후보를 이어 붙인다 (NM-489).
     *
     * 목록 끝에 닿을 때마다 불리므로 **이미 받고 있으면 무시한다.**
     *
     * ## 순서는 우리가 잡는다
     * 서버 응답은 순서를 보장하지 않으므로 [CandidateUiState.ids] 순서대로 다시 배치한다.
     * `missing`(데이터 갱신으로 사라진 품목)은 **목록에서 뺀다** — 로딩 중으로 남기면
     * 영원히 안 채워진다.
     */
    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loadingMore) return

        val next = current.ids.drop(current.candidates.size).take(PAGE_SIZE)
        if (next.isEmpty()) return

        moreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            pillRepository.candidateItems(next)
                .onSuccess { page ->
                    _state.update { state ->
                        val byCode = page.items.associateBy { it.pillCode }
                        // ids 순서대로 꽂는다. 없는 것(missing)은 그대로 빠진다.
                        val added = next.mapNotNull(byCode::get)
                        state.copy(
                            candidates = state.candidates + added,
                            // 사라진 품목을 ids 에서도 지운다 — 안 그러면 hasMore 가 영영 참이다.
                            ids = state.ids - page.missing.toSet(),
                            loadingMore = false
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.w(TAG, "후보 카드 이어받기 실패", throwable)
                    // 목록은 그대로 두고 표시만 끈다 — 받아 둔 후보까지 잃을 이유가 없다.
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }

    private companion object {
        const val TAG = "NM393"

        /** 한 번에 이어받을 후보 카드 수. 첫 응답이 20개를 주므로 같은 단위로 맞춘다. */
        const val PAGE_SIZE = 20
        const val DEBOUNCE_MS = 250L
    }
}

/**
 * 사용자 조건 → 후보 검색 요청.
 *
 * ⚠️ **모델값은 여기 들어오지 않는다.** [PillConditions] 에 담긴 것은 사용자가 고른 값뿐이고,
 * 모델이 본 색·모양·제형은 `attributeToken` 하나로 전달돼 **정렬에만** 쓰인다.
 *
 * 면 조건은 [FaceInput.toRequest] 가 만든다 — 요청의 null 이 "조건 제외"라 '없음'을 걸려면
 * 값을 명시해야 하고, 그 판단은 화면 입력값을 봐야 할 수 있는 일이다.
 */
private fun PillConditions.toRequest(faces: FaceInputs) = PillCandidatesRequest(
    attributeToken = attributeToken,
    colors = colors,
    shape = shape,
    formulation = formulation,
    front = faces.front.toRequest(),
    back = faces.back.toRequest()
)

/**
 * 서버에 물어볼 게 하나라도 있는가.
 *
 * 토큰만 있어도 부를 값어치가 있다 — 조건 없이도 모델값 정렬로 후보가 나온다.
 * 다 비어 있을 때만(수동 추가 직후) 부르지 않는다.
 */
private val PillCandidatesRequest.hasCondition: Boolean
    get() = attributeToken != null || colors.isNotEmpty() || shape != null ||
        formulation != null || front != null || back != null
