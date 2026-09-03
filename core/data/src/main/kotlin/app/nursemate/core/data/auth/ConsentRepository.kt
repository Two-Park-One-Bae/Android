package app.nursemate.core.data.auth

import app.nursemate.core.model.ConsentAgreement
import app.nursemate.core.model.ConsentDefinition
import app.nursemate.core.model.User
import app.nursemate.core.network.api.ConsentAgreementRequest
import app.nursemate.core.network.api.ConsentApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 동의 항목 조회·저장.
 *
 * 화면은 [definitions] 로 받은 항목을 그대로 그리고, 동의하면 **받은 그대로** 되돌려 보낸다.
 * 중간에서 버전을 바꾸거나 항목을 만들어 내지 않는다 — 그러면 서버가 400(버전 불일치)을 준다.
 */
@Singleton
class ConsentRepository @Inject constructor(private val consentApi: ConsentApi) {

    suspend fun definitions(): Result<List<ConsentDefinition>> = runCatching { consentApi.definitions() }

    /**
     * 필수 항목 전체를 `agreed=true` 로 한 번에 저장한다.
     *
     * 선택 항목은 지금 없다. 생기면 화면의 체크 상태를 그대로 실어야 하므로 여기 시그니처가
     * 바뀐다 — 그때까지는 "필수만, 전부 동의" 라는 스펙 그대로다.
     *
     * @param definitions [definitions] 로 받은 목록 그대로. 버전이 여기서 실려 나간다.
     */
    suspend fun agreeToRequired(definitions: List<ConsentDefinition>): Result<User> = runCatching {
        val agreements = definitions
            .filter { it.required }
            .map { ConsentAgreement(type = it.type, version = it.version, agreed = true) }
        consentApi.agree(ConsentAgreementRequest(agreements))
    }
}
