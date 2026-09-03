package app.nursemate.core.network.auth

import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 인증이 필요한 요청에 `Authorization: Bearer <Firebase ID 토큰>` 을 붙인다.
 *
 * 공개 경로([PUBLIC_PATHS])는 건너뛴다. 붙여 보내도 서버가 무시하겠지만, 카카오 교환처럼
 * **로그인 전에** 부르는 경로가 섞여 있어 토큰을 얻으려다 헛돌게 된다.
 *
 * 토큰을 못 얻어도 헤더 없이 그대로 보낸다. 여기서 막아 버리면 서버가 줄 401 을 못 받고,
 * 세션 만료를 판단할 근거가 사라진다.
 *
 * `runBlocking` 을 쓰는 건 OkHttp 인터셉터가 이미 워커 스레드에서 돌기 때문이다.
 * 메인 스레드를 막지 않는다.
 */
class AuthHeaderInterceptor @Inject constructor(private val tokens: BearerTokenProvider) : Interceptor {

    // 건너뛸 이유가 둘(공개 경로 · 토큰 없음)이라 조기 반환이 가장 읽기 쉽다.
    // 하나로 합치면 조건이 뭉쳐서 왜 건너뛰는지가 흐려진다.
    @Suppress("ReturnCount")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (isPublicPath(request.url.encodedPath)) return chain.proceed(request)

        val token = runBlocking { tokens.token() } ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$token")
                .build()
        )
    }
}

internal const val HEADER_AUTHORIZATION = "Authorization"
internal const val BEARER_PREFIX = "Bearer "
