package app.nursemate.core.network.auth

/**
 * `X-Firebase-AppCheck` 에 실을 App Check 토큰 공급자.
 *
 * [BearerTokenProvider] 와 같은 이유로 인터페이스만 여기 둔다 — 구현(Firebase)은 `core:data` 다.
 */
interface AppCheckTokenProvider {

    /** 발급에 실패하면 null. 그래도 요청은 보낸다 — 서버가 줄 401 이 판단 근거다. */
    suspend fun token(): String?
}
