package app.nursemate.core.data.pill

import app.nursemate.core.model.PillHistory
import app.nursemate.core.model.PillHistoryPage
import app.nursemate.core.model.PillHistorySaveRequest
import app.nursemate.core.model.PillHistorySummary
import app.nursemate.core.network.api.PillHistoryApi
import app.nursemate.core.network.error.ApiFailure
import app.nursemate.core.network.error.ProblemDetail
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** spec api/domains/history.md · errors.md §식별 기록 의 오류 대응. */
class PillHistoryRepositoryTest {

    private class FakeApi : PillHistoryApi {
        var failGet: Throwable? = null
        var failSave: Throwable? = null
        var page = PillHistoryPage(emptyList(), null)
        val saved = mutableListOf<Pair<String, List<String>>>()
        var lastLimit = 0

        override suspend fun list(limit: Int, cursor: String?): PillHistoryPage = page.also { lastLimit = limit }
        override suspend fun get(historyId: String): PillHistory =
            failGet?.let { throw it } ?: PillHistory(historyId, "", emptyList())
        override suspend fun save(historyId: String, request: PillHistorySaveRequest) {
            failSave?.let { throw it }
            saved += historyId to request.pillCodes
        }
        override suspend fun delete(historyId: String) = Unit
    }

    private fun failure(status: Int, code: String?) =
        ApiFailure(status, code?.let { ProblemDetail(status = status, rawCode = it) }, "/api/v0/pill-histories")

    @Test
    fun `404 는 기록 없음으로 바꾼다`() = runBlocking {
        val api = FakeApi().apply { failGet = failure(404, "PILL_HISTORY_NOT_FOUND") }
        val result = PillHistoryRepository(api).get("h1")
        assertTrue(result.exceptionOrNull() is PillHistoryNotFound)
    }

    @Test
    fun `다른 실패는 그대로 둔다 — 다시 시도할 수 있는 실패다`() = runBlocking {
        val api = FakeApi().apply { failGet = failure(503, "SERVICE_UNAVAILABLE") }
        val result = PillHistoryRepository(api).get("h1")
        assertTrue(result.exceptionOrNull() is ApiFailure)
    }

    @Test
    fun `저장 400 은 거절로 바꾼다 — 다시 시도하지 않는다`() = runBlocking {
        val api = FakeApi().apply { failSave = failure(400, "INVALID_REQUEST") }
        val repository = PillHistoryRepository(api)
        val result = repository.save("h1", listOf("x"))
        assertTrue(result.exceptionOrNull() is PillHistoryRejected)
        assertEquals("실패하면 다른 화면에 알리지 않는다", 0, repository.version.value)
    }

    @Test
    fun `저장 · 삭제에 성공하면 다른 화면이 다시 불러오게 숫자를 올린다`() = runBlocking {
        val api = FakeApi()
        val repository = PillHistoryRepository(api)
        repository.save("h1", listOf("a", "a", "b"))
        repository.delete("h1")
        assertEquals(2, repository.version.value)
        assertEquals("같은 품목이 여러 번 · 화면 순서 그대로", listOf("h1" to listOf("a", "a", "b")), api.saved)
    }

    @Test
    fun `최근 한 건은 limit 1 로 받고 없으면 null`() = runBlocking {
        val api = FakeApi()
        val repository = PillHistoryRepository(api)
        assertNull(repository.latest().getOrThrow())
        assertEquals(1, api.lastLimit)

        api.page = PillHistoryPage(listOf(PillHistorySummary("h1", "2026-10-09T05:14:00Z", "게보린정", 2)), null)
        assertEquals("h1", repository.latest().getOrThrow()?.historyId)
    }
}
