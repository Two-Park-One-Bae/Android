package app.nursemate.pill

import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 공유 텍스트.
 *
 * 복사된 목록은 인수인계 메모나 메신저로 옮겨 다니고, 그 자리에는 앱의 맥락이 없다.
 * 고지가 빠진 채 목록만 도는 일이 없도록 여기서 못 박는다.
 */
class PillResultShareTest {

    private fun candidate(code: String, name: String?, company: String? = null) = PillCandidate(
        pillCode = code,
        licenseStatus = LicenseStatus.NORMAL,
        pillName = name,
        companyName = company
    )

    @Test
    fun `번호와 품목명 업체명을 한 줄씩 적는다`() {
        val text = listOf(
            candidate("1", "타이레놀정 500mg", "한국얀센"),
            candidate("2", "게보린정", "삼진제약")
        ).toShareText()

        assertTrue(text.contains("1. 타이레놀정 500mg (한국얀센)"))
        assertTrue(text.contains("2. 게보린정 (삼진제약)"))
    }

    @Test
    fun `업체명이 없으면 괄호를 붙이지 않는다`() {
        val text = listOf(candidate("1", "게보린정")).toShareText()

        assertTrue(text.contains("1. 게보린정\n"))
        assertTrue(!text.contains("()"))
    }

    @Test
    fun `품목명이 없으면 품목기준코드로 적는다`() {
        // 이름이 빠진 후보를 빈 줄로 흘려보내면 몇 번 알약이 빠졌는지 알 수 없다.
        val text = listOf(candidate("198800119", null)).toShareText()

        assertTrue(text.contains("1. 198800119"))
    }

    @Test
    fun `고지가 항상 끝에 붙는다`() {
        val text = listOf(candidate("1", "게보린정")).toShareText()

        assertTrue(text.trimEnd().endsWith("최종 판단은 의료진의 확인을 따라 주세요."))
    }

    @Test
    fun `개수를 머리에 적는다`() {
        val text = listOf(candidate("1", "가"), candidate("2", "나"), candidate("3", "다")).toShareText()

        assertEquals("[널스메이트] 알약 식별 결과 3개", text.lineSequence().first())
    }
}
