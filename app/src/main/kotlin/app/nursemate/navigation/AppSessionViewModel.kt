package app.nursemate.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.auth.AuthRepository
import app.nursemate.core.data.auth.AuthSession
import app.nursemate.core.data.auth.UserRepository
import app.nursemate.core.data.pill.UsageHolder
import app.nursemate.core.model.User
import app.nursemate.core.network.error.ApiFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val userRepository: UserRepository,
    private val usageHolder: UsageHolder
) : ViewModel() {

    private val currentUser = MutableStateFlow<User?>(null)

    /** 회원 정보를 한 번도 못 받았는가. 받은 뒤의 실패는 화면을 흔들지 않으므로 여기 반영하지 않는다. */
    private val initialLoadFailed = MutableStateFlow(false)

    private val forcedSignOut = MutableStateFlow(false)

    /**
     * 내 의사와 무관하게 세션이 끊겼는가 — 로그인 화면이 **왜 여기 와 있는지** 알리는 데 쓴다.
     *
     * ## 로그아웃과 만료는 결론만 같다
     * 둘 다 로그인 화면으로 보내지만 사용자에게는 전혀 다른 사건이다. 내가 누른 로그아웃은
     * 설명이 필요 없고, 세션 만료는 **내가 한 일이 아니라** 이유를 말해 주지 않으면 앱이
     * 고장난 것으로 읽힌다. 예전에는 둘을 구분하지 않고 [AuthRepository.signOut] 만 불렀고,
     * 홈에 있던 사용자가 아무 안내 없이 로그인 화면으로 돌아오는 증상이 났다.
     *
     * 화면이 아니라 여기서 신호하는 이유는 **만료를 아는 곳이 여기뿐**이기 때문이다 —
     * 로그인 화면은 세션이 이미 없어진 뒤에 그려져 사유를 알 길이 없다.
     */
    val sessionExpired: StateFlow<Boolean> = forcedSignOut.asStateFlow()

    /**
     * 동의 화면이 **개정 재동의**인가 — 안내 한 장을 앞세울지만 가른다(spec §개정 재동의).
     *
     * ⚠️ [entry] 와 역할이 다르다. 들여보낼지 말지는 서버가 준 `onboardingRequired` 가 쥐고
     * (아래 [entry] 주석), 이 값은 **어느 문구를 보일지**만 정한다. 그래서 여기서만
     * `consents` 를 본다 — 틀려도 문구가 어긋날 뿐 게이트는 흔들리지 않는다.
     */
    val needsReconsent: StateFlow<Boolean> = currentUser
        .map { it?.needsReconsent == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
                        // 앞사람의 동의 상태나 남은 식별 횟수가 보이면 안 된다.
                        currentUser.value = null
                        initialLoadFailed.value = false
                        usageHolder.clear()
                    } else {
                        // 로그인에 성공했으니 지난 만료 안내는 역할을 다했다. 남겨 두면 나중에
                        // 스스로 로그아웃하고 돌아왔을 때 엉뚱한 이유가 떠 있다.
                        forcedSignOut.value = false
                        loadUser()
                    }
                }
        }
    }

    /**
     * 회원 정보를 다시 받는다.
     *
     * ⚠️ **포그라운드 복귀마다 부르지 않는다**(NM-463). 재동의 판정 시점은 **앱 실행 때**다
     * (spec §진입 라우팅: 「포그라운드 복귀에는 다시 판정하지 않는다」). 복귀마다 부르면
     * 다른 일을 하다 돌아온 사용자가 쓰던 화면에서 동의 시트로 끌려 나온다.
     *
     * 지금 부르는 곳은 회원 조회에 실패해 멈춰 선 화면의 「다시 시도」뿐이다.
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
                    // 화면을 갈아끼우기 **전에** 세운다. signOut 이 세션을 SignedOut 으로 바꾸면
                    // 그 즉시 로그인 화면이 그려지는데, 그때 이미 이유가 있어야 한 박자 늦게
                    // 안내가 튀어나오지 않는다.
                    forcedSignOut.value = true
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
