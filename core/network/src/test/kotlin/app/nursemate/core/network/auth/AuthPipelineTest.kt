package app.nursemate.core.network.auth

import app.nursemate.core.network.error.ApiErrorCode
import app.nursemate.core.network.error.ApiFailure
import app.nursemate.core.network.error.ErrorInterceptor
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Bearer 첨부 · 401 재시도 · 에러 변환을 실제 OkHttp 스택 위에서 확인한다.
 *
 * 인터셉터를 따로 떼어 부르지 않고 클라이언트를 통째로 세우는 이유는, 여기서 검증하려는 게
 * **인터셉터와 Authenticator 의 상호작용**이기 때문이다. 재시도는 OkHttp 내부 루프에서 일어나
 * 단위로 떼면 재현되지 않는다.
 */
class AuthPipelineTest {

    private lateinit var server: MockWebServer

    /** 호출될 때마다 다른 토큰을 준다 — 갱신이 실제로 일어났는지 헤더로 구분하려고. */
    private class FakeTokens(private var current: String? = "token-1", private val refreshed: String? = "token-2") :
        BearerTokenProvider {
        var refreshCount = 0
            private set

        override suspend fun token(forceRefresh: Boolean): String? {
            if (forceRefresh) {
                refreshCount++
                current = refreshed
            }
            return current
        }
    }

    private fun clientWith(tokens: BearerTokenProvider) = OkHttpClient.Builder()
        .addInterceptor(AuthHeaderInterceptor(tokens, ApiHost(server.hostName)))
        .addInterceptor(ErrorInterceptor(Json { ignoreUnknownKeys = true }))
        .authenticator(TokenRefreshAuthenticator(tokens))
        .build()

    private fun call(client: OkHttpClient, path: String) =
        client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `인증 경로에는 Bearer 를 붙인다`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("{}"))

        call(clientWith(FakeTokens()), "/api/v0/users/me").use { }

        assertEquals("Bearer token-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `공개 경로에는 Bearer 를 붙이지 않는다`() {
        val client = clientWith(FakeTokens())

        listOf("/actuator/health", "/health", "/api/v0/consents", "/api/v0/auth/kakao/token")
            .forEach { path ->
                server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("{}"))
                call(client, path).use { }
                assertNull(
                    "$path 에 Authorization 이 붙었다",
                    server.takeRequest().getHeader("Authorization")
                )
            }
    }

    @Test
    fun `로그인 상태가 아니면 헤더 없이 그대로 보낸다`() {
        // 여기서 요청을 막아 버리면 서버가 줄 401 을 못 받아 세션 만료를 판단할 수 없다.
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("{}"))

        call(clientWith(FakeTokens(current = null, refreshed = null)), "/api/v0/users/me").use { }

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `401 이면 토큰을 갱신해 한 번 재시도한다`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody(problem("UNAUTHORIZED")))
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("{}"))
        val tokens = FakeTokens()

        call(clientWith(tokens), "/api/v0/users/me").use { assertTrue(it.isSuccessful) }

        assertEquals("Bearer token-1", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer token-2", server.takeRequest().getHeader("Authorization"))
        assertEquals(1, tokens.refreshCount)
    }

    @Test
    fun `두 번째 401 은 재시도하지 않고 ApiFailure 로 끝난다`() {
        repeat(2) {
            server.enqueue(
                MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody(problem("UNAUTHORIZED"))
            )
        }
        val tokens = FakeTokens()

        val failure = runCatching { call(clientWith(tokens), "/api/v0/users/me").use { } }
            .exceptionOrNull()

        assertTrue("ApiFailure 가 아니라 $failure", failure is ApiFailure)
        assertEquals(ApiErrorCode.UNAUTHORIZED, (failure as ApiFailure).code)
        assertTrue(failure.requiresSignIn)
        // 요청은 두 번(원본 + 재시도 한 번)만 나가야 한다.
        assertEquals(2, server.requestCount)
        assertEquals(1, tokens.refreshCount)
    }

    @Test
    fun `갱신해도 토큰이 그대로면 재시도하지 않는다`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody(problem("UNAUTHORIZED")))

        // 만료가 원인이 아니라는 뜻이라 다시 보내도 같은 401 이다.
        val tokens = FakeTokens(current = "same", refreshed = "same")
        runCatching { call(clientWith(tokens), "/api/v0/users/me").use { } }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `공개 경로의 401 은 재시도 대상이 아니다`() {
        server.enqueue(MockResponse().setResponseCode(HTTP_UNAUTHORIZED).setBody(problem("UNAUTHORIZED")))
        val tokens = FakeTokens()

        runCatching { call(clientWith(tokens), "/api/v0/consents").use { } }

        // 애초에 토큰을 안 실었으니 갱신할 것도 없다.
        assertEquals(1, server.requestCount)
        assertEquals(0, tokens.refreshCount)
    }

    @Test
    fun `503 은 ApiFailure 로 오고 재로그인 대상이 아니다`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody(problem("SERVICE_UNAVAILABLE")))

        val failure = runCatching { call(clientWith(FakeTokens()), "/api/v0/users/me").use { } }
            .exceptionOrNull() as ApiFailure

        assertEquals(ApiErrorCode.SERVICE_UNAVAILABLE, failure.code)
        assertTrue(failure.isRetryable)
        assertTrue("503 으로 로그아웃시키면 안 된다", !failure.requiresSignIn)
    }

    @Test
    fun `본문이 problem+json 이 아니어도 status 는 살린다`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>gateway</html>"))

        val failure = runCatching { call(clientWith(FakeTokens()), "/api/v0/users/me").use { } }
            .exceptionOrNull() as ApiFailure

        assertEquals(502, failure.httpStatus)
        assertNull(failure.problem)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun `실패 경로가 메시지에 남는다`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody(problem("PILL_DETAIL_NOT_FOUND")))

        val failure = runCatching { call(clientWith(FakeTokens()), "/api/v0/pill-details/K-001").use { } }
            .exceptionOrNull() as ApiFailure

        assertEquals("/api/v0/pill-details/K-001", failure.requestPath)
    }

    private fun problem(code: String) = """{"type":"about:blank","title":"t","status":0,"detail":"d","code":"$code"}"""
}
