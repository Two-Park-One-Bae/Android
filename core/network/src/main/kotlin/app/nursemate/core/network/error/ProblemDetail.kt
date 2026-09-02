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
