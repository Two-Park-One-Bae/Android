package app.nursemate.core.network.auth

/**
 * `Authorization: Bearer` 에 실을 Firebase ID 토큰 공급자.
 *
 * 구현은 `core:data` 의 AuthRepository 쪽에 있다. 여기에 인터페이스만 두는 이유는 의존 방향 때문이다 —
 * `core:network` 가 `core:data` 를 알면 방향이 뒤집힌다. 데이터 계층이 네트워크를 쓰는 쪽이 자연스럽다.
 */
interface BearerTokenProvider {

    /**
     * @param forceRefresh 서버가 401 을 준 뒤 **딱 한 번** 재시도할 때만 true.
     *                     평소엔 Firebase SDK 가 만료 전 알아서 갱신한다.
     * @return 로그인 상태가 아니거나 **발급에 실패하면 null**. 던지지 않는다 —
     *         OkHttp 인터셉터가 `runBlocking` 으로 받아 가므로 예외가 새면 앱이 죽는다
     *         (자세한 이유는 `FirebaseAuthRepository.idToken`). [AppCheckTokenProvider] 도 같다.
     */
    suspend fun token(forceRefresh: Boolean = false): String?
}
