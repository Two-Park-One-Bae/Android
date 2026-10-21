package app.nursemate.review

import app.nursemate.review.ReviewPolicy.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewPolicyTest {

    private val today = 20_000L
    private val now = today * DAY_MS + 12 * 60 * 60 * 1000

    /** 묶음 1 을 딱 채운 기록. 하나씩 빼 가며 본다. */
    private val identifyReady = ReviewStats(identifyCount = 3, firstUseAt = now - 3 * DAY_MS)

    private fun identify(stats: ReviewStats, unmodified: Boolean = true) =
        ReviewPolicy.bundleFor(stats, ReviewTrigger.IDENTIFIED, now, today, unmodified)

    private fun timerEnded(stats: ReviewStats) = ReviewPolicy.bundleFor(stats, ReviewTrigger.TIMER_ENDED, now, today)

    @Test
    fun `묶음 1 — 식별 3회 · 첫 사용 3일 · 수정 없음 · 한도 안내 안 봄`() {
        assertEquals(1, identify(identifyReady))
    }

    @Test
    fun `묶음 1 — 하나라도 모자라면 요청하지 않는다`() {
        assertNull(identify(identifyReady.copy(identifyCount = 2)))
        assertNull(identify(identifyReady.copy(firstUseAt = now - 3 * DAY_MS + 1)))
        assertNull(identify(identifyReady, unmodified = false))
        assertNull(identify(identifyReady.copy(limitShownDay = today)))
        // 어제 본 한도 안내는 상관없다
        assertEquals(1, identify(identifyReady.copy(limitShownDay = today - 1)))
    }

    @Test
    fun `마지막 요청 뒤 30일이 안 됐으면 어느 묶음도 요청하지 않는다`() {
        assertNull(identify(identifyReady.copy(lastRequestAt = now - 30 * DAY_MS + 1)))
        assertEquals(1, identify(identifyReady.copy(lastRequestAt = now - 30 * DAY_MS)))
    }

    @Test
    fun `묶음 2 — 최근 7일 중 3일 사용 · 식별과 타이머를 합쳐 3회`() {
        val stats = ReviewStats(identifyCount = 1, timerStartCount = 2, usedDays = setOf(today, today - 3, today - 6))
        assertEquals(2, identify(stats, unmodified = false))
        assertEquals(2, timerEnded(stats))
    }

    @Test
    fun `묶음 2 — 7일 밖의 날은 세지 않는다`() {
        val stats = ReviewStats(identifyCount = 3, usedDays = setOf(today, today - 3, today - 7))
        assertNull(identify(stats, unmodified = false))
    }

    @Test
    fun `묶음 3 — 타이머를 끝냈을 때만 · 첫 사용 3일 · 타이머 5회`() {
        val stats = ReviewStats(timerStartCount = 5, firstUseAt = now - 3 * DAY_MS)
        assertEquals(3, timerEnded(stats))
        assertNull(identify(stats))
        assertNull(timerEnded(stats.copy(timerStartCount = 4)))
    }

    @Test
    fun `아무것도 없으면 요청하지 않는다`() {
        assertNull(identify(ReviewStats()))
        assertNull(timerEnded(ReviewStats()))
    }
}
