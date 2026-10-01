package app.nursemate.core.network.error

/**
 * 서버가 `ProblemDetail.code`로 주는 애플리케이션 에러 코드.
 *
 * 정본은 `spec/api/domains/errors.md`. **클라이언트는 이 code로 분기하고 `detail` 문구로는 분기하지
 * 않는다** — detail은 계약이 아니라 언제든 바뀐다.
 *
 * `EXTRACTION_FAILED`는 여기 없다. 그건 HTTP 에러가 아니라 200 응답 본문 안
 * `PillAttribute.error` 필드에 실리는 값이라 축이 다르다.
 */
enum class ApiErrorCode {
    /** 400 — 본문 형식·필수 필드 누락·검증 위반 */
    INVALID_REQUEST,

    /** 400 — 페이지네이션 파라미터 오류 */
    INVALID_PAGINATION,

    /**
     * 400 — 속성 토큰을 해석할 수 없거나 지원하지 않는 버전이다 (NM-517).
     *
     * **사용자에게 알리지 않는다.** 앱이 토큰 없이 다시 조회하면 후보는 그대로 나오고
     * 정렬만 덜 맞는다(`PillRepository.candidates`).
     */
    INVALID_ATTRIBUTE_TOKEN,

    /** 401 — Bearer 누락·검증 실패. 재로그인 유도 */
    UNAUTHORIZED,

    /** 401 — App Check 실패. **정상 앱에선 발생하지 않고 재시도도 무의미하다** */
    APP_CHECK_FAILED,

    /** 401 — 카카오 액세스 토큰 검증 실패(앱 ID 불일치 포함) */
    KAKAO_TOKEN_INVALID,

    /** 404 — 알약 상세 없음 */
    PILL_DETAIL_NOT_FOUND,

    /** 413 — 업로드 이미지 초과 */
    IMAGE_SIZE_EXCEEDED,

    /** 429 — 일일 식별 한도 도달(미차감). 응답에 usage 동봉 */
    LIMIT_EXCEEDED,

    /** 500 — AI 분류 처리 실패(복구 불가) */
    CLASSIFICATION_FAILED,

    /** 500 — 예상치 못한 서버 오류 */
    INTERNAL_ERROR,

    /** 503 — 일시적 불가(복구 가능). 토큰 검증 불가도 여기로 온다 */
    SERVICE_UNAVAILABLE,

    /**
     * 목록에 없는 code.
     *
     * 서버가 코드를 새로 추가해도 앱이 깨지지 않게 두는 자리다. 이때는 **HTTP status로 폴백**한다
     * (errors.md §규약) — [ApiFailure.isRetryable] 참고.
     */
    UNKNOWN;

    companion object {
        private val BY_NAME = entries.associateBy { it.name }

        /** 서버 문자열 → 코드. 미지 값·null 은 [UNKNOWN]. */
        fun from(raw: String?): ApiErrorCode = raw?.let { BY_NAME[it] } ?: UNKNOWN
    }
}
