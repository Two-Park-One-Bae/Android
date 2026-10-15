package app.nursemate.pill

import app.nursemate.core.data.pill.PillHistoryRejected
import app.nursemate.core.data.pill.PillHistoryRepository
import java.util.UUID
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
        val pillCodes = current.finalPills().map { it.pillCode }
        if (current.save == HistorySave.Saving || current.save == HistorySave.Saved || pillCodes.isEmpty()) return
        // 키는 식별 시작 때 정한다. 없으면(그 길을 거치지 않은 상태) 여기서 정해 둔다 — `완료` 가 말없이 멈추지 않게.
        val historyId = current.historyId ?: UUID.randomUUID().toString()

        state.update { it.copy(historyId = historyId, save = HistorySave.Saving) }
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
