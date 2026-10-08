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

    // ── NM-548 선택 동의 ──────────────────────────────────────────────

    /**
     * 선택 항목 하나 때문에 「약관이 변경되었어요」가 뜨면 안 된다.
     *
     * `OVERSEAS` 는 고지사항이 바뀌면 버전이 오르는데, 필수 약관은 그대로인 채로 이 상태가
     * 오래 남는다. 세어 버리면 쓸 때마다 변경 안내를 본다.
     */
    @Test
    fun `선택 항목이 옛 버전이어도 개정 재동의가 아니다`() {
        val user = user(
            consent(ConsentType.TERMS, agreed = true, satisfied = true),
            consent(ConsentType.PRIVACY, agreed = true, satisfied = true),
            consent(ConsentType.OVERSEAS, agreed = true, satisfied = false)
        )
        assertFalse(user.needsReconsent)
    }

    @Test
    fun `지금 버전으로 동의했을 때만 측정을 켠다`() {
        assertTrue(user(consent(ConsentType.OVERSEAS, agreed = true, satisfied = true)).overseasConsented)
    }

    /** 옛 버전 동의는 **미동의로 다룬다** — 고지가 바뀌었는데 옛 동의로 계속 보내면 안 된다. */
    @Test
    fun `선택 항목이 옛 버전이면 측정을 켜지 않는다`() {
        assertFalse(user(consent(ConsentType.OVERSEAS, agreed = true, satisfied = false)).overseasConsented)
    }

    @Test
    fun `거부했으면 측정을 켜지 않는다`() {
        assertFalse(user(consent(ConsentType.OVERSEAS, agreed = false, satisfied = false)).overseasConsented)
    }

    /** 서버에 `OVERSEAS` 정의가 없는 동안(도입 순서 ②~③)은 항목 자체가 안 온다. */
    @Test
    fun `선택 항목이 아예 없으면 측정을 켜지 않는다`() {
        val user = user(
            consent(ConsentType.TERMS, agreed = true, satisfied = true),
            consent(ConsentType.PRIVACY, agreed = true, satisfied = true)
        )
        assertFalse(user.overseasConsented)
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
