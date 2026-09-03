package app.nursemate.consent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.auth.AuthRepository
import app.nursemate.core.data.auth.ConsentRepository
import app.nursemate.core.model.ConsentDefinition
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
 * @param definitions 서버가 준 항목. 비어 있으면 아직 로딩 중이거나 못 받은 것이다.
 * @param checked 체크된 항목. 화면 상태일 뿐이고, 저장은 [definitions] 를 그대로 보낸다.
 * @param blocked 앱이 모르는 필수 항목이 내려왔다. 보내 봐야 400 이라 진행할 수 없다.
 */
data class ConsentUiState(
    val definitions: List<ConsentDefinition> = emptyList(),
    val checked: Set<ConsentType> = emptySet(),
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val blocked: Boolean = false,
    val message: String? = null
) {
    /** 필수 항목이 **전부** 체크됐는가. 하나라도 빠지면 '동의하고 계속'이 눌리지 않는다. */
    val canSubmit: Boolean
        get() = !loading && !submitting && !blocked &&
            definitions.isNotEmpty() &&
            definitions.filter { it.required }.all { it.type in checked }
}

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val consentRepository: ConsentRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConsentUiState())
    val state = _state.asStateFlow()

    init {
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
                            definitions = definitions,
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

    /** 전체 동의 — 하나라도 빠져 있으면 모두 켜고, 이미 전부 켜져 있으면 모두 끈다. */
    fun toggleAll() = _state.update { current ->
        val all = current.definitions.map { it.type }.toSet()
        val checked = if (current.checked.containsAll(all)) emptySet() else all
        current.copy(checked = checked, message = null)
    }

    /**
     * @param onAgreed 갱신된 회원. 진입 상태를 홈으로 넘기는 건 셸이 한다 —
     *                 `onboardingRequired` 를 여기서 단정하지 않고 응답 값을 그대로 올린다.
     */
    fun submit(onAgreed: (User) -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, message = null) }

        viewModelScope.launch {
            consentRepository.agreeToRequired(current.definitions)
                .onSuccess(onAgreed)
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
        const val VERSION_CHANGED = "약관이 개정되어 다시 불러왔어요. 확인 후 동의해 주세요"
        const val RETRYABLE = "잠시 후 다시 시도해 주세요"
        const val GENERIC = "약관을 불러오지 못했어요"
    }
}
