package app.nursemate.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.auth.AuthError
import app.nursemate.core.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 로그인 화면 상태.
 *
 * 성공은 여기서 신호하지 않는다 — [AuthRepository.session]이 `SignedIn`으로 바뀌고
 * 그걸 본 네비게이션이 화면을 옮긴다. 성공 플래그를 따로 두면 세션과 어긋날 수 있다.
 */
data class LoginUiState(val inProgress: Boolean = false, val error: AuthError? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    /**
     * @param activityContext 자격 증명 선택 UI를 띄울 **Activity** 컨텍스트.
     *
     * ViewModel이 Context를 받는 건 보통 피해야 하지만, Credential Manager가 Activity를 요구해서
     * 달리 방법이 없다. **보관하지 않고** 호출 안에서만 쓴다 — 필드에 담으면 화면 회전 시 누수된다.
     */
    fun signInWithGoogle(activityContext: Context) {
        if (_state.value.inProgress) return
        _state.update { LoginUiState(inProgress = true) }

        viewModelScope.launch {
            googleIdTokenProvider.request(activityContext)
                .mapCatching { idToken -> authRepository.signInWithGoogle(idToken).getOrThrow() }
                .onSuccess {
                    // 화면 전환은 세션 관찰자가 한다. 여기서는 진행 표시만 끈다.
                    _state.update { it.copy(inProgress = false) }
                }
                .onFailure { throwable ->
                    // 사용자가 계정 선택을 닫은 건 오류가 아니다 — 문구 없이 원래 화면으로 되돌린다.
                    when (val error = throwable.toAuthError()) {
                        is AuthError.Cancelled -> _state.update { LoginUiState() }

                        else -> {
                            Log.w(TAG, "구글 로그인 실패", throwable)
                            _state.update { LoginUiState(error = error) }
                        }
                    }
                }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        const val TAG = "NM407"
    }
}
