package app.nursemate.core.data.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * 인증 세션.
 *
 * 스펙 `spec/feature/auth/README.md` §진입 라우팅이 세션 유무로 화면을 가르므로,
 * "아직 모른다"([Unknown])와 "없다"([SignedOut])를 반드시 구분한다.
 * 둘을 합치면 앱 시작 직후 복원이 끝나기 전에 로그인 화면이 한 번 번쩍인다.
 */
sealed interface AuthSession {
    /** 복원 전. 스플래시를 유지한다. */
    data object Unknown : AuthSession

    data object SignedOut : AuthSession

    /**
     * @param uid Firebase UID. 서버의 `userId`와 같은 값이다(api/domains/auth.md 회원).
     * @param providerId `google.com` · `apple.com` · `custom`(카카오). 없을 수도 있어 nullable.
     */
    data class SignedIn(val uid: String, val providerId: String?) : AuthSession
}

/** 로그인 실패 사유 — 화면이 문구를 고르는 기준. */
sealed interface AuthError {
    /** 사용자가 계정 선택을 닫았다. 오류 문구를 띄우지 않는다. */
    data object Cancelled : AuthError

    /** 기기에 쓸 수 있는 구글 계정이 없다. 계정 추가를 안내한다. */
    data object NoCredential : AuthError

    /** 그 밖(네트워크·Play 서비스·Firebase). @param cause 로그용, 사용자에게 그대로 보여주지 않는다. */
    data class Unknown(val cause: Throwable) : AuthError
}

interface AuthRepository {
    /** 앱 전역에서 하나만 흐른다. 시작값은 [AuthSession.Unknown]. */
    val session: StateFlow<AuthSession>

    /**
     * 구글 ID 토큰으로 Firebase에 로그인한다.
     *
     * 토큰을 **얻는 일**은 UI 레이어(Credential Manager)가 하고, 여기서는 받은 토큰을 쓰기만 한다 —
     * 자격 증명 선택 UI가 Activity를 요구해서 데이터 레이어에 둘 수 없다.
     */
    suspend fun signInWithGoogle(idToken: String): Result<Unit>

    /**
     * `Authorization: Bearer`에 실을 Firebase ID 토큰.
     *
     * @param forceRefresh 서버가 401을 준 뒤의 **단 한 번**의 재시도에만 true를 쓴다
     *                     (spec §토큰·세션). 평소에는 SDK가 만료 전 알아서 갱신한다.
     * @return 로그인 상태가 아니면 null
     */
    suspend fun idToken(forceRefresh: Boolean = false): String?

    suspend fun signOut()
}
