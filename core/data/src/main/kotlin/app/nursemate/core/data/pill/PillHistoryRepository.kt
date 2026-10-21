package app.nursemate.core.data.pill

import app.nursemate.core.model.PillHistory
import app.nursemate.core.model.PillHistoryPage
import app.nursemate.core.model.PillHistorySaveRequest
import app.nursemate.core.model.PillHistorySummary
import app.nursemate.core.network.api.PillHistoryApi
import app.nursemate.core.network.error.ApiErrorCode
import app.nursemate.core.network.error.ApiFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 기록이 없다 — 다른 기기에서 지웠거나 100건 상한으로 지워졌다(404 `PILL_HISTORY_NOT_FOUND`). */
class PillHistoryNotFound : Exception("식별 기록 없음")

/** 서버가 저장을 거절했다(400). **다시 시도하지 않는다** — 같은 요청은 같은 결과다. */
class PillHistoryRejected(cause: Throwable) : Exception("식별 기록 저장 거절", cause)

/**
 * 식별 기록 (NM-552) — `spec/api/domains/history.md`.
 *
 * ## [version] 이 왜 있나
 * 알약 탭 목록 · 기록 상세 · 홈 최근 식별 카드가 같은 기록을 따로 읽는다. 저장하거나 지우면 다른 화면은
 * 옛 목록을 들고 있으므로, 바뀔 때마다 숫자를 올려 「다시 불러오라」고 알린다. 숫자 자체에 뜻은 없다.
 */
@Singleton
class PillHistoryRepository @Inject constructor(private val api: PillHistoryApi) {

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** 최신순 한 묶음. [cursor] 는 앞 묶음의 `nextCursor` — 처음이면 null. */
    suspend fun page(cursor: String? = null): Result<PillHistoryPage> = runCatching { api.list(PAGE_SIZE, cursor) }

    /** 가장 최근 한 건 — 홈 최근 식별 카드. 기록이 없으면 null. */
    suspend fun latest(): Result<PillHistorySummary?> = runCatching { api.list(1, null).items.firstOrNull() }

    /** 한 건 전체. 없으면 [PillHistoryNotFound] 로 실패한다. */
    suspend fun get(historyId: String): Result<PillHistory> = runCatching { api.get(historyId) }
        .recoverCatching { e -> throw if (e.isNotFound()) PillHistoryNotFound() else e }

    /**
     * 확정한 품목을 화면 순서로 저장한다. 같은 [historyId] 로 다시 보내면 통째로 바뀌어 기록이 늘지 않는다.
     * 서버가 400 으로 거절하면 [PillHistoryRejected] 로 실패한다 — 다시 시도하지 않는다.
     */
    suspend fun save(historyId: String, pillCodes: List<String>): Result<Unit> =
        runCatching { api.save(historyId, PillHistorySaveRequest(pillCodes)) }
            .recoverCatching { e ->
                throw if (e is ApiFailure &&
                    e.httpStatus == HTTP_BAD_REQUEST
                ) {
                    PillHistoryRejected(e)
                } else {
                    e
                }
            }
            .onSuccess { _version.update { it + 1 } }

    /** 삭제 — 이미 없어도 성공이다. */
    suspend fun delete(historyId: String): Result<Unit> = runCatching { api.delete(historyId) }
        .onSuccess { _version.update { it + 1 } }

    /** 다른 화면이 [PillHistoryNotFound] 를 만났다 — 목록에서도 빠졌을 테니 다시 불러오게 한다. */
    fun invalidate() = _version.update { it + 1 }

    private fun Throwable.isNotFound() = this is ApiFailure &&
        (code == ApiErrorCode.PILL_HISTORY_NOT_FOUND || httpStatus == HTTP_NOT_FOUND)

    private companion object {
        /** 한 번에 받는 기록 수 — 서버 기본값과 같다. */
        const val PAGE_SIZE = 20
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
    }
}
