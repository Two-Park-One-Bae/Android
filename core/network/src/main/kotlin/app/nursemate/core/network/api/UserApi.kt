package app.nursemate.core.network.api

import app.nursemate.core.model.User
import retrofit2.http.DELETE
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

    /**
     * 회원 탈퇴. 회원·동의·userId 로 키된 데이터와 Firebase 사용자를 지운다(Apple 심사 필수 요건).
     *
     * **식별 사용량은 지워지지 않는다** — 지우면 "한도 소진 → 탈퇴 → 재로그인"으로 한도를
     * 무제한 초기화할 수 있다. 카운트 키가 회원이 아니라 소셜 식별자 해시라 재가입해도 이어진다.
     * 탈퇴 안내 문구는 이 예외를 사실대로 적어야 한다(spec §탈퇴).
     *
     * **멱등** — 이미 지워진 회원이면 남은 정리를 하고 204 를 준다. 재시도가 안전하다.
     *
     * @throws app.nursemate.core.network.error.ApiFailure
     *   500 — Firebase 사용자 삭제가 실패한 경우. 계정이 남아 있으므로 **로그아웃하지 않고**
     *   재시도한다. 삭제됐다고 안내해 놓고 계정이 살아 있는 상태를 만들지 않기 위해서다.
     */
    @DELETE("api/v0/users/me")
    suspend fun deleteMe()
}
