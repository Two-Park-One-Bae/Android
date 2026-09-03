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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 앱이 지금 어느 화면에서 시작해야 하는가.
 *
 * 스펙 `spec/feature/auth/README.md` §진입 라우팅의 판단을 **한 값으로** 모은다.
 * 세션과 `onboardingRequired` 를 화면마다 따로 보면 "로그인은 됐는데 동의는 안 된" 중간 상태에서
 * 서로 다른 결론을 내린다.
 */
sealed interface AppEntry {
    /** 세션 복원 전이거나 회원 정보를 기다리는 중. 스플래시를 유지한다. */
    data object Loading : AppEntry

    data object Login : AppEntry

    /** 필수 동의 미충족. 신규 가입이거나 약관이 개정된 경우다. */
    data object Consent : AppEntry

    data object Home : AppEntry

    /**
     * 로그인은 됐는데 회원 정보를 못 받았다.
     *
     * **로그아웃시키지 않는다**(spec §오류 처리) — 500 은 서버 쪽 상태 문제라 재로그인해도 같은
     * 응답이 오고, 503 은 일시 장애다. 여기서 로그인 화면으로 보내면 사용자가 빠져나갈 방법이 없다.
     */
    data object Unavailable : AppEntry
}

/**
 * 앱 셸이 보는 인증 세션과 회원 정보.
 *
 * 진입 판단을 화면 하나가 아니라 **셸**이 하도록 여기에 둔다.
 */
@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val currentUser = MutableStateFlow<User?>(null)

    /** 회원 정보를 한 번도 못 받았는가. 받은 뒤의 실패는 화면을 흔들지 않으므로 여기 반영하지 않는다. */
    private val initialLoadFailed = MutableStateFlow(false)

    val entry: StateFlow<AppEntry> =
        combine(authRepository.session, currentUser, initialLoadFailed) { session, user, failed ->
            when (session) {
                AuthSession.Unknown -> AppEntry.Loading

                AuthSession.SignedOut -> AppEntry.Login

                is AuthSession.SignedIn -> when {
                    // 서버가 준 onboardingRequired 만 믿는다. consents 를 훑어 직접 계산하면
                    // 서버가 필수 항목을 늘렸을 때 어긋난다(spec §동의 온보딩).
                    user != null -> if (user.onboardingRequired) AppEntry.Consent else AppEntry.Home

                    failed -> AppEntry.Unavailable

                    else -> AppEntry.Loading
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppEntry.Loading)

    init {
        viewModelScope.launch {
            authRepository.session
                // uid 로 좁혀서 같은 세션에 두 번 부르지 않는다. 재구성마다 호출하면 낭비다.
                .map { (it as? AuthSession.SignedIn)?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    if (uid == null) {
                        // 계정 스코프 캐시 폐기(spec §로그아웃·탈퇴) — 병동 공용 기기에서
                        // 앞사람의 동의 상태가 남아 보이면 안 된다.
                        currentUser.value = null
                        initialLoadFailed.value = false
                    } else {
                        loadUser()
                    }
                }
        }
    }

    /**
     * 회원 정보를 다시 받는다.
     *
     * 포그라운드 복귀마다 부른다 — 약관이 개정되면 `onboardingRequired` 가 다시 true 가 되고,
     * 그래야 다음 진입에서 동의 화면이 뜬다(spec §약관 개정).
     */
    fun refresh() {
        if (authRepository.session.value !is AuthSession.SignedIn) return
        viewModelScope.launch { loadUser() }
    }

    /** 동의 저장 응답으로 받은 회원 정보를 반영한다. 그 순간 진입 상태가 [AppEntry.Home] 으로 넘어간다. */
    fun onUserUpdated(user: User) {
        currentUser.value = user
        initialLoadFailed.value = false
    }

    private suspend fun loadUser() {
        userRepository.me()
            .onSuccess { fetched ->
                currentUser.value = fetched
                initialLoadFailed.value = false
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
                if (failure?.requiresSignIn == true) {
                    // 401 — 갱신까지 실패한 진짜 세션 만료다(spec §토큰·세션). 예를 들어 다른
                    // 기기에서 탈퇴하면 서버가 리프레시 토큰을 폐기하는데, 이 기기의 Firebase
                    // 세션은 로컬에 남아 있어 SignedIn 인 채로 401 만 반복된다 — 재시도 화면에
                    // 머무르게 두지 않고 로그아웃해 로그인으로 돌려보낸다.
                    authRepository.signOut()
                    return@onFailure
                }
                // 이미 받아 둔 회원 정보가 있으면 건드리지 않는다 — 홈에 있던 사용자를
                // 일시 장애 때문에 재시도 화면으로 끌어내리지 않는다.
                if (currentUser.value == null) initialLoadFailed.value = true
            }
    }

    private companion object {
        const val TAG = "NM392"
    }
}
