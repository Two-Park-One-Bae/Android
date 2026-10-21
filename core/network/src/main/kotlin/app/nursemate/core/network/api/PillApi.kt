package app.nursemate.core.network.api

import app.nursemate.core.model.Image
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidateItems
import app.nursemate.core.model.PillCandidateResult
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillDetail
import app.nursemate.core.model.PillFaceRequest
import app.nursemate.core.model.PillFormulation
import app.nursemate.core.model.PillShape
import app.nursemate.core.model.Usage
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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
     * **각인·마크는 이 응답에 없다** — 앱이 온디바이스로 읽는다(NM-485 · NM-515).
     * 서버는 색(openCV)·모양·제형(자체 분류 모델)만 뽑는다. 외부 AI 호출이 없어 v0 보다 빠르다.
     *
     * 응답에는 `attributeToken` 이 함께 온다. **해석하지 않고** 후보 조회에 그대로 되돌려주면
     * 서버가 정렬에 쓴다 — 한 식별 흐름 안에서만 유효하다.
     *
     * @throws app.nursemate.core.network.error.ApiFailure
     *   429 `LIMIT_EXCEEDED` — 한도 도달. **차감되지 않으며** 본문의
     *   `ProblemDetail.usage` 에 `remaining=0` 과 리셋 시각이 담겨 온다.
     *   413 — 크롭이 너무 크다. 503 — 외부 AI 일시 오류(재시도 가능).
     */
    @POST("api/v1/pill-attributes")
    suspend fun attributes(@Body request: PillAttributesRequest): PillAttributesResponse

    /**
     * 사용자가 정한 조건으로 후보를 조회한다 (NM-517).
     *
     * ## 자르는 것과 줄 세우는 것이 다르다
     * | 하드 필터 (자른다) | 소프트 (줄만 세운다) |
     * |---|---|
     * | 각인 · 마크 유무 · 구분선 | `attributeToken` (모델 출력 · 로지스틱) |
     * | 사용자가 **직접 고른** 모양 · 제형 | 마크 임베딩 · 사용자가 고른 **색** |
     *
     * ⚠️ **모델이 추정한 모양·제형·색을 조건으로 보내지 않는다.** 보내면 하드 필터가 되어
     * 정답 약을 떨어뜨린다 — 모델값은 `attributeToken` 하나로 정렬에만 쓰인다
     * ([app.nursemate.core.model.PillConditions]).
     *
     * ## 페이지네이션이 없다
     * 커서 대신 **정렬된 `ids` 전체(최대 200)와 앞 20개 상세**가 한 번에 온다. 21번째부터는
     * [candidateItems] 로 ID 조회한다(NM-489) — 순서는 이 응답의 `ids` 가 고정한다.
     *
     * 식별 횟수를 **차감하지 않는다.**
     *
     * @throws app.nursemate.core.network.error.ApiFailure
     *   400 `INVALID_ATTRIBUTE_TOKEN` — 토큰이 오래됐거나 지원하지 않는 버전이다.
     *   **앱은 토큰 없이 다시 요청한다** — 후보는 나오고 정렬만 덜 맞는다. 화면에 따로 알리지 않는다
     */
    @POST("api/v1/pill-candidates")
    suspend fun candidates(@Body request: PillCandidatesRequest): PillCandidateResult

    /**
     * 후보 카드 일괄 조회 — 21번째부터의 상세 (NM-489).
     *
     * [candidates] 가 준 `ids` 중 아직 받지 않은 것을 묶어 부른다.
     *
     * ⚠️ **`GET` 에 쿼리 파라미터**다 — 본문이 아니라 `pillCodes=코드1,코드2` 로 쉼표 구분해
     * 보낸다. POST 로 보내면 405 다(실기기에서 확인). 조회 전용이라 식별 횟수와 무관하다.
     *
     * **1~50개**만 받는다 — 밖이면 400 `INVALID_REQUEST` 이고 잘라서 돌려주지 않는다.
     * 중복은 한 번만 담는다.
     *
     * ⚠️ **순서가 보장되지 않는다.** 앱이 `ids` 순서대로 다시 배치한다.
     * 그리고 `missing` 에 담겨 오는 pillCode 는 **목록에서 뺀다** — 로딩 중으로 남기지 않는다.
     */
    @GET("api/v1/pill-candidates/items")
    suspend fun candidateItems(@Query("pillCodes") pillCodes: String): PillCandidateItems

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
     * 학습데이터 축적용이다. 앱이 만든 `imageId`(UUID v7)를 본문에 보내면 서버가 그것을 원본 키로 쓴다 — 같은 값을
     * 속성 추출에도 보내 학습 라벨이 이 사진을 가리킨다(NM-571). 둘의 순서는 상관없고, 실패해도 식별 플로우에 영향이
     * 없다. 횟수도 차감되지 않는다(NM-348).
     */
    @POST("api/v0/pill-images/upload-url")
    suspend fun uploadUrl(@Body request: UploadUrlRequest): UploadUrlResponse

    /**
     * 학습 라벨 — 사용자가 후보를 「선택」한 알약 하나(NM-571, spec `api/domains/label.md`).
     *
     * 베스트 에포트다. 실패해도 식별 흐름을 막지 않고, 화면에 알리지 않고, 다시 보내지 않는다. 같은 알약을 다시 고르면
     * 다시 보내고 서버가 덮어쓴다. 회원과 잇지 않는 익명 데이터이고 식별 횟수를 쓰지 않는다(429 없음).
     *
     * @param pillIndex 모델이 찾은 알약은 속성 추출의 `pillId`(0~99), 수동 추가 알약은 100~199
     */
    @PUT("api/v0/pill-images/{imageId}/labels/{pillIndex}")
    suspend fun label(
        @Path("imageId") imageId: String,
        @Path("pillIndex") pillIndex: Int,
        @Body request: PillLabelRequest
    )

    /**
     * 학습 라벨 — 「완료」(또는 종료 확인의 「종료」) 때 사용자가 카드를 지운 알약 번호(NM-571). 저장하지 않고 나가면
     * 보내지 않는다. 위치 행이 없으면(속성 추출 저장 실패) 404 인데 무시한다. 베스트 에포트.
     */
    @PATCH("api/v0/pill-images/{imageId}/segmentation")
    suspend fun segmentation(@Path("imageId") imageId: String, @Body request: PillSegmentationSaveRequest)
}

/**
 * 라벨 필드([imageId] · [imageWidth] · [imageHeight] · [segModel])는 선택이다 — [imageId] 가 있어야 서버가 크롭 · 라벨 행 ·
 * 위치 행을 저장한다(NM-571). 잘못돼도 서버는 400 없이 저장만 건너뛴다.
 *
 * @param imageWidth · imageHeight 업로드한 원본(가공본) 픽셀 크기
 * @param segModel 알약 찾기 모델 버전(`PillDetector.MODEL_VERSION`)
 */
@Serializable
data class PillAttributesRequest(
    val items: List<PillAttributeItem>,
    val imageId: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val segModel: String? = null
)

/**
 * @param pillId 응답을 검출 결과와 짝짓는 키. [PillAttributesRequest.imageId] 를 보낼 때는 **0~99 정수 문자열**이고
 *               그대로 라벨 번호(`pillIndex`)가 된다 — 알약 찾기 결과 번호다. 카드를 지워도 바꾸지 않는다
 * @param box 크롭이 원본 어디서 잘렸는지 `[x, y, 너비, 높이]` — 업로드한 원본 픽셀 기준. 너비 · 높이가 크롭 크기와 같아야 한다
 * @param score 알약 찾기 점수(0~1)
 */
@Serializable
data class PillAttributeItem(
    val pillId: String,
    val croppedImage: Image,
    val box: List<Int>? = null,
    val score: Float? = null
)

/** @param imageId 앱이 만든 사진 id(UUID v7) — 원본 키의 uuid 가 되고, 속성 추출에도 같은 값이 간다 */
@Serializable
data class UploadUrlRequest(val imageId: String)

/**
 * 「선택」 라벨. [model] · [userInput] 은 둘 다 후보 조회 요청 본문 그대로다 — 사용자가 하나도 고치지 않았으면 둘이 같다.
 *
 * @param manual 수동 추가 알약이면 true(이때 [model] 은 보내지 않는다 — 보내면 400). 아니면 빼서 보낸다(기본 false)
 * @param model 이 알약의 **첫** 후보 조회 요청
 * @param userInput 이 알약의 **마지막** 후보 조회 요청
 */
@Serializable
data class PillLabelRequest(
    val pillCode: String,
    val userInput: PillCandidatesRequest,
    val model: PillCandidatesRequest? = null,
    val manual: Boolean? = null
)

/** @param deletedIndexes 사용자가 카드를 지운 모델 알약 번호(0~99). 수동 추가 번호는 넣지 않는다. 없으면 빈 목록 */
@Serializable
data class PillSegmentationSaveRequest(val deletedIndexes: List<Int>)

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
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PillCandidatesRequest(
    /**
     * 속성 추출이 준 토큰을 **그대로** 되돌려준다. 사용자가 무엇을 고쳤든 항상 보낸다 —
     * 모델이 본 색·모양·제형이 여기 들어 있고 서버가 정렬에 쓴다.
     * 수동 추가·추출 실패 알약은 null 이다.
     */
    val attributeToken: String? = null,
    /**
     * [front]·[back] 의 `markEmbedding` 을 뽑은 마크 모델 버전 — ML 레포 모델 폴더 이름이다.
     * 어느 면이든 임베딩이 있으면 **필수**다(없으면 400 `INVALID_REQUEST`).
     *
     * 서버가 가진 카탈로그 버전에 없으면 **임베딩 항만 빼고** 나머지 가중치는 그대로다 —
     * 에러가 아니다. 버전이 다른 임베딩끼리는 코사인이 성립하지 않는데, 그걸 에러로 막으면
     * 모델을 바꿀 때마다 옛 앱의 조회가 통째로 죽는다.
     */
    val markEmbeddingModel: String? = null,
    // ⚠️ 이 프로젝트의 Json 은 explicitNulls=false 뿐 encodeDefaults 는 기본값(false) 이다 —
    // 기본값과 같은 값은 직렬화에서 통째로 빠진다. 계약이 colors 에 `default: []` 를 두고 있어
    // 빠져도 서버가 받아 주지만, 「색 조건 없음」을 명시적으로 보내는 편이 읽기 쉽다.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val colors: List<PillColor> = emptyList(),
    val shape: PillShape? = null,
    val formulation: PillFormulation? = null,
    val front: PillFaceRequest? = null,
    val back: PillFaceRequest? = null
)

@Serializable
data class UploadUrlResponse(val uploadUrl: String, val expiresAt: String)
