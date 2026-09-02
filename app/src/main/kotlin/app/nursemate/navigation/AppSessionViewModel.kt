package app.nursemate.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.auth.AuthRepository
import app.nursemate.core.data.auth.AuthSession
import app.nursemate.core.data.auth.UserRepository
import app.nursemate.core.model.User
import app.nursemate.core.network.error.ApiFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 앱 셸이 보는 인증 세션과 회원 정보.
 *
 * 스펙 `spec/feature/auth/README.md` §진입 라우팅은 앱 실행·포그라운드 복귀마다 세션과
 * `onboardingRequired` 로 화면을 정한다. 그 판단을 화면 하나가 아니라 **셸**이 하도록 여기에 둔다.
 */
@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    val session = authRepository.session

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.session
                // uid 로 좁혀서 같은 세션에 두 번 부르지 않는다. 재구성마다 호출하면 낭비다.
                .map { (it as? AuthSession.SignedIn)?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    if (uid == null) {
                        _user.value = null
                    } else {
                        loadUser()
                    }
                }
        }
    }

    /**
     * 실패해도 **화면을 전환하지 않는다**(spec §진입 라우팅).
     *
     * `onboardingRequired` 를 모르는 채로 홈이나 로그인으로 보내면 잘못된 화면에 갇힌다.
     * 특히 500 은 공급자 불일치일 수 있는데, 서버 쪽 상태 문제라 재로그인해도 같은 응답이 온다.
     */
    private suspend fun loadUser() {
        userRepository.me()
            .onSuccess { fetched ->
                _user.value = fetched
                Log.i(
                    TAG,
                    "users/me 200 · uid=${fetched.userId} provider=${fetched.provider} " +
                        "onboardingRequired=${fetched.onboardingRequired} consents=${fetched.consents}"
                )
            }
            .onFailure { throwable ->
                val failure = throwable as? ApiFailure
                Log.w(
                    TAG,
                    "users/me 실패 · status=${failure?.httpStatus} code=${failure?.code} " +
                        "retryable=${failure?.isRetryable} requiresSignIn=${failure?.requiresSignIn}",
                    throwable
                )
            }
    }

    private companion object {
        const val TAG = "NM392"
    }
}
