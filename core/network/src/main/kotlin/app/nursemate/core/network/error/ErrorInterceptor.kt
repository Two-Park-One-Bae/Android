package app.nursemate.core.network.error

import javax.inject.Inject
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 2xx 가 아닌 응답을 [ApiFailure] 로 바꿔 던진다.
 *
 * 호출부가 `retrofit2.HttpException` 을 붙들고 본문을 다시 읽어 파싱하는 일을 없앤다.
 * 에러 판단 근거(`code`)를 한곳에서 뽑아 두면 화면마다 같은 파싱을 반복하지 않는다.
 *
 * ## 본문은 한 번만 읽을 수 있다
 * `response.body.string()` 은 스트림을 소비한다. 어차피 예외로 끝낼 응답이라 상관없지만,
 * 실패해도 원래 상태 코드는 살려서 넘긴다 — 본문이 비었거나 problem+json 이 아닐 수 있다.
 */
class ErrorInterceptor @Inject constructor(private val json: Json) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return response

        val body = runCatching { response.body?.string()?.takeIf { it.isNotBlank() } }.getOrNull()
        val problem = body?.let { runCatching { json.decodeFromString<ProblemDetail>(it) }.getOrNull() }

        response.close()
        throw ApiFailure(
            httpStatus = response.code,
            problem = problem,
            requestPath = chain.request().url.encodedPath,
            rawBody = body
        )
    }
}
