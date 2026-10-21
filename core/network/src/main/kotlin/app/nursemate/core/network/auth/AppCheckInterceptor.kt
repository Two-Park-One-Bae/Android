package app.nursemate.core.network.auth

import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 알약 식별 경로에 `X-Firebase-AppCheck` 를 붙인다.
 *
 * ## 왜 몇 경로뿐인가
 * 서버가 알약 식별 · 학습 라벨 경로에만 검증을 건다(`FirebaseAppCheckInterceptor`). 스펙도 같은 경로에만
 * `AppCheckToken` 파라미터를 선언했다([APP_CHECK_PATHS] · [APP_CHECK_PATTERNS]). 전 요청에 붙이면 토큰 발급 비용만 늘고
 * 계약에서도 벗어난다.
 *
 * ## 왜 이 API 만 보호하나
 * 앱에 박힌 키는 공개 값이라 APK 를 뜯으면 누구나 서버를 직접 부를 수 있다. 알약 식별은
 * 서버가 외부 AI 를 호출하는 비싼 경로고 일일 한도가 걸려 있어, 스크립트로 때리면 비용이 샌다.
 *
 * 토큰을 못 얻어도 요청은 그대로 보낸다 — 여기서 막으면 서버가 줄 401 `APP_CHECK_FAILED` 를
 * 못 받아 원인을 알 수 없다.
 */
class AppCheckInterceptor @Inject constructor(private val tokens: AppCheckTokenProvider, private val apiHost: ApiHost) :
    Interceptor {

    // 붙일 이유가 없는 경우와 못 붙이는 경우를 나눠 두는 편이 읽기 쉽다.
    @Suppress("ReturnCount")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 우리 서버가 아니면 붙이지 않는다 — 경로만 보면 외부 호스트에도 토큰이 실린다.
        if (!apiHost.matches(request.url)) return chain.proceed(request)
        if (!needsAppCheck(request.url.encodedPath)) return chain.proceed(request)

        val token = runBlocking { tokens.token() } ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder().header(HEADER_APP_CHECK, token).build()
        )
    }
}

internal const val HEADER_APP_CHECK = "X-Firebase-AppCheck"

/**
 * 정본: `spec/api/openapi.yaml` 에서 `parameters: AppCheckToken` 을 참조하는 오퍼레이션. 경로에 변수가 없는 것은 여기,
 * 있는 것은 [APP_CHECK_PATTERNS] 에 둔다.
 */
internal val APP_CHECK_PATHS = setOf(
    "/api/v0/pill-images/upload-url",
    // 속성 추출은 v1 으로 옮겼다(NM-516). 잔여 횟수 조회는 바뀌지 않아 v0 그대로다.
    "/api/v1/pill-attributes",
    "/api/v0/pill-attributes/usage"
)

/**
 * 경로에 변수가 있는 App Check 대상 — 학습 라벨(NM-571). 빠뜨리면 서버가 401 `APP_CHECK_FAILED` 로 거절한다
 * (2026-10-21 실기기 — 라벨 PUT 이 그렇게 조용히 버려졌다).
 */
internal val APP_CHECK_PATTERNS = listOf(
    Regex("/api/v0/pill-images/[^/]+/labels/[^/]+"),
    Regex("/api/v0/pill-images/[^/]+/segmentation")
)

internal fun needsAppCheck(path: String): Boolean =
    path in APP_CHECK_PATHS || APP_CHECK_PATTERNS.any { it.matches(path) }
