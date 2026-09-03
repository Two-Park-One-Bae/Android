package app.nursemate.core.network.api

import app.nursemate.core.model.Image
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.Usage
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 알약 식별 API.
 *
 * 이 경로들은 서버가 **App Check 도** 검증한다 — 헤더가 없거나 등록되지 않은 앱이면
 * 401 `APP_CHECK_FAILED` 이고, 재시도해도 소용없다.
 */
interface PillApi {

    /**
     * 남은 식별 횟수.
     *
     * 조회만 하고 카운트를 늘리지 않는다. **조회 실패가 식별 진입을 막지는 않는다** —
     * 최종 판정은 식별 요청의 429 다(spec §식별 횟수 제한).
     */
    @GET("api/v0/pill-attributes/usage")
    suspend fun usage(): Usage

    /**
     * 크롭 N개 → 색·모양·제형.
     *
     * **1회 식별 = 이 요청 1회다.** 사진에 알약이 몇 개든 상관없다. 검출 0개면 아예
     * 부르지 않는다 — 부르면 헛되이 한 번 차감된다.
     *
     * 각인계열(front·back)은 MVP 에서 서버가 뽑지 않아 항상 null 로 온다.
     *
     * @throws app.nursemate.core.network.error.ApiFailure
     *   429 `DAILY_LIMIT_EXCEEDED` — 한도 도달. **차감되지 않으며** 본문의
     *   `ProblemDetail.usage` 에 `remaining=0` 과 리셋 시각이 담겨 온다.
     *   413 — 크롭이 너무 크다. 503 — 외부 AI 일시 오류(재시도 가능).
     */
    @POST("api/v0/pill-attributes")
    suspend fun attributes(@Body request: PillAttributesRequest): PillAttributesResponse

    /**
     * 원본 이미지 업로드용 presigned PUT URL.
     *
     * 학습데이터 축적용이고 **식별과 완전히 분리**돼 있다 — 키를 식별 요청에 넘기지 않고,
     * 실패해도 식별 플로우에 영향이 없다. 횟수도 차감되지 않는다(NM-348).
     */
    @POST("api/v0/pill-images/upload-url")
    suspend fun uploadUrl(): UploadUrlResponse
}

@Serializable
data class PillAttributesRequest(val items: List<PillAttributeItem>)

/** @param pillId 세션 안에서만 쓰는 로컬 키. 응답을 검출 결과와 짝지을 때 쓴다. */
@Serializable
data class PillAttributeItem(val pillId: String, val croppedImage: Image)

@Serializable
data class PillAttributesResponse(
    val items: List<PillAttribute>,
    /** 이번 차감이 반영된 사용량. 화면의 남은 횟수는 이 값으로 갱신한다. */
    val usage: Usage
)

@Serializable
data class UploadUrlResponse(val uploadUrl: String, val expiresAt: String)
