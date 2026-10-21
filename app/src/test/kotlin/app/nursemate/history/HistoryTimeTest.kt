package app.nursemate.history

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** spec §식별 기록 · home §최근 식별 카드 의 시각 표기. */
class HistoryTimeTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 9)

    /** 2026-10-09 14:14 KST 는 UTC 05:14 다. */
    private fun at(raw: String) = parseIdentifiedAt(raw, seoul)!!

    @Test
    fun `목록 항목은 오전 오후 시각`() {
        assertEquals("오후 2:14", listTime(at("2026-10-09T05:14:00Z")))
        assertEquals("오전 9:02", listTime(at("2026-10-09T00:02:00Z")))
    }

    @Test
    fun `날짜 묶음은 오늘 어제 그리고 요일을 붙인 날짜`() {
        assertEquals("오늘", dateGroupLabel(today, today))
        assertEquals("어제", dateGroupLabel(today.minusDays(1), today))
        assertEquals("10월 7일 (수)", dateGroupLabel(LocalDate.of(2026, 10, 7), today))
        assertEquals("2025년 10월 7일 (화)", dateGroupLabel(LocalDate.of(2025, 10, 7), today))
    }

    @Test
    fun `기록 상세는 날짜 요일 시각`() {
        assertEquals("10월 9일 (금) 오후 2:14", detailTime(at("2026-10-09T05:14:00Z"), today))
        assertEquals("2025년 12월 31일 (수) 오후 11:00", detailTime(at("2025-12-31T14:00:00Z"), today))
    }

    @Test
    fun `홈은 오늘 시각 어제 시각 그 전 날짜`() {
        assertEquals("오후 2:14", homeTime(at("2026-10-09T05:14:00Z"), today))
        assertEquals("어제 오후 6:40", homeTime(at("2026-10-08T09:40:00Z"), today))
        assertEquals("10월 7일", homeTime(at("2026-10-07T01:00:00Z"), today))
        assertEquals("2025년 10월 7일", homeTime(at("2025-10-07T01:00:00Z"), today))
    }

    @Test
    fun `날짜는 기기 시간대로 나눈다`() {
        // UTC 로는 10월 8일 16:30 이지만 서울에서는 10월 9일 오전 1:30 — 오늘이다.
        assertEquals(today, at("2026-10-08T16:30:00Z").toLocalDate())
    }

    @Test
    fun `알약 수로 외 N 을 붙인다`() {
        assertEquals("타이레놀정 500mg 외 3", pillsTitle("타이레놀정 500mg", 4))
        assertEquals("게보린정", pillsTitle("게보린정", 1))
    }

    @Test
    fun `오프셋 시각과 소수 초도 읽는다`() {
        assertEquals("오후 2:14", listTime(at("2026-10-09T14:14:00+09:00")))
        assertEquals("오후 2:14", listTime(at("2026-10-09T05:14:00.123456Z")))
    }

    @Test
    fun `읽지 못하는 시각은 null`() {
        assertNull(parseIdentifiedAt("어제", seoul))
    }
}
