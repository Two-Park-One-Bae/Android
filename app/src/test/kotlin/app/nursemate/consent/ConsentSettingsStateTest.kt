package app.nursemate.consent

import app.nursemate.core.model.ConsentDefinition
import app.nursemate.core.model.ConsentStatus
import app.nursemate.core.model.ConsentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「약관 및 동의」 화면이 **무엇을 보이고 무엇을 체크된 채로 시작하나** (NM-548).
 *
 * 동의 온보딩([VisibleConsentsTest])과 규칙이 다른 자리라 따로 못박는다 —
 * spec `feature/auth/README.md` §약관 및 동의.
 */
class ConsentSettingsStateTest {

    @Test
    fun `이미 응답한 선택 항목도 보인다 — 철회하러 오는 화면이다`() {
        // 온보딩이라면 숨겼을 상태다. 여기서 숨기면 철회할 자리가 사라진다.
        val visible = listOf(terms, privacy, overseas).forSettings()

        assertEquals(
            listOf(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.OVERSEAS),
            visible.map { it.type }
        )
    }

    @Test
    fun `선택 항목이 없는 서버에서는 필수만 보인다`() {
        val visible = listOf(terms, privacy).forSettings()

        assertEquals(listOf(ConsentType.TERMS, ConsentType.PRIVACY), visible.map { it.type })
    }

    @Test
    fun `모르는 선택 항목은 버린다`() {
        val unknownOptional = overseas.copy(type = ConsentType.UNKNOWN)

        val visible = listOf(terms, unknownOptional).forSettings()

        assertEquals(listOf(ConsentType.TERMS), visible.map { it.type })
    }

    @Test
    fun `모르는 필수 항목은 남긴다 — 잠긴 채 보이기만 한다`() {
        val unknownRequired = terms.copy(type = ConsentType.UNKNOWN, title = "앞으로 생길 필수 항목")

        val visible = listOf(terms, unknownRequired).forSettings()

        assertEquals(2, visible.size)
    }

    @Test
    fun `필수가 앞에 온다`() {
        val visible = listOf(overseas, terms).forSettings()

        assertEquals(listOf(ConsentType.TERMS, ConsentType.OVERSEAS), visible.map { it.type })
    }

    @Test
    fun `필수는 기록이 없어도 늘 체크된 채로 시작한다`() {
        val checked = listOf(terms, privacy).agreedTypes(answered = emptyList())

        assertEquals(setOf(ConsentType.TERMS, ConsentType.PRIVACY), checked)
    }

    @Test
    fun `현재 버전에 동의한 선택 항목은 체크된 채로 시작한다`() {
        val checked = listOf(terms, overseas).agreedTypes(
            answered = listOf(ConsentStatus(ConsentType.OVERSEAS, agreed = true, version = CURRENT, satisfied = true))
        )

        assertTrue(ConsentType.OVERSEAS in checked)
    }

    @Test
    fun `옛 버전에 동의한 선택 항목은 체크되지 않은 채로 시작한다`() {
        // agreed && !satisfied — 고지사항이 바뀌었다. 옛 동의를 지금 동의로 보여 주면
        // 다시 동의할 기회를 뺏는 셈이다.
        val checked = listOf(terms, overseas).agreedTypes(
            answered = listOf(
                ConsentStatus(ConsentType.OVERSEAS, agreed = true, version = "2026-01-01", satisfied = false)
            )
        )

        assertFalse(ConsentType.OVERSEAS in checked)
    }

    @Test
    fun `거부한 선택 항목은 체크되지 않은 채로 시작한다`() {
        val checked = listOf(terms, overseas).agreedTypes(
            answered = listOf(ConsentStatus(ConsentType.OVERSEAS, agreed = false, version = CURRENT, satisfied = false))
        )

        assertFalse(ConsentType.OVERSEAS in checked)
    }

    @Test
    fun `한 번도 묻지 않은 선택 항목은 체크되지 않은 채로 시작한다`() {
        val checked = listOf(terms, overseas).agreedTypes(answered = emptyList())

        assertEquals(setOf(ConsentType.TERMS), checked)
    }

    @Test
    fun `저장은 처음과 달라졌을 때만 눌린다`() {
        val base = ConsentSettingsUiState(
            definitions = listOf(terms, overseas),
            checked = setOf(ConsentType.TERMS),
            baseline = setOf(ConsentType.TERMS),
            loading = false
        )

        assertFalse(base.canSave)
        assertTrue(base.copy(checked = setOf(ConsentType.TERMS, ConsentType.OVERSEAS)).canSave)
    }

    @Test
    fun `체크를 켰다 다시 끄면 저장이 도로 비활성이 된다`() {
        // 두 번 눌러 제자리로 온 것은 바뀐 것이 아니다.
        val state = ConsentSettingsUiState(
            definitions = listOf(terms, overseas),
            checked = setOf(ConsentType.TERMS, ConsentType.OVERSEAS),
            baseline = setOf(ConsentType.TERMS, ConsentType.OVERSEAS),
            loading = false
        )

        val toggledOff = state.copy(checked = state.checked - ConsentType.OVERSEAS)
        assertTrue(toggledOff.canSave)
        assertFalse(toggledOff.copy(checked = state.baseline).canSave)
    }

    @Test
    fun `저장 중에는 다시 눌리지 않는다`() {
        val state = ConsentSettingsUiState(
            definitions = listOf(terms, overseas),
            checked = setOf(ConsentType.TERMS, ConsentType.OVERSEAS),
            baseline = setOf(ConsentType.TERMS),
            loading = false,
            saving = true
        )

        assertFalse(state.canSave)
    }

    private companion object {
        const val CURRENT = "2026-10-08"

        val terms = ConsentDefinition(
            type = ConsentType.TERMS,
            version = CURRENT,
            required = true,
            policyUrl = "https://nursemate.app/terms/",
            title = "이용약관"
        )

        val privacy = ConsentDefinition(
            type = ConsentType.PRIVACY,
            version = CURRENT,
            required = true,
            policyUrl = "https://nursemate.app/privacy/",
            title = "개인정보처리방침"
        )

        val overseas = ConsentDefinition(
            type = ConsentType.OVERSEAS,
            version = CURRENT,
            required = false,
            policyUrl = "https://nursemate.app/privacy/overseas/",
            title = "개인정보 국외 이전 및 제3자 제공"
        )
    }
}
