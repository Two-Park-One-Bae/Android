package app.nursemate.core.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 카카오 토큰 → Firebase Custom Token 교환.
 *
 * 구글·애플은 Firebase 네이티브 공급자라 이 단계가 없다. 카카오만 Firebase 가 직접
 * 지원하지 않아 서버를 거친다(spec `api/domains/auth.md` §소셜 로그인).
 *
 * **공개 엔드포인트다** — 로그인 전에 부르므로 Bearer 를 싣지 않는다.
 * `PublicEndpoints` 에 등록돼 있어 인터셉터가 건너뛴다.
 */
interface AuthApi {

    /**
     * @throws app.nursemate.core.network.error.ApiFailure
     *   401 `KAKAO_TOKEN_INVALID` — 만료·위조이거나 **우리 카카오 앱에서 발급되지 않은** 토큰.
     *   503 `SERVICE_UNAVAILABLE` — 카카오·Firebase 일시 장애. Custom Token 발급 실패도 여기다.
     */
    @POST("api/v0/auth/kakao/token")
    suspend fun exchangeKakaoToken(@Body request: KakaoTokenRequest): KakaoTokenResponse
}

@Serializable
data class KakaoTokenRequest(val kakaoAccessToken: String)

@Serializable
data class KakaoTokenResponse(val firebaseCustomToken: String)
