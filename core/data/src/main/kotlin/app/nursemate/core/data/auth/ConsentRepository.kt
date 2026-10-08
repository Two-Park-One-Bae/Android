package app.nursemate.core.data.auth

import app.nursemate.core.model.ConsentAgreement
import app.nursemate.core.model.ConsentDefinition
import app.nursemate.core.model.ConsentType
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
     * 화면에 보인 항목을 한 번에 저장한다 — **필수는 `agreed=true`, 선택은 체크한 그대로**.
     *
     * ⚠️ **보낸 항목만 갱신된다**(`spec/api/domains/auth.md` §선택 동의). 그래서 `definitions` 에
     * 선택 항목이 들어 있지 않으면 서버에 있던 그 항목의 응답은 **그대로 남는다** — 호출부가
     * 화면에 보인 목록을 그대로 넘기면 "안 보였으니 건드리지 않는다"가 저절로 성립한다.
     *
     * 필수 항목의 `agreed=false` 는 서버가 400 으로 거절한다. 그래서 [checked] 는 선택 항목에만
     * 묻고, 필수는 묻지 않고 `true` 로 보낸다 — 체크하지 않은 필수가 섞여 있으면 애초에
     * 「동의하고 계속」이 눌리지 않는다.
     *
     * @param definitions [definitions] 로 받은 목록 중 **화면에 보인 것**. 버전이 여기서 실려 나간다.
     * @param checked 체크된 항목. 선택 항목의 `agreed` 가 이 값에서 나온다.
     */
    suspend fun agree(definitions: List<ConsentDefinition>, checked: Set<ConsentType>): Result<User> = runCatching {
        val agreements = definitions.map { definition ->
            ConsentAgreement(
                type = definition.type,
                version = definition.version,
                agreed = definition.required || definition.type in checked
            )
        }
        consentApi.agree(ConsentAgreementRequest(agreements))
    }
}
