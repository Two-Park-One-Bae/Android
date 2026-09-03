package app.nursemate.auth

import android.content.Context
import app.nursemate.core.data.auth.AuthError
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 카카오 액세스 토큰 획득.
 *
 * 여기서 얻는 건 **카카오 토큰이지 Firebase 토큰이 아니다.** 서버가
 * `POST /api/v0/auth/kakao/token` 으로 Firebase Custom Token 을 발급해 주고,
 * 그걸로 Firebase 에 로그인해야 비로소 다른 API 에 쓸 ID 토큰이 나온다
 * (spec `api/domains/auth.md` §소셜 로그인).
 *
 * ## 카카오톡이 있으면 앱으로, 없으면 웹으로
 * 카카오톡이 깔려 있으면 앱 간 전환이 빠르고 재로그인이 필요 없다. 다만 카카오톡이
 * 있어도 **사용자가 취소하면** `ClientError(Cancelled)` 로 떨어지는데, 이때 웹으로
 * 넘기면 취소했는데 또 로그인 창이 뜬다. 그래서 취소만은 그대로 올린다.
 *
 * SDK 는 콜백 API 라 [suspendCoroutine] 으로 감싼다. 콜백이 정확히 한 번만 불리므로
 * 재개(resume)가 중복될 걱정은 없다.
 */
@Singleton
class KakaoAccessTokenProvider @Inject constructor() {

    suspend fun request(context: Context): Result<String> {
        val client = UserApiClient.instance
        return if (client.isKakaoTalkLoginAvailable(context)) {
            loginWithTalk(context, client).recoverCatching { error ->
                // 취소가 아니면 웹으로 한 번 더 시도한다 — 카카오톡이 로그인되어 있지 않거나
                // 앱 상태가 이상한 경우가 있다.
                if (error.isUserCancellation()) throw error
                loginWithAccount(context, client).getOrThrow()
            }
        } else {
            loginWithAccount(context, client)
        }
    }

    private suspend fun loginWithTalk(context: Context, client: UserApiClient): Result<String> =
        suspendCoroutine { continuation ->
            client.loginWithKakaoTalk(context) { token, error ->
                continuation.resume(token.toResult(error))
            }
        }

    private suspend fun loginWithAccount(context: Context, client: UserApiClient): Result<String> =
        suspendCoroutine { continuation ->
            client.loginWithKakaoAccount(context) { token, error ->
                continuation.resume(token.toResult(error))
            }
        }

    private fun OAuthToken?.toResult(error: Throwable?): Result<String> = when {
        error != null -> Result.failure(error)
        this != null -> Result.success(accessToken)
        else -> Result.failure(IllegalStateException("카카오 토큰과 오류가 모두 비어 있다"))
    }
}

/** 사용자가 로그인 창을 닫았는가. 이건 오류로 다루지 않는다. */
fun Throwable.isUserCancellation(): Boolean = this is ClientError && reason == ClientErrorCause.Cancelled

/** 카카오 예외를 화면이 분기할 수 있는 형태로 바꾼다. */
fun Throwable.toKakaoAuthError(): AuthError = if (isUserCancellation()) AuthError.Cancelled else AuthError.Unknown(this)
