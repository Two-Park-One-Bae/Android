package app.nursemate.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.pill.PillRepository
import app.nursemate.core.model.PillDetail
import app.nursemate.core.network.error.ApiFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.HttpURLConnection
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 세부정보 화면의 상태 — 디자인 ⑩-a·b·e·f·g 와 1:1 이다.
 *
 * '없음'([Empty])과 '실패'([Failed])를 반드시 가른다. 없는 것은 다시 눌러도 없으므로
 * 재시도 버튼을 두지 않고, 실패는 재시도가 유일한 길이다.
 */
sealed interface DetailUiState {
    data object Loading : DetailUiState

    data class Ready(val detail: PillDetail) : DetailUiState

    /** 404 `PILL_DETAIL_NOT_FOUND` — 미적재 품목·모르는 코드가 한 코드로 온다(NM-309). */
    data object Empty : DetailUiState

    data object Failed : DetailUiState

    /** 허가 종료 품목. **조회하지 않고** 여기서 끝낸다(NM-369). */
    data object Revoked : DetailUiState
}

@HiltViewModel
class PillDetailViewModel @Inject constructor(private val pillRepository: PillRepository) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state = _state.asStateFlow()

    private var pillCode: String? = null

    /**
     * @param revoked 허가 종료 품목인가. true 면 서버를 부르지 않는다 — 허가정보가 남아 있는
     *                품목도 안내로 끝내기로 정해져 있어(NM-369) 물어볼 이유가 없다.
     */
    fun load(pillCode: String, revoked: Boolean) {
        if (this.pillCode == pillCode && _state.value !is DetailUiState.Failed) return
        this.pillCode = pillCode

        if (revoked) {
            _state.value = DetailUiState.Revoked
            return
        }
        fetch(pillCode)
    }

    fun retry() {
        pillCode?.let { fetch(it) }
    }

    private fun fetch(pillCode: String) {
        _state.value = DetailUiState.Loading
        viewModelScope.launch {
            pillRepository.detail(pillCode)
                .onSuccess { _state.value = DetailUiState.Ready(it) }
                .onFailure { throwable ->
                    val notFound = (throwable as? ApiFailure)?.httpStatus == HttpURLConnection.HTTP_NOT_FOUND
                    if (notFound) {
                        Log.i(TAG, "세부정보 없음 · pillCode=$pillCode")
                        _state.value = DetailUiState.Empty
                    } else {
                        Log.w(TAG, "세부정보 조회 실패", throwable)
                        _state.value = DetailUiState.Failed
                    }
                }
        }
    }

    private companion object {
        const val TAG = "NM312"
    }
}
