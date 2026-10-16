package app.nursemate.pill

import app.nursemate.core.data.pill.UsageHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 미리보기의 한도 안내 — `이 사진 사용` 을 누른 순간 오늘 횟수가 0 이면 요청 없이 그 화면 위에 알린다
 * (spec §세션 중 소진 방어 · 정본 ②-a). 서버가 429 로 막은 경우도 같은 상태([AttributePhase.LimitReached])로 온다.
 *
 * [PillCorrections] 처럼 플로우 상태를 함께 쓴다.
 */
class PillLimitGate internal constructor(
    private val state: MutableStateFlow<PillUiState>,
    private val usageHolder: UsageHolder
) {
    /** 한도에 걸렸다고 확인된 경우에만 true — 모르면 막지 않는다. */
    val blocked: Boolean get() = usageHolder.blocked

    /** 요청 없이 안내를 띄운다. */
    fun show() {
        state.update { it.copy(attributes = AttributePhase.LimitReached) }
    }

    /** 안내를 닫는다 — 미리보기에 머문다. 사진은 그대로라 한도가 풀리면 바로 다시 할 수 있다. */
    fun dismiss() {
        state.update {
            if (it.attributes ==
                AttributePhase.LimitReached
            ) {
                it.copy(attributes = AttributePhase.Idle)
            } else {
                it
            }
        }
    }
}
