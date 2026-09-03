package app.nursemate.core.network.api

import app.nursemate.core.model.Usage
import app.nursemate.core.network.error.ApiFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 알약 API 에러 본문의 RFC 9457 **확장 필드**를 읽는다.
 *
 * ## 왜 ProblemDetail 이 아니라 여기인가
 * `ProblemDetail` 에 타입 필드로 두면 서버가 모양을 조금만 바꿔도 **에러 본문 전체가
 * 파싱에 실패해 `code` 마저 잃는다**(그 파일 주석 참고). 확장은 필요한 쪽에서 따로 읽고,
 * 실패하면 그 값만 포기한다.
 *
 * 본문 파싱은 전송 계층의 일이라 데이터 레이어로 내리지 않는다 — 그러면 `Json` 의존이
 * 그쪽까지 번진다.
 */
@Singleton
class PillErrorReader @Inject constructor(private val json: Json) {

    /**
     * 429 `LIMIT_EXCEEDED` 본문에 실려 오는 사용량.
     *
     * @return 없거나 못 읽으면 null — 한도에 걸렸다는 사실 자체는 [ApiFailure.code] 로 안다.
     */
    fun limitUsage(failure: ApiFailure): Usage? = failure.rawBody?.let { body ->
        runCatching { json.decodeFromString<LimitExceededBody>(body).usage }.getOrNull()
    }

    @Serializable
    private data class LimitExceededBody(val usage: Usage? = null)
}
