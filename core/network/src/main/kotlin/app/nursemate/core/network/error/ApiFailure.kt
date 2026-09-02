package app.nursemate.core.network.error

import java.io.IOException

/**
 * 서버가 2xx 가 아닌 응답을 준 경우.
 *
 * `IOException`을 상속한다 — Retrofit 은 인터셉터가 던진 `IOException`만 호출부까지 그대로 전달한다.
 * 다른 예외를 던지면 `UndeclaredThrowableException` 등으로 감싸여 원인이 가려진다.
 *
 * @param problem 파싱된 본문. 본문이 없거나 problem+json 이 아니면 null
 */
class ApiFailure(val httpStatus: Int, val problem: ProblemDetail?, val requestPath: String?) :
    IOException(
        "HTTP $httpStatus" +
            (problem?.rawCode?.let { " $it" } ?: "") +
            (requestPath?.let { " ($it)" } ?: "")
    ) {

    val code: ApiErrorCode get() = problem?.errorCode ?: ApiErrorCode.UNKNOWN

    /**
     * 잠시 후 재시도가 의미 있는가.
     *
     * 스펙 §규약: 목록에 없는 code 는 **HTTP status 로 폴백**한다 — 5xx 는 재시도, 4xx 는 입력 문제.
     * 알려진 코드는 errors.md 의 "클라이언트 대응" 열을 그대로 옮겼다.
     */
    val isRetryable: Boolean
        get() = when (code) {
            ApiErrorCode.SERVICE_UNAVAILABLE,
            ApiErrorCode.INTERNAL_ERROR,
            ApiErrorCode.CLASSIFICATION_FAILED -> true

            // 정상 앱에선 발생하지 않고, 발생해도 다시 보내봐야 같은 결과다.
            ApiErrorCode.APP_CHECK_FAILED -> false

            ApiErrorCode.UNKNOWN -> httpStatus >= HTTP_SERVER_ERROR

            else -> false
        }

    /**
     * 세션을 버리고 로그인 화면으로 보내야 하는가.
     *
     * ⚠️ **500·503 은 로그아웃 사유가 아니다** (spec §토큰·세션). 특히 진입의 `GET /users/me` 가
     * 500이면 공급자 불일치일 수 있는데, 서버 쪽 상태 문제라 재로그인해도 같은 응답이 온다 —
     * 로그인 화면으로 보내면 사용자가 빠져나올 방법이 없다.
     *
     * 401 이라도 **토큰 갱신 재시도를 이미 한 뒤**여야 참으로 다뤄야 한다. 그 재시도는
     * OkHttp `Authenticator` 가 담당하므로, 여기까지 온 401 은 갱신해도 안 된 것이다.
     */
    val requiresSignIn: Boolean
        get() = code == ApiErrorCode.UNAUTHORIZED ||
            (code == ApiErrorCode.UNKNOWN && httpStatus == HTTP_UNAUTHORIZED)

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_SERVER_ERROR = 500
    }
}
