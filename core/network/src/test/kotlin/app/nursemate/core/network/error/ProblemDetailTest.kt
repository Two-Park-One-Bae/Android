package app.nursemate.core.network.error

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemDetailTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** dev 서버가 실제로 돌려준 응답. 계약이 바뀌면 여기서 먼저 깨져야 한다. */
    @Test
    fun `서버 401 응답을 그대로 파싱한다`() {
        val body = """
            {"detail":"Authentication failed.","instance":"/api/v0/users/me",
             "status":401,"title":"Unauthorized","type":"about:blank","code":"UNAUTHORIZED"}
        """.trimIndent()

        val problem = json.decodeFromString<ProblemDetail>(body)

        assertEquals(401, problem.status)
        assertEquals("Unauthorized", problem.title)
        assertEquals("/api/v0/users/me", problem.instance)
        assertEquals(ApiErrorCode.UNAUTHORIZED, problem.errorCode)
    }

    @Test
    fun `모르는 code 는 UNKNOWN 이 된다`() {
        val problem = json.decodeFromString<ProblemDetail>(
            """{"status":418,"code":"TEAPOT_ON_FIRE"}"""
        )

        assertEquals(ApiErrorCode.UNKNOWN, problem.errorCode)
        // 원본은 버리지 않는다 — 로그·문의에 필요하다.
        assertEquals("TEAPOT_ON_FIRE", problem.rawCode)
    }

    @Test
    fun `code 가 없어도 파싱된다`() {
        val problem = json.decodeFromString<ProblemDetail>("""{"status":500}""")

        assertNull(problem.rawCode)
        assertEquals(ApiErrorCode.UNKNOWN, problem.errorCode)
    }

    /**
     * 필드가 하나도 없어도 예외를 던지면 안 된다.
     * 에러 응답 파싱이 실패하면 원래 실패 원인이 가려진다.
     */
    @Test
    fun `빈 객체도 기본값으로 파싱된다`() {
        val problem = json.decodeFromString<ProblemDetail>("{}")

        assertEquals(0, problem.status)
        assertEquals("about:blank", problem.type)
        assertEquals(ApiErrorCode.UNKNOWN, problem.errorCode)
    }

    @Test
    fun `스펙에 없는 필드가 섞여도 무시한다`() {
        val problem = json.decodeFromString<ProblemDetail>(
            """{"status":429,"code":"LIMIT_EXCEEDED","usage":{"remaining":0}}"""
        )

        assertEquals(ApiErrorCode.LIMIT_EXCEEDED, problem.errorCode)
    }

    @Test
    fun `errors_md 의 HTTP 오류 코드가 모두 매핑된다`() {
        // 계약에는 13종이 있는데 EXTRACTION_FAILED 는 여기 없다 — HTTP 오류가 아니라
        // 200 응답 본문의 `PillAttribute.error` 에 실리는 값이라 축이 다르다.
        val fromSpec = listOf(
            "INVALID_REQUEST", "INVALID_PAGINATION", "INVALID_ATTRIBUTE_TOKEN",
            "UNAUTHORIZED", "APP_CHECK_FAILED",
            "KAKAO_TOKEN_INVALID", "PILL_DETAIL_NOT_FOUND", "IMAGE_SIZE_EXCEEDED",
            "LIMIT_EXCEEDED", "CLASSIFICATION_FAILED", "INTERNAL_ERROR", "SERVICE_UNAVAILABLE"
        )

        fromSpec.forEach { raw ->
            assertEquals("$raw 가 UNKNOWN 으로 떨어졌다", raw, ApiErrorCode.from(raw).name)
        }
        // UNKNOWN 은 폴백 전용이라 서버 코드 목록에 없다.
        assertEquals(fromSpec.size + 1, ApiErrorCode.entries.size)
    }

    @Test
    fun `from 은 null 과 빈 문자열을 UNKNOWN 으로 본다`() {
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.from(null))
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.from(""))
    }
}

class ApiFailureTest {

    private fun failure(status: Int, code: String?) = ApiFailure(
        httpStatus = status,
        problem = ProblemDetail(status = status, rawCode = code),
        requestPath = "/api/v0/users/me"
    )

    @Test
    fun `503 과 500 은 재시도 대상이지만 로그아웃 사유가 아니다`() {
        listOf(
            failure(503, "SERVICE_UNAVAILABLE"),
            failure(500, "INTERNAL_ERROR")
        ).forEach {
            assertTrue("${it.code} 는 재시도 대상이어야 한다", it.isRetryable)
            assertFalse("${it.code} 로 로그아웃시키면 안 된다", it.requiresSignIn)
        }
    }

    @Test
    fun `CLASSIFICATION_FAILED 는 500 이어도 재시도 대상이 아니다`() {
        // 같은 이미지를 다시 보내도 같은 결과다(errors.md "클라이언트 대응" = 재촬영).
        // HTTP status 폴백(5xx=재시도)보다 이 code 가 우선해야 한다.
        assertFalse(failure(500, "CLASSIFICATION_FAILED").isRetryable)
    }

    @Test
    fun `401 UNAUTHORIZED 만 재로그인을 요구한다`() {
        assertTrue(failure(401, "UNAUTHORIZED").requiresSignIn)
        // App Check 실패는 재로그인해도 소용없다.
        assertFalse(failure(401, "APP_CHECK_FAILED").requiresSignIn)
        assertFalse(failure(401, "APP_CHECK_FAILED").isRetryable)
    }

    @Test
    fun `모르는 code 는 HTTP status 로 폴백한다`() {
        // 5xx → 재시도
        assertTrue(failure(502, "SOMETHING_NEW").isRetryable)
        assertTrue(failure(500, null).isRetryable)
        // 4xx → 입력 문제, 재시도 무의미
        assertFalse(failure(400, "SOMETHING_NEW").isRetryable)
        assertFalse(failure(404, null).isRetryable)
        // 401 은 code 를 몰라도 재로그인 대상
        assertTrue(failure(401, "SOMETHING_NEW").requiresSignIn)
    }

    @Test
    fun `본문이 없어도 status 로 판단한다`() {
        val noBody = ApiFailure(httpStatus = 503, problem = null, requestPath = null)

        assertEquals(ApiErrorCode.UNKNOWN, noBody.code)
        assertTrue(noBody.isRetryable)
        assertFalse(noBody.requiresSignIn)
    }

    @Test
    fun `메시지에 status 와 code 가 담긴다`() {
        val message = failure(429, "LIMIT_EXCEEDED").message.orEmpty()

        assertTrue(message, message.contains("429"))
        assertTrue(message, message.contains("LIMIT_EXCEEDED"))
        assertTrue(message, message.contains("/api/v0/users/me"))
    }
}
