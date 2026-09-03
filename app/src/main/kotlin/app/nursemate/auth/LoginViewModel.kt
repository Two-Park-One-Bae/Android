package app.nursemate.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.auth.AuthError
import app.nursemate.core.data.auth.AuthRepository
import app.nursemate.core.network.api.AuthApi
import app.nursemate.core.network.api.KakaoTokenRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 로그인 버튼 종류. 진행 표시를 **누른 버튼 위에** 그리려고 구분한다. */
enum class LoginProvider { Google, Apple, Kakao }

/**
 * 로그인 화면 상태.
 *
 * 성공은 여기서 신호하지 않는다 — [AuthRepository.session]이 `SignedIn`으로 바뀌고
 * 그걸 본 네비게이션이 화면을 옮긴다. 성공 플래그를 따로 두면 세션과 어긋날 수 있다.
 *
 * @param pending 진행 중인 공급자. null 이면 대기 상태다. 불리언 하나로 두면 카카오를
 *                누르고 구글 버튼에 인디케이터가 도는 꼴이 된다.
 */
data class LoginUiState(val pending: LoginProvider? = null, val error: AuthError? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val authRepository: AuthRepository,
    private val kakaoAccessTokenProvider: KakaoAccessTokenProvider,
    private val authApi: AuthApi
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    init {
        // 애플만 브라우저에 다녀오는 사이 우리 앱이 죽을 수 있다. 그때 남은 결과를 이어받는다.
        // 평소에는 이어받을 게 없어 즉시 null 이라 화면에 아무 흔적도 남지 않는다 —
        // 그래서 여기서는 진행 표시를 켜지 않는다.
        viewModelScope.launch {
            val result = authRepository.resumeAppleSignIn() ?: return@launch
            result.finish("애플 로그인 이어받기 실패", Throwable::toAppleAuthError)
        }
    }

    /**
     * @param activityContext 자격 증명 선택 UI를 띄울 **Activity** 컨텍스트.
     *
     * ViewModel이 Context를 받는 건 보통 피해야 하지만, Credential Manager가 Activity를 요구해서
     * 달리 방법이 없다. **보관하지 않고** 호출 안에서만 쓴다 — 필드에 담으면 화면 회전 시 누수된다.
     */
    fun signInWithGoogle(activityContext: Context) {
        if (_state.value.pending != null) return
        _state.update { LoginUiState(pending = LoginProvider.Google) }

        viewModelScope.launch {
            googleIdTokenProvider.request(activityContext)
                .mapCatching { idToken -> authRepository.signInWithGoogle(idToken).getOrThrow() }
                .finish("구글 로그인 실패", Throwable::toAuthError)
        }
    }

    /**
     * 애플 — Firebase가 웹 플로우 창을 직접 띄운다. 그래서 [Activity]가 필요하다
     * (`AuthRepository.signInWithApple` 주석 참고).
     */
    fun signInWithApple(activity: Activity) {
        if (_state.value.pending != null) return
        _state.update { LoginUiState(pending = LoginProvider.Apple) }

        viewModelScope.launch {
            authRepository.signInWithApple(activity)
                .finish("애플 로그인 실패", Throwable::toAppleAuthError)
        }
    }

    /**
     * 카카오 — **두 단계**다. 카카오에서 액세스 토큰을 받고, 서버가 그걸 Firebase Custom Token 으로
     * 바꿔 준 뒤에야 Firebase 에 로그인한다. 구글·애플은 Firebase 네이티브라 한 단계다.
     *
     * 중간에 실패하면 그 지점에서 멈춘다 — 서버 교환이 401 이면 우리 카카오 앱에서 발급된
     * 토큰이 아니라는 뜻이고, 503 이면 카카오·Firebase 일시 장애다.
     */
    fun signInWithKakao(activityContext: Context) {
        if (_state.value.pending != null) return
        _state.update { LoginUiState(pending = LoginProvider.Kakao) }

        viewModelScope.launch {
            kakaoAccessTokenProvider.request(activityContext)
                .mapCatching { kakaoToken -> authApi.exchangeKakaoToken(KakaoTokenRequest(kakaoToken)) }
                .mapCatching { response ->
                    authRepository.signInWithCustomToken(response.firebaseCustomToken).getOrThrow()
                }
                .finish("카카오 로그인 실패", Throwable::toKakaoAuthError)
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /**
     * 로그인 시도의 끝. 세 공급자가 공유한다.
     *
     * 성공해도 화면을 옮기지 않는다 — 세션 관찰자가 한다. 여기서는 진행 표시만 끈다.
     * 사용자가 창을 닫은 건 오류가 아니라, 문구 없이 원래 화면으로 되돌린다.
     *
     * @param toError 공급자마다 취소를 알아보는 방법이 달라 밖에서 받는다.
     */
    private fun Result<Unit>.finish(failureLog: String, toError: (Throwable) -> AuthError) {
        onSuccess { _state.update { it.copy(pending = null) } }
        onFailure { throwable ->
            when (val error = toError(throwable)) {
                is AuthError.Cancelled -> _state.update { LoginUiState() }

                else -> {
                    Log.w(TAG, failureLog, throwable)
                    _state.update { LoginUiState(error = error) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "NM407"
    }
}
