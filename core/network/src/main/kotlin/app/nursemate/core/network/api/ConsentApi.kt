package app.nursemate.core.network.api

import app.nursemate.core.model.ConsentAgreement
import app.nursemate.core.model.ConsentDefinition
import app.nursemate.core.model.User
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 동의 항목 조회·저장 (NM-412).
 *
 * 두 호출의 인증이 다르다 — 조회는 **공개**(로그인 전 화면에도 띄울 수 있어야 한다),
 * 저장은 Bearer 다. 조회 경로는 `PublicEndpoints` 에 등록돼 있어 인터셉터가 토큰을 붙이지 않는다.
 */
interface ConsentApi {

    /**
     * 필수 동의 항목과 **현재 버전**.
     *
     * 앱은 이 응답으로 화면을 구성하고, 저장할 때 같은 버전을 되돌려 보낸다.
     * 버전을 앱에 박아 두면 약관 개정 때마다 스토어 심사를 기다려야 한다(spec §동의 온보딩).
     */
    @GET("api/v0/consents")
    suspend fun definitions(): List<ConsentDefinition>

    /**
     * 동의 저장. **필수 항목 전체를 한 번에** 보낸다 — 부분 저장이 없다(단일 트랜잭션).
     *
     * @return 갱신된 회원. `onboardingRequired` 는 이 응답 값만 믿고 앱이 추측하지 않는다.
     * @throws app.nursemate.core.network.error.ApiFailure
     *   400 — 항목 누락·`agreed=false`·**버전 불일치**. 저장하는 사이 서버가 약관을 개정한
     *   경우라, 오류로 끝내지 말고 [definitions] 를 다시 받아 화면을 새 버전으로 다시 그린다.
     */
    @POST("api/v0/users/me/consents")
    suspend fun agree(@Body request: ConsentAgreementRequest): User
}

@Serializable
data class ConsentAgreementRequest(val agreements: List<ConsentAgreement>)
