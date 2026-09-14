package app.nursemate.timer

import org.junit.Assert.assertEquals
import org.junit.Test

/** C1 헤더 부제 — 폰 전용 표기라 `core:model` 로 올리지 않았다(워치에는 이 자리가 없다). */
class TimerCountLabelTest {

    @Test
    fun `헤더 부제는 0인 항목을 뺀다`() {
        assertEquals("진행 중 2 · 일시정지 1 · 종료 1", timerCountLabel(running = 2, paused = 1, ringing = 1))
        assertEquals("진행 중 1", timerCountLabel(running = 1, paused = 0, ringing = 0))
        assertEquals("", timerCountLabel(running = 0, paused = 0, ringing = 0))
    }
}
