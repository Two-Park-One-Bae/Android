package app.nursemate.timer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 시간 표기 고정 — 정본 `타이머 / C1 리스트` · `C3 프리셋 시트`. */
class TimerFormatTest {

    @Test
    fun `남은 시간은 한 시간을 넘을 때만 시를 붙인다`() {
        assertEquals("12:34", formatRemaining(754))
        assertEquals("00:00", formatRemaining(0))
        assertEquals("59:59", formatRemaining(3599))
        assertEquals("1:12:40", formatRemaining(4360))
    }

    @Test
    fun `남은 시간이 음수여도 0으로 보여 준다`() {
        // 만료 직후 화면이 한 틱 늦을 수 있다. 그때 `-00:01` 이 보이면 안 된다.
        assertEquals("00:00", formatRemaining(-5))
    }

    @Test
    fun `전체 시간은 0인 단위를 뺀다`() {
        assertEquals("15분", formatDuration(15 * 60))
        assertEquals("2시간", formatDuration(2 * 60 * 60))
        assertEquals("1시간 30분", formatDuration(90 * 60))
    }

    @Test
    fun `분이 안 되는 시간은 초로 쓴다`() {
        // 분으로만 계산하면 `0분` 이 돼 무엇을 맞춰 놨는지 알 수 없다.
        assertEquals("15초", formatDuration(15))
        assertEquals("1분 30초", formatDuration(90))
        assertEquals("1시간 1분 1초", formatDuration(3661))
        assertEquals("0초", formatDuration(0))
    }

    @Test
    fun `헤더 부제는 0인 항목을 뺀다`() {
        assertEquals("진행 중 2 · 일시정지 1 · 종료 1", timerCountLabel(running = 2, paused = 1, ringing = 1))
        assertEquals("진행 중 1", timerCountLabel(running = 1, paused = 0, ringing = 0))
        assertEquals("", timerCountLabel(running = 0, paused = 0, ringing = 0))
    }
}
