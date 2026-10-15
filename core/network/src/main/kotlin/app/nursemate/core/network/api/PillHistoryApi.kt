package app.nursemate.core.network.api

import app.nursemate.core.model.PillHistory
import app.nursemate.core.model.PillHistoryPage
import app.nursemate.core.model.PillHistorySaveRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 식별 기록 (NM-552) — `spec/api/domains/history.md`.
 *
 * 모두 식별 횟수와 무관하다(429 없음). 기록 ID 는 **앱이 만든다**(UUID) — 식별을 시작할 때 하나 만들어
 * 그 식별이 끝날 때까지 쓴다. 서버는 대소문자를 가리지 않고 소문자로 돌려준다.
 */
interface PillHistoryApi {

    /** 최신순 요약. [limit] 기본 20 · 최대 50, [cursor] 는 앞 응답의 `nextCursor`. */
    @GET("api/v0/pill-histories")
    suspend fun list(@Query("limit") limit: Int, @Query("cursor") cursor: String?): PillHistoryPage

    /**
     * 한 건 전체.
     *
     * @throws app.nursemate.core.network.error.ApiFailure 404 `PILL_HISTORY_NOT_FOUND` — 다른 기기에서
     *   지웠거나 100건 상한으로 지워졌다. 화면은 들어온 곳으로 돌아가 목록을 다시 불러온다.
     */
    @GET("api/v0/pill-histories/{historyId}")
    suspend fun get(@Path("historyId") historyId: String): PillHistory

    /**
     * 저장 — 같은 [historyId] 로 다시 보내면 **통째로 바꾼다.** 다시 시도해도 기록이 늘지 않는다.
     *
     * @throws app.nursemate.core.network.error.ApiFailure 400 — 카탈로그에 없는 품목 등.
     *   **다시 시도하지 않는다** — 같은 요청은 같은 결과다.
     */
    @PUT("api/v0/pill-histories/{historyId}")
    suspend fun save(@Path("historyId") historyId: String, @Body request: PillHistorySaveRequest)

    /** 삭제 — 이미 없어도 204 다. */
    @DELETE("api/v0/pill-histories/{historyId}")
    suspend fun delete(@Path("historyId") historyId: String)
}
