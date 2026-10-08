package app.nursemate.consent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * 「약관 및 동의」 화면 상태 — 선택 동의 철회·재동의(NM-548).
 *
 * @param definitions 서버가 준 항목에서 **모르는 선택 항목만** 걷어낸 것. 동의 온보딩과 달리
 *   이미 응답한 선택 항목도 보인다 — 여기가 바로 그 응답을 바꾸러 오는 자리다.
 * @param checked 지금 화면의 체크. 필수는 항상 들어 있고 끌 수 없다.
 * @param baseline 화면에 들어왔을 때의 체크. [dirty] 가 이것과 비교한다.
 */
data class ConsentSettingsUiState(
    val definitions: List<ConsentDefinition> = emptyList(),
    val checked: Set<ConsentType> = emptySet(),
    val baseline: Set<ConsentType> = emptySet(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val message: String? = null
) {
    /**
     * 처음과 달라졌는가 — `저장`이 활성화되는 유일한 조건(spec §약관 및 동의).
     *
     * 달라진 것이 없는데 저장이 눌리면 아무 일도 일어나지 않는 요청이 나가고, 사용자는
     * 무언가 저장됐다고 믿는다.
     */
    val dirty: Boolean get() = checked != baseline

    val canSave: Boolean get() = !loading && !saving && dirty
}

/**
 * 약관 전문을 확인하고 **선택 항목의 동의 여부를 바꾸는** 화면.
 *
 * ## 동의 온보딩과 공유하지 않는다
 * 같은 API 를 쓰고 같은 줄을 그리지만 규칙이 정반대인 곳이 셋이다 — 필수 항목을 (ⓐ 온보딩은
 * 체크받고 / ⓑ 여기선 잠그고), 선택 항목을 (ⓐ 이미 응답했으면 숨기고 / ⓑ 여기선 늘 보이고),
 * 저장을 (ⓐ 보인 것 전부 / ⓑ 바뀐 것만) 한다. 한 뷰모델에 담으면 분기가 세 겹으로 엉킨다.
 */
@HiltViewModel
class ConsentSettingsViewModel @Inject constructor(private val consentRepository: ConsentRepository) : ViewModel() {

    private val _state = MutableStateFlow(ConsentSettingsUiState())
    val state = _state.asStateFlow()

    private var answered: List<ConsentStatus> = emptyList()
    private var started = false

    /**
     * 셸이 회원의 동의 기록을 넣고 첫 조회를 시작한다 — 화면이 뜰 때 **한 번**.
     *
     * 재구성마다 다시 로드하면 바꿔 둔 체크가 풀린다. 저장에 성공하면 그 응답으로
     * [answered] 를 갱신하므로, 여기서 다시 받을 일은 없다.
     */
    fun start(consents: List<ConsentStatus>) {
        if (started) return
        started = true
        answered = consents
        load()
    }

    /** @param pendingMessage 재조회 뒤에도 남겨 둘 안내 — 저장 중 약관이 개정된 경우([save]). */
    fun load(pendingMessage: String? = null) {
        _state.update { ConsentSettingsUiState(loading = true, message = pendingMessage) }
        viewModelScope.launch {
            consentRepository.definitions()
                .onSuccess { definitions ->
                    val visible = definitions.forSettings()
                    val checked = visible.agreedTypes(answered)
                    _state.update {
                        ConsentSettingsUiState(
                            definitions = visible,
                            checked = checked,
                            baseline = checked,
                            loading = false,
                            message = pendingMessage
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.w(TAG, "동의 항목 조회 실패", throwable)
                    _state.update {
                        ConsentSettingsUiState(loading = false, message = throwable.toMessage(GENERIC))
                    }
                }
        }
    }

    /** 필수 항목은 호출부가 거르지만(행이 아예 눌리지 않는다), 여기서도 한 번 더 막는다. */
    fun toggle(type: ConsentType) = _state.update { current ->
        if (current.definitions.any { it.type == type && it.required }) return@update current
        val checked = if (type in current.checked) current.checked - type else current.checked + type
        current.copy(checked = checked, message = null)
    }

    /**
     * 바뀐 항목만 저장한다.
     *
     * ⚠️ **필수 항목은 싣지 않는다.** 서버는 보낸 항목만 갱신하므로 빼면 그대로 남는다
     * (spec §선택 동의). 필수를 함께 보내면 개정 직후 이 화면에서 「저장」을 누른 것이
     * **옛 버전으로의 재동의**가 되거나 버전 불일치 400 을 맞는다 — 둘 다 여기서 할 일이 아니다.
     *
     * 확인 다이얼로그를 띄우지 않는다. 철회가 동의보다 번거로우면 안 된다
     * (개인정보 보호법 제38조 제4항).
     *
     * @param onSaved 갱신된 회원 — 셸이 받아 측정 SDK 를 켜고 끈다(NM-543).
     */
    fun save(onSaved: (User) -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        val changed = current.definitions.filter {
            !it.required &&
                (it.type in current.checked) != (it.type in current.baseline)
        }
        if (changed.isEmpty()) return

        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            consentRepository.agree(changed, current.checked)
                .onSuccess { user ->
                    answered = user.consents
                    // 저장된 값이 곧 새 기준선이다 — 「저장」이 다시 비활성으로 돌아가는 것이
                    // 이 화면의 유일한 완료 신호다(정본에 성공 토스트가 없다).
                    _state.update { it.copy(saving = false, baseline = it.checked) }
                    onSaved(user)
                }
                .onFailure { throwable ->
                    Log.w(TAG, "동의 저장 실패", throwable)
                    val failure = throwable as? ApiFailure
                    if (failure?.httpStatus == HTTP_BAD_REQUEST) {
                        // 저장하는 사이 약관이 개정됐다. 새 버전으로 화면을 다시 그린다.
                        load(pendingMessage = VERSION_CHANGED)
                    } else {
                        // 체크를 저장 전으로 되돌린다(spec §약관 및 동의). 실패했는데 바뀐 채로
                        // 두면 화면이 서버에 없는 상태를 보여 주게 된다.
                        _state.update {
                            it.copy(saving = false, checked = it.baseline, message = throwable.toMessage(SAVE_FAILED))
                        }
                    }
                }
        }
    }

    private fun Throwable.toMessage(fallback: String): String {
        val failure = this as? ApiFailure
        return if (failure?.isRetryable == true) RETRYABLE else fallback
    }

    private companion object {
        const val TAG = "NM548"
        const val HTTP_BAD_REQUEST = 400

        // 동의 온보딩과 같은 문구다(spec §약관 및 동의 「안내 문구는 동의 온보딩과 같다」).
        const val VERSION_CHANGED = "약관이 변경되어 다시 불러왔어요. 확인 후 동의해 주세요."
        const val RETRYABLE = "잠시 후 다시 시도해 주세요"
        const val GENERIC = "약관을 불러오지 못했어요"
        const val SAVE_FAILED = "잠시 후 다시 시도해 주세요"
    }
}

/**
 * 「약관 및 동의」가 보일 항목 — **모르는 선택 항목만** 버리고 필수를 앞에 둔다.
 *
 * 동의 온보딩의 [visibleFor] 와 다르다. 여기서는 **이미 응답한 선택 항목도 보인다** —
 * 숨기면 철회할 자리가 사라진다. 모르는 **필수** 항목은 남긴다: 바꿀 수 없는 줄이라
 * 잠긴 채 보이기만 하고, 저장에는 애초에 싣지 않는다.
 */
internal fun List<ConsentDefinition>.forSettings(): List<ConsentDefinition> = this
    .filter { it.required || it.type != ConsentType.UNKNOWN }
    .sortedBy { !it.required }

/**
 * 화면이 체크된 채로 시작할 항목.
 *
 * 필수는 **늘** 체크된다(바꿀 수 없다). 선택은 `agreed && satisfied` 일 때만 —
 * 옛 버전에 동의한 상태(`agreed && !satisfied`)와 응답한 적 없는 상태는 **체크되지 않은
 * 것으로 보인다**(spec §약관 및 동의). 고지사항이 바뀌었는데 옛 동의를 지금 동의로
 * 보여 주면, 다시 동의할 기회를 뺏는 셈이다.
 */
internal fun List<ConsentDefinition>.agreedTypes(answered: List<ConsentStatus>): Set<ConsentType> =
    filter { definition ->
        definition.required ||
            answered.any { it.type == definition.type && it.agreed && it.satisfied }
    }.map { it.type }.toSet()
