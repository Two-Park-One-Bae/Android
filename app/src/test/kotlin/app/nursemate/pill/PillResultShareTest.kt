package app.nursemate.pill

import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
import java.time.LocalDate
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

    private val day = LocalDate.of(2026, 9, 3)

    @Test
    fun `품목명 제조사 품목코드를 줄을 나눠 적는다`() {
        // 병원 컴퓨터에 다시 입력하는 자리라 한 줄에 몰아 쓰지 않는다(인터뷰 반영 — iOS 동일).
        val text = listOf(candidate("197000123", "타이레놀정 500mg", "한국얀센")).toShareText(day)

        assertTrue(text.contains("1. 타이레놀정 500mg"))
        assertTrue(text.contains("   제조사: 한국얀센"))
        assertTrue(text.contains("   품목코드: 197000123"))
    }

    @Test
    fun `업체명이 없으면 그 줄을 아예 빼놓는다`() {
        val text = listOf(candidate("1", "게보린정")).toShareText(day)

        assertTrue(!text.contains("제조사:"))
    }

    @Test
    fun `품목명이 없으면 이름 미상으로 적는다`() {
        // 빈 줄로 흘려보내면 몇 번 알약이 빠졌는지 알 수 없다. 품목코드는 아래 줄에 남는다.
        val text = listOf(candidate("198800119", null)).toShareText(day)

        assertTrue(text.contains("1. 이름 미상"))
        assertTrue(text.contains("   품목코드: 198800119"))
    }

    @Test
    fun `고지는 NM-380 정본 문구를 쓴다`() {
        // ⚠️ iOS 는 공유물에만 다른 문장을 쓴다. 심사 대응으로 확정된 문구라 밖으로 나가는
        //    문서일수록 이쪽을 지켜야 한다.
        val text = listOf(candidate("1", "게보린정")).toShareText(day)

        assertTrue(
            text.trimEnd().endsWith(
                "널스메이트의 알약 식별 결과는 참고용 보조 정보입니다. " +
                    "투약 전 반드시 처방 내용과 약품 라벨을 확인하시고, 최종 판단은 의료진의 확인을 따라 주세요."
            )
        )
    }

    @Test
    fun `머리에 제목 날짜 개수를 적는다`() {
        val text = listOf(candidate("1", "가"), candidate("2", "나"), candidate("3", "다")).toShareText(day)
        val lines = text.lines()

        assertEquals("널스메이트 · 알약 식별 결과", lines[0])
        assertEquals("2026년 9월 3일", lines[1])
        assertEquals("식별된 알약 3개", lines[3])
    }
}
