package app.nursemate.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [User.needsReconsent] 의 경계 — **안내 문구만** 가르는 값이다(spec §개정 재동의).
 *
 * 틀리면 최초 가입자에게 「약관이 변경되었어요」가 나간다. 게이트가 아니라 말이 틀리는 것이라
 * 테스트로 박아 두지 않으면 눈에 안 띈 채로 남는다. iOS `AuthUseCaseTests` 와 같은 경계다.
 */
class UserReconsentTest {

    @Test
    fun `동의한 적 있는데 지금 미충족이면 개정이다`() {
        assertTrue(user(consent(ConsentType.TERMS, agreed = true, satisfied = false)).needsReconsent)
    }

    @Test
    fun `한 번도 동의한 적 없으면 개정이 아니다`() {
        assertFalse(user(consent(ConsentType.TERMS, agreed = false, satisfied = false)).needsReconsent)
    }

    @Test
    fun `동의했고 충족이면 개정이 아니다`() {
        assertFalse(user(consent(ConsentType.TERMS, agreed = true, satisfied = true)).needsReconsent)
    }

    @Test
    fun `항목이 비어 있으면 개정이 아니다`() {
        assertFalse(user().needsReconsent)
    }

    /** 둘 중 하나만 개정돼도 개정이다 — 약관은 충족인데 방침만 올라간 경우. */
    @Test
    fun `한 항목만 미충족이어도 개정이다`() {
        val user = user(
            consent(ConsentType.TERMS, agreed = true, satisfied = true),
            consent(ConsentType.PRIVACY, agreed = true, satisfied = false)
        )
        assertTrue(user.needsReconsent)
    }

    /**
     * `onboardingRequired` 와 독립이다 — 게이트는 서버가 쥐고 이 값은 문구만 고른다.
     * 서버가 필수 항목을 늘려 `onboardingRequired=true` 인데 그 항목은 동의한 적이 없으면,
     * 그 사람은 개정 재동의가 아니라 **처음 동의**다.
     */
    @Test
    fun `서버가 항목을 늘려 막혔어도 동의한 적 없으면 개정이 아니다`() {
        val user = user(
            consent(ConsentType.TERMS, agreed = true, satisfied = true),
            consent(ConsentType.PRIVACY, agreed = false, satisfied = false),
            onboardingRequired = true
        )
        assertFalse(user.needsReconsent)
    }

    private fun consent(type: ConsentType, agreed: Boolean, satisfied: Boolean) =
        ConsentStatus(type = type, agreed = agreed, version = "2026-09-10".takeIf { agreed }, satisfied = satisfied)

    private fun user(vararg consents: ConsentStatus, onboardingRequired: Boolean = true) = User(
        userId = "u1",
        provider = AuthProvider.KAKAO,
        providerUserId = "p1",
        createdAt = "2026-09-01T00:00:00Z",
        consents = consents.toList(),
        onboardingRequired = onboardingRequired
    )
}
