package app.nursemate.core.data.auth

import app.nursemate.core.model.User
import app.nursemate.core.network.api.UserApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 회원 정보 조회.
 *
 * 스펙 §진입 라우팅이 `onboardingRequired` 로 화면을 가르므로, 앱 진입과 포그라운드 복귀마다
 * 이걸 부른다. 실패해도 **화면을 전환하지 않는다** — 값을 모르는 상태에서 홈이나 로그인으로
 * 보내면 잘못된 화면에 갇힌다.
 */
@Singleton
class UserRepository @Inject constructor(private val userApi: UserApi) {
    /** 실패 사유는 [app.nursemate.core.network.error.ApiFailure] 로 감싸여 온다. */
    suspend fun me(): Result<User> = runCatching { userApi.me() }
}
