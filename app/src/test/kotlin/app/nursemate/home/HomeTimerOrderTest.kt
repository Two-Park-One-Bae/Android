package app.nursemate.home

import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

/** spec home §진행 중인 타이머 카드 — 만료 → 진행 중(남은 시간이 짧은 순) → 일시정지. */
class HomeTimerOrderTest {

    private val now = 1_000_000_000L

    private fun timer(id: String, state: TimerState, remainingSec: Int) = CareTimer(
        id = id,
        label = id,
        category = TimerCategory.TREATMENT,
        durationSeconds = 3_600,
        endAtEpochMillis = now + remainingSec * 1_000L,
        remainingSeconds = if (state == TimerState.PAUSED) remainingSec else null,
        state = state
    )

    @Test
    fun `만료가 먼저, 진행 중은 남은 시간이 짧은 순, 일시정지가 끝`() {
        val timers = listOf(
            timer("paused", TimerState.PAUSED, 10),
            timer("long", TimerState.RUNNING, 1_200),
            timer("ringing", TimerState.RINGING, 0),
            timer("short", TimerState.RUNNING, 60)
        )
        assertEquals(listOf("ringing", "short", "long", "paused"), homeTimerOrder(timers, now).map { it.id })
    }

    @Test
    fun `끝 시각을 지난 진행 중 타이머는 아직 울림 전이어도 만료로 앞에 선다`() {
        val timers = listOf(
            timer("running", TimerState.RUNNING, 30),
            timer("overdue", TimerState.RUNNING, -5)
        )
        assertEquals(listOf("overdue", "running"), homeTimerOrder(timers, now).map { it.id })
    }

    @Test
    fun `일시정지는 남은 시간이 짧아도 진행 중 뒤에 선다`() {
        val timers = listOf(
            timer("paused", TimerState.PAUSED, 1),
            timer("running", TimerState.RUNNING, 3_000)
        )
        assertEquals(listOf("running", "paused"), homeTimerOrder(timers, now).map { it.id })
    }
}
