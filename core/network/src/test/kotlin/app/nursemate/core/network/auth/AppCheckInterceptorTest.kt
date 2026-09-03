package app.nursemate.core.network.auth

import java.net.HttpURLConnection.HTTP_OK
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AppCheckInterceptorTest {

    private lateinit var server: MockWebServer

    private class FakeAppCheck(private val value: String?) : AppCheckTokenProvider {
        var callCount = 0
            private set

        override suspend fun token(): String? {
            callCount++
            return value
        }
    }

    private fun clientWith(tokens: AppCheckTokenProvider) =
        OkHttpClient.Builder().addInterceptor(AppCheckInterceptor(tokens, ApiHost(server.hostName))).build()

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
    fun `알약 식별 3경로에만 헤더를 붙인다`() {
        val tokens = FakeAppCheck("ac-token")
        val client = clientWith(tokens)

        listOf(
            "/api/v0/pill-images/upload-url",
            "/api/v0/pill-attributes",
            "/api/v0/pill-attributes/usage"
        ).forEach { path ->
            server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("{}"))
            call(client, path).use { }
            assertEquals(
                "$path 에 App Check 헤더가 없다",
                "ac-token",
                server.takeRequest().getHeader("X-Firebase-AppCheck")
            )
        }
    }

    @Test
    fun `나머지 경로에는 붙이지 않는다`() {
        val tokens = FakeAppCheck("ac-token")
        val client = clientWith(tokens)

        listOf("/api/v0/users/me", "/api/v0/consents", "/api/v0/pill-candidates", "/actuator/health")
            .forEach { path ->
                server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("{}"))
                call(client, path).use { }
                assertNull(
                    "$path 에 App Check 헤더가 붙었다",
                    server.takeRequest().getHeader("X-Firebase-AppCheck")
                )
            }

        // 대상이 아니면 토큰을 아예 요청하지 않는다 — 발급 비용을 아낀다.
        assertEquals(0, tokens.callCount)
    }

    @Test
    fun `토큰 발급에 실패해도 요청은 그대로 나간다`() {
        // 여기서 막으면 서버가 줄 401 APP_CHECK_FAILED 를 못 받아 원인을 알 수 없다.
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("{}"))

        call(clientWith(FakeAppCheck(null)), "/api/v0/pill-attributes").use { }

        assertNull(server.takeRequest().getHeader("X-Firebase-AppCheck"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `경로가 접두만 같으면 붙이지 않는다`() {
        // "/api/v0/pill-attributes" 로 시작하는 다른 경로가 생겨도 오검출하지 않아야 한다.
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody("{}"))
        val tokens = FakeAppCheck("ac-token")

        call(clientWith(tokens), "/api/v0/pill-attributes/history").use { }

        assertNull(server.takeRequest().getHeader("X-Firebase-AppCheck"))
        assertEquals(0, tokens.callCount)
    }
}
