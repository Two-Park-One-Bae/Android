package app.nursemate.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.auth.AuthRepository
import app.nursemate.core.data.auth.UserRepository
import app.nursemate.core.network.error.ApiFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @param deleting 탈퇴 요청 중. 이 동안 두 항목 모두 못 누르게 한다 —
 *                 삭제가 진행되는 사이 로그아웃하면 요청 결과를 확인할 길이 없다.
 */
data class SettingsUiState(val deleting: Boolean = false, val message: String? = null)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    /** 화면 전환은 하지 않는다 — 세션이 끊기면 셸이 로그인으로 보낸다. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /**
     * 탈퇴 — 서버 삭제가 **성공한 뒤에만** 로그아웃한다.
     *
     * 순서를 뒤집으면 토큰이 사라져 삭제 요청을 보낼 수 없다. 그리고 실패했는데 로그아웃하면
     * 계정이 남아 있는 채로 "삭제됐다"고 안내하는 꼴이 된다(spec §탈퇴).
     */
    fun deleteAccount() {
        if (_state.value.deleting) return
        _state.update { SettingsUiState(deleting = true) }

        viewModelScope.launch {
            userRepository.delete()
                .onSuccess { authRepository.signOut() }
                .onFailure { throwable ->
                    Log.w(TAG, "탈퇴 실패", throwable)
                    val failure = throwable as? ApiFailure
                    // 401 은 이미 지워진 계정이다 — 서버가 삭제 시 리프레시 토큰을 폐기하므로
                    // 남은 토큰이 거부된다. 재시도해도 같으니 세션만 정리한다.
                    if (failure?.requiresSignIn == true) {
                        authRepository.signOut()
                    } else {
                        _state.update { SettingsUiState(message = DELETE_FAILED) }
                    }
                }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val TAG = "NM408"
        const val DELETE_FAILED = "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요"
    }
}
