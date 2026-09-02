package app.nursemate.core.network.api

import app.nursemate.core.model.User
import retrofit2.http.GET

/**
 * 회원·동의 상태 API.
 *
 * 경로를 **상대 경로**로 적는다. `/api/v0/...` 처럼 `/` 로 시작하면 Retrofit 이 baseUrl 의
 * 경로 부분을 버린다 — 지금은 baseUrl 에 경로가 없어 티가 안 나지만, 나중에 프록시 경로가
 * 붙으면 조용히 깨진다.
 */
interface UserApi {

    /**
     * 현재 회원 정보와 동의 상태.
     *
     * **최초 호출이면 서버가 회원을 만든다**(upsert). 별도 가입 엔드포인트가 없다.
     *
     * @throws app.nursemate.core.network.error.ApiFailure 401·500·503 등. 500 은 공급자 불일치일
     *         수 있는데 재로그인해도 같은 응답이 오므로 로그아웃시키지 않는다.
     */
    @GET("api/v0/users/me")
    suspend fun me(): User
}
