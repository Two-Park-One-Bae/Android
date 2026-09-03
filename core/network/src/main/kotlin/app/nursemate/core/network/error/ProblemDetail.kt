package app.nursemate.core.network.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RFC 9457 Problem Details — 모든 에러 응답의 본문(`application/problem+json`).
 *
 * 정본은 `spec/api/openapi.yaml`의 `ProblemDetail`. 서버 실제 응답 예:
 * ```json
 * {"type":"about:blank","title":"Unauthorized","status":401,
 *  "detail":"Authentication failed.","instance":"/api/v0/users/me","code":"UNAUTHORIZED"}
 * ```
 *
 * ⚠️ 스키마상 `type`·`title`·`status`·`detail`이 required 지만 **전부 기본값을 준다.**
 * 에러 응답을 파싱하다 예외가 나면 원래 실패 원인을 덮어써 버려, 무엇이 잘못됐는지 알 수 없게 된다.
 * 파싱은 최대한 관대하게 하고 판단은 [code]·[status]로 한다.
 *
 * ⚠️ **도메인 타입 필드를 여기 추가하지 말 것.** 429 응답에는 `usage` 확장이 실려 오는데
 * 그걸 타입 필드로 받으면, 서버가 그 모양을 조금만 바꿔도 **에러 본문 전체가 파싱에 실패해
 * `code` 마저 잃는다**. 도메인 확장은 [ApiFailure.rawBody] 를 도메인 레이어에서 읽는다.
 */
@Serializable
data class ProblemDetail(
    val type: String = "about:blank",
    val title: String = "",
    val status: Int = 0,
    val detail: String = "",
    val instance: String? = null,
    /** 애플리케이션 에러 코드. 문자열 원본을 그대로 둔다 — 해석은 [errorCode]. */
    @SerialName("code") val rawCode: String? = null
) {
    /** 미지 코드는 [ApiErrorCode.UNKNOWN]. 이때 판단 근거는 [status]다. */
    val errorCode: ApiErrorCode get() = ApiErrorCode.from(rawCode)
}
