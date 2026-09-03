package app.nursemate.core.data.auth

import app.nursemate.core.network.auth.BearerTokenProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네트워크 계층에 Firebase ID 토큰을 건네는 어댑터.
 *
 * `core:network` 는 [BearerTokenProvider] 인터페이스만 알고 [AuthRepository] 는 모른다.
 * 그래야 의존이 `core:data → core:network` 한 방향으로만 흐른다.
 */
@Singleton
class FirebaseBearerTokenProvider @Inject constructor(private val authRepository: AuthRepository) :
    BearerTokenProvider {

    override suspend fun token(forceRefresh: Boolean): String? = authRepository.idToken(forceRefresh)
}
