package app.nursemate.consent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.attribution.AttributionTracker
import app.nursemate.core.data.auth.AuthRepository
import app.nursemate.core.data.auth.ConsentRepository
import app.nursemate.core.model.ConsentDefinition
import app.nursemate.core.model.ConsentStatus
import app.nursemate.core.model.ConsentType
import app.nursemate.core.model.User
import app.nursemate.core.network.error.ApiFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 동의 온보딩 상태.
 *
 * @param definitions **화면에 보일** 항목. 서버가 준 것에서 모르는 선택 항목과 이미 응답한 선택
 *   항목을 걷어낸 결과다([ConsentViewModel.load]). 비어 있으면 로딩 중이거나 못 받은 것이다.
 * @param checked 체크된 항목. **빈 집합으로 시작한다**(spec §동의 온보딩 「모든 항목은 체크되지
 *   않은 상태로 시작한다」) — 필수라고 미리 켜 두면 동의를 받은 것이 아니라 끼운 것이 된다.
 * @param blocked 앱이 모르는 **필수** 항목이 내려왔다. 보내 봐야 400 이라 진행할 수 없다.
 */
data class ConsentUiState(
    val definitions: List<ConsentDefinition> = emptyList(),
    val checked: Set<ConsentType> = emptySet(),
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val blocked: Boolean = false,
    val message: String? = null
) {
    /**
     * 필수 항목이 **전부** 체크됐는가. 하나라도 빠지면 '동의하고 계속'이 눌리지 않는다.
     *
     * **선택 항목은 보지 않는다**(spec §동의 온보딩 「`동의하고 계속`은 필수 항목만 체크돼도
     * 활성화된다」) — 선택을 눌러야 넘어갈 수 있으면 그건 선택이 아니다.
     */
    val canSubmit: Boolean
        get() = !loading && !submitting && !blocked &&
            definitions.isNotEmpty() &&
            definitions.filter { it.required }.all { it.type in checked }
}

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val consentRepository: ConsentRepository,
    private val authRepository: AuthRepository,
    private val attribution: AttributionTracker
) : ViewModel() {

    private val _state = MutableStateFlow(ConsentUiState())
    val state = _state.asStateFlow()

    /**
     * 회원이 **어느 버전에 응답했는지** — 선택 항목을 보일지 가리는 데만 쓴다(NM-548).
     *
     * ## 여기서 `GET /users/me` 를 다시 부르지 않는다
     * 이 화면에 오기까지 셸이 이미 받아 뒀다([app.nursemate.navigation.AppSessionViewModel]).
     * 같은 값을 한 번 더 받으면 동의 시트가 뜨는 길목에 네트워크 왕복이 하나 더 끼고, 두 응답이
     * 어긋나면 어느 쪽을 믿을지 정해야 한다. 그래서 `init` 에서 스스로 로드하지 않고
     * [start] 로 **셸이 넣어 줄 때까지** 기다린다.
     */
    private var answered: List<ConsentStatus> = emptyList()

    private var started = false

    /**
     * 셸이 회원의 동의 기록을 넣고 첫 조회를 시작한다 — 화면이 뜰 때 **한 번**.
     *
     * 재구성마다 불려도 두 번 로드하지 않는다. 화면 회전이나 다이얼로그 하나로 약관을 다시
     * 받아 오면 그 사이 체크해 둔 것이 전부 풀린다.
     */
    fun start(consents: List<ConsentStatus>) {
        if (started) return
        started = true
        answered = consents
        load()
    }

    /**
     * @param pendingMessage 재조회 뒤에도 남겨 둘 안내. 예를 들어 저장 중 약관이 개정돼
     *   다시 부르는 경우([submit] 참고) — 그 안내를 여기서 지우면 사용자는 화면이
     *   이유 없이 다시 그려지는 것만 보고 왜 그런지 모른다.
     */
    fun load(pendingMessage: String? = null) {
        _state.update { ConsentUiState(loading = true, message = pendingMessage) }
        viewModelScope.launch {
            consentRepository.definitions()
                .onSuccess { definitions ->
                    // 앱이 모르는 필수 항목은 되돌려 보낼 수가 없다 — enum 이 UNKNOWN 으로 떨어지고
                    // 그대로 보내면 서버가 400 을 준다. 다시 받아도 같은 값이라 무한히 맴돈다.
                    // 여기서 멈추고 업데이트를 안내하는 편이 정직하다.
                    val unknown = definitions.any { it.required && it.type == ConsentType.UNKNOWN }
                    _state.update {
                        ConsentUiState(
                            definitions = definitions.visibleFor(answered),
                            loading = false,
                            blocked = unknown,
                            message = if (unknown) UPDATE_REQUIRED else pendingMessage
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.w(TAG, "동의 항목 조회 실패", throwable)
                    _state.update { ConsentUiState(loading = false, message = throwable.toMessage()) }
                }
        }
    }

    fun toggle(type: ConsentType) = _state.update { current ->
        val checked = if (type in current.checked) current.checked - type else current.checked + type
        current.copy(checked = checked, message = null)
    }

    /**
     * 전체 동의 — 하나라도 빠져 있으면 모두 켜고, 이미 전부 켜져 있으면 모두 끈다.
     *
     * **선택 항목까지 켠다**(spec §동의 온보딩). 「전체」가 필수만 뜻하면 선택 항목이 꺼진 채로
     * 전체 동의가 체크돼 보인다 — 무엇에 동의했는지 화면이 거짓으로 말하게 된다.
     * 누른 뒤에도 항목별로 다시 끌 수 있어야 하므로, 여기서 잠그는 것은 없다.
     */
    fun toggleAll() = _state.update { current ->
        val all = current.definitions.map { it.type }.toSet()
        val checked = if (current.checked.containsAll(all)) emptySet() else all
        current.copy(checked = checked, message = null)
    }

    /**
     * @param firstTime **최초 가입**인가 — 유입 측정의 가입 이벤트를 보낼지 가른다(NM-543).
     *                  약관 개정 재동의는 가입이 아니다. 최초인지는 이 화면이 아니라 셸이 아는
     *                  값이라(`User.needsReconsent`) 인자로 받는다 — 여기서 `consents` 를
     *                  다시 세지 않는다.
     * @param onAgreed 갱신된 회원. 진입 상태를 홈으로 넘기는 건 셸이 한다 —
     *                 `onboardingRequired` 를 여기서 단정하지 않고 응답 값을 그대로 올린다.
     */
    fun submit(firstTime: Boolean, onAgreed: (User) -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, message = null) }

        viewModelScope.launch {
            consentRepository.agree(current.definitions, current.checked)
                .onSuccess { user ->
                    // ⚠️ **저장이 성공한 뒤에만** 보낸다. 누른 시점에 보내면 400(버전 불일치)으로
                    //    되돌아온 사람까지 가입으로 세어, 같은 사람이 두 번 가입한 것이 된다.
                    if (firstTime) attribution.signUp()
                    onAgreed(user)
                }
                .onFailure { throwable ->
                    Log.w(TAG, "동의 저장 실패", throwable)
                    val failure = throwable as? ApiFailure
                    if (failure?.httpStatus == HTTP_BAD_REQUEST) {
                        // 저장하는 사이 서버가 약관을 개정했다. 오류로 끝내지 않고
                        // 새 버전으로 화면을 다시 그린다(spec §동의 온보딩). 안내 문구는
                        // load() 가 재조회를 마친 뒤에도 남아 있어야 해서 인자로 넘긴다 —
                        // 여기서 따로 _state.update 하면 재조회 완료 시 그대로 덮여 사라진다.
                        load(pendingMessage = VERSION_CHANGED)
                    } else {
                        _state.update { it.copy(submitting = false, message = throwable.toMessage()) }
                    }
                }
        }
    }

    /** 동의를 거부하고 로그인 화면으로 돌아간다. 세션을 끊는 것이므로 로그아웃이다(spec §동의 온보딩). */
    fun cancel() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun Throwable.toMessage(): String {
        val failure = this as? ApiFailure
        return if (failure?.isRetryable == true) RETRYABLE else GENERIC
    }

    private companion object {
        const val TAG = "NM412"
        const val HTTP_BAD_REQUEST = 400
        const val UPDATE_REQUIRED = "앱을 최신 버전으로 업데이트해 주세요"

        // 사용자에게 보이는 말은 「개정」이 아니라 「변경」으로 통일한다(spec §동의 온보딩 정본).
        const val VERSION_CHANGED = "약관이 변경되어 다시 불러왔어요. 확인 후 동의해 주세요."
        const val RETRYABLE = "잠시 후 다시 시도해 주세요"
        const val GENERIC = "약관을 불러오지 못했어요"
    }
}

/**
 * 화면에 보일 항목만 남긴다 — **선택 항목에만 적용되는 규칙 둘**(NM-548).
 *
 * ① **모르는 선택 항목은 버린다.** 서버가 선택 항목을 하나 더 늘려도 출시된 앱이 막히지
 *    않아야 한다(spec §선택 동의 「모르는 항목」). 이름도 뜻도 모르는 줄을 그려 놓고 동의를
 *    받을 수는 없고, 그렇다고 필수처럼 멈춰 세울 일도 아니다 — 모르면 없는 것으로 둔다.
 *    모르는 **필수** 항목은 반대로 멈춘다([ConsentUiState.blocked]).
 *
 * ② **현재 버전에 이미 응답한 선택 항목은 버린다.** 동의했든 거부했든 마찬가지다
 *    (spec §선택 동의 「거부한 회원에게 따로 다시 묻지 않는다」). 거부한 사람에게 약관이
 *    개정될 때마다 같은 것을 다시 들이밀면 그건 묻는 것이 아니라 조르는 것이다.
 *    `ConsentStatus.version` 이 null 이면 한 번도 묻지 않은 것이라 보인다.
 *
 * 끝으로 **필수를 앞에, 선택을 뒤에** 둔다(spec §항목 표시). 서버 순서에 기대지 않는다 —
 * `sortedBy` 는 안정적이라 같은 그룹 안에서는 받은 순서가 그대로 유지된다.
 *
 * 뷰모델 밖의 함수로 둔 것은 **코루틴 없이 시험할 수 있게** 하려고다. 여기 담긴 네 가지
 * 경계(모르는 선택 · 이미 동의 · 이미 거부 · 묻지 않음)가 서버 없이 확인되어야 한다.
 *
 * @param answered 회원이 어느 버전에 응답했는지 — `User.consents`
 */
internal fun List<ConsentDefinition>.visibleFor(answered: List<ConsentStatus>): List<ConsentDefinition> = this
    .filter { definition ->
        when {
            definition.required -> true
            definition.type == ConsentType.UNKNOWN -> false
            else -> answered.none { it.type == definition.type && it.version == definition.version }
        }
    }
    .sortedBy { !it.required }
