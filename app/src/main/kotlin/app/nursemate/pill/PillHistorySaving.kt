package app.nursemate.pill

import app.nursemate.core.data.pill.PillHistoryRejected
import app.nursemate.core.data.pill.PillHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 확정한 알약을 식별 기록으로 남긴다 — 식별 결과의 `완료`(spec §완료 · 공유 · NM-552).
 *
 * [PillCorrections] 처럼 플로우 상태를 함께 쓴다 — 저장 단계([PillUiState.save])가 식별 결과와 같은 흐름에 있어야
 * 화면이 한 상태만 본다.
 */
class PillHistorySaving internal constructor(
    private val state: MutableStateFlow<PillUiState>,
    private val repository: PillHistoryRepository,
    private val scope: CoroutineScope
) {

    /**
     * 저장 중이거나 이미 남겼으면 다시 보내지 않는다. 확정한 알약이 없으면 보내지 않는다 — 버튼도 꺼져 있다.
     * 실패는 [HistorySave.Failed] 로 남기고 화면이 모달로 알린다. 400 은 다시 보내도 같아서
     * [HistorySave.Failed.retryable] 이 false 다. 다시 시도는 같은 [PillUiState.historyId] 로 가므로 기록이 늘지 않는다.
     */
    fun save() {
        val current = state.value
        val historyId = current.historyId
        val pillCodes = current.finalPills().map { it.pillCode }
        if (current.save == HistorySave.Saving || current.save == HistorySave.Saved) return
        if (historyId == null || pillCodes.isEmpty()) return

        state.update { it.copy(save = HistorySave.Saving) }
        scope.launch {
            val result = repository.save(historyId, pillCodes)
            state.update {
                it.copy(
                    save = result.fold(
                        onSuccess = { HistorySave.Saved },
                        onFailure = { e -> HistorySave.Failed(retryable = e !is PillHistoryRejected) }
                    )
                )
            }
        }
    }

    /** 저장 실패 모달을 닫는다 — 화면에 남아 다시 `완료` 를 누를 수 있다. */
    fun dismissFailure() {
        state.update { if (it.save is HistorySave.Failed) it.copy(save = HistorySave.Idle) else it }
    }
}
