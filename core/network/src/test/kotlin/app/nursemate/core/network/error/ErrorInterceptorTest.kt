package app.nursemate.core.network.error

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 비 2xx 응답이 [ApiFailure] 로 바뀌는지 실제 OkHttp 스택 위에서 확인한다. */
class ErrorInterceptorTest {

    private lateinit var server: MockWebServer

    private val client = OkHttpClient.Builder()
        .addInterceptor(ErrorInterceptor(Json { ignoreUnknownKeys = true }))
        .build()

    private fun call(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `2xx 는 그대로 통과한다`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        call("/api/v0/anything").use { assertTrue(it.isSuccessful) }
    }

    @Test
    fun `problem+json 본문을 파싱해 code 를 담는다`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"type":"about:blank","title":"Unauthorized","status":401,
                    "detail":"Authentication failed.","code":"UNAUTHORIZED"}"""
            )
        )

        val failure = runCatching { call("/api/v0/users/me").use { } }.exceptionOrNull() as ApiFailure

        assertEquals(401, failure.httpStatus)
        assertEquals(ApiErrorCode.UNAUTHORIZED, failure.code)
        assertEquals("/api/v0/users/me", failure.requestPath)
    }

    @Test
    fun `본문이 problem+json 이 아니어도 status 는 살린다`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>gateway</html>"))

        val failure = runCatching { call("/api/v0/anything").use { } }.exceptionOrNull() as ApiFailure

        assertEquals(502, failure.httpStatus)
        assertNull(failure.problem)
        // 알려진 code 가 없으면 status 로 판단한다.
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `503 은 재시도 대상이지만 재로그인 대상이 아니다`() {
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"status":503,"code":"SERVICE_UNAVAILABLE"}""")
        )

        val failure = runCatching { call("/api/v0/anything").use { } }.exceptionOrNull() as ApiFailure

        assertTrue(failure.isRetryable)
        assertFalse("503 으로 로그아웃시키면 안 된다", failure.requiresSignIn)
    }

    @Test
    fun `본문이 비어 있어도 예외를 던진다`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val failure = runCatching { call("/api/v0/anything").use { } }.exceptionOrNull()

        assertTrue("ApiFailure 가 아니라 $failure", failure is ApiFailure)
        assertEquals(404, (failure as ApiFailure).httpStatus)
    }
}
