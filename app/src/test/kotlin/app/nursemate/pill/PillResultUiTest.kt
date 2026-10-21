package app.nursemate.pill

import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 식별 결과 화면이 상태에서 무엇을 뽑나 — 번호 · 상태 · 찾은 수 · 확정 수(spec §식별 결과 · NM-587). */
class PillResultUiTest {

    private fun candidate(code: String) = PillCandidate(code, LicenseStatus.NORMAL, "약$code", "업체", "", "")

    /** JVM 에서는 검출 결과(비트맵)를 못 만든다 — 카드는 직접 추가한 알약으로 세운다. */
    private val base = PillUiState(
        manualPillIds = listOf("m1"),
        selections = mapOf("m1" to candidate("A"))
    )

    @Test
    fun `직접 추가한 알약은 찾은 수에 넣지 않는다`() {
        val ui = base.toResultUi()
        assertEquals(0, ui.found)
        assertEquals(listOf("m1"), ui.cards.map { it.id })
        assertEquals(PillResultStatus.IDENTIFIED, ui.cards.single().status)
    }

    @Test
    fun `확정 수와 남은 수는 카드 전체로 센다`() {
        val ui = base.copy(manualPillIds = listOf("m1", "m2")).toResultUi()
        assertEquals(1, ui.confirmed)
        assertEquals(1, ui.unconfirmed)
    }

    @Test
    fun `가운데 알약이 빠지면 뒤 번호가 당겨진다`() {
        val ui = base.copy(manualPillIds = listOf("m1", "m3")).toResultUi()
        assertEquals(listOf(1, 2), ui.cards.map { it.number })
        assertEquals(listOf("m1", "m3"), ui.cards.map { it.id })
    }

    @Test
    fun `기록에 남는 것은 확정한 알약뿐이고 보이는 순서를 따른다`() {
        val state = base.copy(
            manualPillIds = listOf("m1", "m2", "m3"),
            selections = mapOf("m3" to candidate("C"), "m1" to candidate("A"))
        )
        assertEquals(listOf("A", "C"), state.finalPills().map { it.pillCode })
    }
}
