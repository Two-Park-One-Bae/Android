package app.nursemate.history

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 식별 기록의 시각 표기 — spec feature/pill-recognition §식별 기록 · feature/home §최근 식별 카드.

 | 자리 | 오늘 | 어제 | 그 전 | 올해가 아님 |
 |---|---|---|---|---|
 | 목록 항목 | `오후 2:14` | 같음 | 같음 | 같음 |
 | 목록 날짜 묶음 | `오늘` | `어제` | `10월 7일 (수)` | `2025년 10월 7일 (수)` |
 | 기록 상세 | `10월 9일 (금) 오후 2:14` | 같음 | 같음 | 앞에 연도 |
 | 홈 최근 식별 | `오후 2:14` | `어제 오후 6:40` | `10월 7일` | `2025년 10월 7일` |

 날짜는 **기기 시간대**로 나눈다(spec). 서버 시각은 ISO-8601 문자열로 온다.
*/

private val KOREAN = Locale.KOREAN
private val Clock = DateTimeFormatter.ofPattern("a h:mm", KOREAN)
private val MonthDay = DateTimeFormatter.ofPattern("M월 d일", KOREAN)
private val YearMonthDay = DateTimeFormatter.ofPattern("yyyy년 M월 d일", KOREAN)
private val DayOfWeek = DateTimeFormatter.ofPattern("E", KOREAN)

/**
 * 서버 시각 → 기기 시간대. `Z` 와 `+09:00` 같은 오프셋을 모두 읽는다. 읽지 못하면 null — 화면은 시각 자리를 비운다.
 */
fun parseIdentifiedAt(raw: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? =
    runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone) }.getOrNull()

/** 목록 항목 — `오후 2:14`. */
fun listTime(at: ZonedDateTime): String = at.format(Clock)

/** 목록 날짜 묶음 — `오늘` · `어제` · `10월 7일 (수)` · `2025년 10월 7일 (수)`. */
fun dateGroupLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "오늘"
    today.minusDays(1) -> "어제"
    else -> "${dayText(date, today)} (${date.format(DayOfWeek)})"
}

/** 기록 상세 — `10월 9일 (금) 오후 2:14`. 올해가 아니면 연도를 붙인다. */
fun detailTime(at: ZonedDateTime, today: LocalDate): String {
    val date = at.toLocalDate()
    return "${dayText(date, today)} (${date.format(DayOfWeek)}) ${at.format(Clock)}"
}

/** 홈 최근 식별 — `오후 2:14` · `어제 오후 6:40` · `10월 7일` · `2025년 10월 7일`. */
fun homeTime(at: ZonedDateTime, today: LocalDate): String {
    val date = at.toLocalDate()
    return when (date) {
        today -> at.format(Clock)
        today.minusDays(1) -> "어제 ${at.format(Clock)}"
        else -> dayText(date, today)
    }
}

/** 첫 품목명과 `외 N`. 알약이 하나면 품목명만. */
fun pillsTitle(firstPillName: String, pillCount: Int): String =
    if (pillCount > 1) "$firstPillName 외 ${pillCount - 1}" else firstPillName

private fun dayText(date: LocalDate, today: LocalDate): String =
    date.format(if (date.year == today.year) MonthDay else YearMonthDay)
