package app.nursemate.core.network.api

import app.nursemate.core.model.Image
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidatePage
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillDetail
import app.nursemate.core.model.PillFaceRequest
import app.nursemate.core.model.PillFormulation
import app.nursemate.core.model.PillShape
import app.nursemate.core.model.Usage
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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
     * 수정한 속성으로 후보를 조회한다.
     *
     * **하드 필터 AND** 다 — 넣은 조건만 적용되고, 넣을수록 좁아진다. 조건이 좁으면
     * 후보 0개가 정상 응답이다(빈 목록). 속성을 고칠 때마다 다시 부른다.
     *
     * 정렬은 서버가 한다: 허가 정상 우선 → 색 정확 일치 우선 → pillCode 오름차순.
     * 앱이 다시 정렬하면 그 규칙이 어긋난다.
     *
     * 식별 횟수를 **차감하지 않는다**(Gemini 미사용).
     */
    @POST("api/v0/pill-candidates")
    suspend fun candidates(@Body request: PillCandidatesRequest): PillCandidatePage

    /**
     * 확정한 알약의 세부정보.
     *
     * ## 404 는 오류가 아니다
     * 미적재 품목·모르는 pillCode 모두 404 `PILL_DETAIL_NOT_FOUND` 하나로 온다. 화면은
     * 오류가 아니라 **'세부정보 없음'** 으로 그리고 재시도 버튼을 두지 않는다(NM-309).
     *
     * ## 허가 종료 품목은 아예 부르지 않는다
     * `licenseStatus = REVOKED` 는 조회 없이 안내로 끝낸다(NM-369). 허가정보가 남아 있는
     * 품목도 마찬가지다.
     *
     * ⚠️ 허가문서에 base64 인라인 이미지가 섞여 있어 응답이 **수 MB** 에 이를 수 있다.
     */
    @GET("api/v0/pill-details/{pillCode}")
    suspend fun pillDetail(@Path("pillCode") pillCode: String): PillDetail

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

/**
 * 후보 조회 요청.
 *
 * ⚠️ 면 필터([front]·[back])의 null 은 **"조건 제외"** 다 — 응답의 [app.nursemate.core.model.PillFace]
 * 와 뜻이 반대라는 걸 [PillFaceRequest] 주석에 적어 뒀다.
 *
 * @param size 1~50. 범위를 벗어나면 서버가 400 `INVALID_PAGINATION` 을 준다(clamp 없음).
 */
@Serializable
data class PillCandidatesRequest(
    val colors: List<PillColor> = emptyList(),
    val isTransparent: Boolean? = null,
    val shape: PillShape? = null,
    val formulation: PillFormulation? = null,
    val front: PillFaceRequest? = null,
    val back: PillFaceRequest? = null,
    val cursor: String? = null,
    val size: Int = DEFAULT_PAGE_SIZE
)

private const val DEFAULT_PAGE_SIZE = 20

@Serializable
data class UploadUrlResponse(val uploadUrl: String, val expiresAt: String)
