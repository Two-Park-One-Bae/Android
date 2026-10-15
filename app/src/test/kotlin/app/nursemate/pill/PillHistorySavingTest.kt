package app.nursemate.pill

import app.nursemate.core.data.pill.PillHistoryRepository
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.model.PillHistory
import app.nursemate.core.model.PillHistoryPage
import app.nursemate.core.model.PillHistorySaveRequest
import app.nursemate.core.network.api.PillHistoryApi
import app.nursemate.core.network.error.ApiFailure
import app.nursemate.core.network.error.ProblemDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** 식별 결과 `완료` 의 기록 저장 — spec §완료 · 공유 · §식별 기록 — 저장. */
class PillHistorySavingTest {

    private class FakeApi : PillHistoryApi {
        var fail: Throwable? = null
        val saved = mutableListOf<Pair<String, List<String>>>()

        override suspend fun list(limit: Int, cursor: String?) = PillHistoryPage(emptyList(), null)
        override suspend fun get(historyId: String) = PillHistory(historyId, "", emptyList())
        override suspend fun save(historyId: String, request: PillHistorySaveRequest) {
            fail?.let { throw it }
            saved += historyId to request.pillCodes
        }
        override suspend fun delete(historyId: String) = Unit
    }

    private fun candidate(code: String) = PillCandidate(code, LicenseStatus.NORMAL, "약$code", "업체", "", "")

    /** 셋 중 둘(m1 · m3)을 확정한 결과. */
    private val confirmed = PillUiState(
        historyId = "h1",
        manualPillIds = listOf("m1", "m2", "m3"),
        selections = mapOf("m3" to candidate("C"), "m1" to candidate("A"))
    )

    private val api = FakeApi()
    private val state = MutableStateFlow(confirmed)

    /** 실행기를 Unconfined 로 두어 `save()` 가 돌아오면 응답까지 끝나 있게 한다. */
    private val saving = PillHistorySaving(state, PillHistoryRepository(api), CoroutineScope(Dispatchers.Unconfined))

    private fun failure(status: Int) = ApiFailure(status, ProblemDetail(status = status, rawCode = "X"), "/h")

    @Test
    fun `확정한 알약만 보이는 순서대로 보낸다`() {
        saving.save()
        assertEquals(listOf("h1" to listOf("A", "C")), api.saved)
        assertEquals(HistorySave.Saved, state.value.save)
    }

    @Test
    fun `남긴 뒤에는 다시 보내지 않는다`() {
        saving.save()
        saving.save()
        assertEquals(1, api.saved.size)
    }

    @Test
    fun `하나도 확정하지 않았으면 보내지 않는다`() {
        state.value = confirmed.copy(selections = emptyMap())
        saving.save()
        assertEquals(emptyList<Pair<String, List<String>>>(), api.saved)
        assertEquals(HistorySave.Idle, state.value.save)
    }

    @Test
    fun `통신 실패는 다시 시도할 수 있고 같은 키로 다시 보낸다`() {
        api.fail = failure(503)
        saving.save()
        assertEquals(HistorySave.Failed(retryable = true), state.value.save)

        api.fail = null
        saving.save()
        assertEquals(listOf("h1" to listOf("A", "C")), api.saved)
        assertEquals(HistorySave.Saved, state.value.save)
    }

    @Test
    fun `400 은 다시 시도를 두지 않는다`() {
        api.fail = failure(400)
        saving.save()
        assertEquals(HistorySave.Failed(retryable = false), state.value.save)
    }

    @Test
    fun `실패 모달을 닫으면 처음 상태로 돌아간다`() {
        api.fail = failure(503)
        saving.save()
        saving.dismissFailure()
        assertEquals(HistorySave.Idle, state.value.save)
    }
}
