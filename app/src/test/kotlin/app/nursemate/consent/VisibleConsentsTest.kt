package app.nursemate.consent

import app.nursemate.core.model.ConsentDefinition
import app.nursemate.core.model.ConsentStatus
import app.nursemate.core.model.ConsentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 동의 화면이 **어느 항목을 보일지** (NM-548).
 *
 * 서버에 `OVERSEAS` 정의가 들어오기 전에도 확인되어야 하는 규칙이라 여기서 못박는다
 * (spec `feature/auth/README.md` §동의 온보딩 · `api/domains/auth.md` §선택 동의).
 */
class VisibleConsentsTest {

    @Test
    fun `필수 항목은 응답 기록과 무관하게 항상 보인다`() {
        val visible = listOf(terms, privacy).visibleFor(
            answered = listOf(status(ConsentType.TERMS, CURRENT), status(ConsentType.PRIVACY, CURRENT))
        )

        assertEquals(listOf(ConsentType.TERMS, ConsentType.PRIVACY), visible.map { it.type })
    }

    @Test
    fun `선택 항목은 한 번도 묻지 않았으면 보인다`() {
        // version 이 null = 아직 물어본 적 없음.
        val visible = listOf(terms, privacy, overseas).visibleFor(
            answered = listOf(status(ConsentType.OVERSEAS, version = null, agreed = false))
        )

        assertTrue(ConsentType.OVERSEAS in visible.map { it.type })
    }

    @Test
    fun `선택 항목에 대한 기록이 아예 없어도 보인다`() {
        val visible = listOf(terms, overseas).visibleFor(answered = emptyList())

        assertEquals(listOf(ConsentType.TERMS, ConsentType.OVERSEAS), visible.map { it.type })
    }

    @Test
    fun `현재 버전에 이미 동의한 선택 항목은 보이지 않는다`() {
        val visible = listOf(terms, privacy, overseas).visibleFor(
            answered = listOf(status(ConsentType.OVERSEAS, CURRENT, agreed = true))
        )

        assertEquals(listOf(ConsentType.TERMS, ConsentType.PRIVACY), visible.map { it.type })
    }

    @Test
    fun `현재 버전에서 거부한 선택 항목도 보이지 않는다 — 다시 조르지 않는다`() {
        val visible = listOf(terms, overseas).visibleFor(
            answered = listOf(status(ConsentType.OVERSEAS, CURRENT, agreed = false))
        )

        assertEquals(listOf(ConsentType.TERMS), visible.map { it.type })
    }

    @Test
    fun `옛 버전에 응답한 선택 항목은 다시 보인다`() {
        // 고지사항이 바뀌어 버전이 올랐다 — 옛 응답은 지금 버전에 대한 답이 아니다.
        val visible = listOf(terms, overseas).visibleFor(
            answered = listOf(status(ConsentType.OVERSEAS, "2026-01-01", agreed = true))
        )

        assertTrue(ConsentType.OVERSEAS in visible.map { it.type })
    }

    @Test
    fun `모르는 선택 항목은 버린다 — 출시된 앱이 막히지 않는다`() {
        val unknownOptional = ConsentDefinition(
            type = ConsentType.UNKNOWN,
            version = CURRENT,
            required = false,
            policyUrl = "https://nursemate.app/privacy/whatever/",
            title = "앞으로 생길 선택 항목"
        )

        val visible = listOf(terms, unknownOptional).visibleFor(answered = emptyList())

        assertEquals(listOf(ConsentType.TERMS), visible.map { it.type })
    }

    @Test
    fun `모르는 필수 항목은 버리지 않는다 — 멈춰 세워야 하므로 화면에 남는다`() {
        // 버려 버리면 호출부의 blocked 판정이 설 자리가 없어진다. 보이되 진행이 막힌다.
        val unknownRequired = ConsentDefinition(
            type = ConsentType.UNKNOWN,
            version = CURRENT,
            required = true,
            policyUrl = "https://nursemate.app/terms/new/",
            title = "앞으로 생길 필수 항목"
        )

        val visible = listOf(terms, unknownRequired).visibleFor(answered = emptyList())

        assertEquals(2, visible.size)
    }

    @Test
    fun `서버가 선택 항목을 먼저 줘도 필수가 앞에 온다`() {
        val visible = listOf(overseas, terms, privacy).visibleFor(answered = emptyList())

        assertEquals(
            listOf(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.OVERSEAS),
            visible.map { it.type }
        )
    }

    @Test
    fun `같은 그룹 안에서는 서버가 준 순서가 그대로 유지된다`() {
        val visible = listOf(privacy, terms).visibleFor(answered = emptyList())

        assertEquals(listOf(ConsentType.PRIVACY, ConsentType.TERMS), visible.map { it.type })
    }

    @Test
    fun `선택 항목 정의가 없는 서버에서는 필수만 보인다`() {
        // 도입 순서 ②와 ③ 사이 — 새 앱이 OVERSEAS 를 모르는 서버와 붙는다.
        val visible = listOf(terms, privacy).visibleFor(answered = emptyList())

        assertEquals(listOf(ConsentType.TERMS, ConsentType.PRIVACY), visible.map { it.type })
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

        fun status(type: ConsentType, version: String?, agreed: Boolean = true) = ConsentStatus(
            type = type,
            agreed = agreed,
            version = version,
            // 이 함수는 satisfied 를 보지 않는다 — 보일지 말지는 버전 일치로만 가른다.
            satisfied = version == CURRENT && agreed
        )
    }
}
