package app.nursemate.core.network.auth

import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * 401 을 받으면 토큰을 강제로 갱신해 **한 번만** 다시 보낸다.
 *
 * 스펙(`spec/feature/auth/README.md` §토큰·세션):
 * > 401 `UNAUTHORIZED` 를 받으면 토큰을 강제 갱신해 한 번만 재시도하고,
 * > 그래도 401 이면 세션 만료로 보고 로그인 화면으로 유도한다.
 *
 * ## 왜 인터셉터가 아니라 Authenticator 인가
 * OkHttp 는 401 응답에만 `Authenticator` 를 부르고, 재시도 요청의 `priorResponse` 에 이전 응답을
 * 매달아 준다. 그래서 "이미 한 번 갱신했는가"를 직접 세지 않아도 된다 — 인터셉터로 짜면
 * 재시도 횟수를 손으로 관리하다 무한 루프를 만들기 쉽다.
 *
 * null 을 돌려주면 OkHttp 가 401 을 그대로 호출부에 넘긴다. 그 401 이 곧 세션 만료 신호다.
 */
class TokenRefreshAuthenticator @Inject constructor(private val tokens: BearerTokenProvider) : Authenticator {

    // 포기 조건이 넷이다. 각각 이유가 달라 조기 반환으로 하나씩 끊어 두는 편이 명확하다.
    @Suppress("ReturnCount")
    override fun authenticate(route: Route?, response: Response): Request? {
        // 애초에 토큰을 안 실은 요청이면 갱신할 게 없다(공개 경로 · 비로그인).
        val previous = response.request.header(HEADER_AUTHORIZATION) ?: return null

        // priorResponse 가 있으면 이번이 이미 재시도다. 두 번째 401 은 포기한다.
        if (response.priorResponse != null) return null

        val refreshed = runBlocking { tokens.token(forceRefresh = true) } ?: return null

        // 갱신했는데 값이 같으면 서버가 거부하는 이유가 만료가 아니다. 다시 보내도 같은 401 이다.
        if ("$BEARER_PREFIX$refreshed" == previous) return null

        return response.request.newBuilder()
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$refreshed")
            .build()
    }
}
