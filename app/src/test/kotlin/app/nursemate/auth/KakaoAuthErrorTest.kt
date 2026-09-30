package app.nursemate.auth

import app.nursemate.core.data.auth.AuthError
import app.nursemate.core.network.error.ApiFailure
import app.nursemate.core.network.error.ProblemDetail
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 카카오 로그인 실패를 **원인별로** 가르는지 본다 (NM-452).
 *
 * 예전에는 취소만 걸러내고 나머지를 전부 `Unknown` 으로 합쳤다. 그래서 401(우리 앱 토큰이
 * 아님)과 503(일시 장애)이 같은 칸에 떨어졌고, 화면은 늘 「잠시 후 다시 시도해 주세요」만
 * 말했다 — 카카오만 죽었을 때 구글·애플로는 멀쩡히 들어갈 수 있는데 그 길을 안내할 수 없었다.
 *
 * ⚠️ **틀려도 조용한 종류의 버그다.** 로그인은 어차피 실패하고 사용자는 문구 하나만 본다.
 * 그래서 매핑을 테스트로 고정한다.
 */
class KakaoAuthErrorTest {

    @Test
    fun `사용자가 창을 닫으면 취소다`() {
        val cancelled = ClientError(ClientErrorCause.Cancelled, "사용자가 로그인을 취소했습니다")

        assertEquals(AuthError.Cancelled, cancelled.toKakaoAuthError())
    }

    @Test
    fun `서버가 카카오 토큰을 거절하면 재시도를 안내한다`() {
        val rejected = apiFailure(401, "KAKAO_TOKEN_INVALID")

        assertEquals(AuthError.KakaoTokenInvalid, rejected.toKakaoAuthError())
    }

    @Test
    fun `일시 장애는 다른 방법을 안내할 수 있게 따로 가른다`() {
        val unavailable = apiFailure(503, "SERVICE_UNAVAILABLE")

        assertEquals(AuthError.ServiceUnavailable, unavailable.toKakaoAuthError())
    }

    /**
     * 500 도 로그아웃 사유는 아니지만([ApiFailure.requiresSignIn]) 사용자가 할 수 있는 일이
     * 「잠시 후 다시」뿐이라 따로 가르지 않는다. iOS 도 같은 칸에 둔다.
     */
    @Test
    fun `서버 내부 오류는 일반 실패로 둔다`() {
        val internal = apiFailure(500, "INTERNAL_ERROR")

        assertTrue(internal.toKakaoAuthError() is AuthError.Unknown)
    }

    @Test
    fun `네트워크 예외는 일반 실패로 둔다`() {
        val offline = IOException("연결 실패")

        assertTrue(offline.toKakaoAuthError() is AuthError.Unknown)
    }

    /** 코드를 모르면 status 로 떨어진다 — 그 경로도 `Unknown` 이어야 한다. */
    @Test
    fun `모르는 코드는 일반 실패로 둔다`() {
        val unfamiliar = apiFailure(418, "I_AM_A_TEAPOT")

        assertTrue(unfamiliar.toKakaoAuthError() is AuthError.Unknown)
    }

    private fun apiFailure(status: Int, code: String) = ApiFailure(
        httpStatus = status,
        problem = ProblemDetail(status = status, rawCode = code),
        requestPath = "/api/v0/auth/kakao/token"
    )
}
