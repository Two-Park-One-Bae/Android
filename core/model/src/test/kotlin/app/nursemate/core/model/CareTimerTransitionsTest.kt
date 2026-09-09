package app.nursemate.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 상태 전이 규칙 고정 — 정본 `spec/feature/care-timer/domain-model.md`.
 *
 * 시각을 인자로 받는 순수 함수라 실제 시계 없이 검증한다.
 */
class CareTimerTransitionsTest {

    private val t0 = 1_700_000_000_000L
    private val preset = TimerPreset(
        id = "p1",
        label = "AST",
        category = TimerCategory.TEST,
        durationSeconds = 900,
        sortOrder = 0
    )

    @Test
    fun `프리셋 원탭은 즉시 RUNNING 으로 시작한다`() {
        val timer = CareTimerTransitions.start(preset, "t1", t0)

        assertEquals(TimerState.RUNNING, timer.state)
        assertEquals(t0 + 900_000L, timer.endAtEpochMillis)
        assertEquals(900, timer.remainingAt(t0))
        assertNull(timer.remainingSeconds, "실행 중에는 remaining 을 쓰지 않는다")
    }

    @Test
    fun `실행 값이 프리셋에서 복사된다 — 이후 프리셋을 고쳐도 영향 없다`() {
        val timer = CareTimerTransitions.start(preset, "t1", t0)

        assertEquals("AST", timer.label)
        assertEquals(TimerCategory.TEST, timer.category)
        assertEquals(900, timer.durationSeconds)
    }

    @Test
    fun `일시정지는 남은 시간을 고정한다`() {
        val timer = CareTimerTransitions.start(preset, "t1", t0)

        // 5분 경과 후 정지 → 600초 남음
        val paused = CareTimerTransitions.pause(timer, t0 + 300_000L)

        assertEquals(TimerState.PAUSED, paused.state)
        assertEquals(600, paused.remainingSeconds)
    }

    @Test
    fun `일시정지 중에는 시간이 흐르지 않는다`() {
        val paused = CareTimerTransitions.pause(
            CareTimerTransitions.start(preset, "t1", t0),
            t0 + 300_000L
        )

        // 한참 지나도 남은 시간은 그대로다
        assertEquals(600, paused.remainingAt(t0 + 999_999_999L))
    }

    @Test
    fun `재개하면 멈춘 시점의 남은 시간으로 만료 시각을 다시 잡는다`() {
        val paused = CareTimerTransitions.pause(
            CareTimerTransitions.start(preset, "t1", t0),
            t0 + 300_000L
        )

        val resumedAt = t0 + 10_000_000L
        val resumed = CareTimerTransitions.resume(paused, resumedAt)

        assertEquals(TimerState.RUNNING, resumed.state)
        assertEquals(resumedAt + 600_000L, resumed.endAtEpochMillis)
        assertNull(resumed.remainingSeconds, "재개하면 remaining 을 비운다")
    }

    @Test
    fun `울리는 중에는 일시정지되지 않는다 — 멈추는 방법은 완료뿐이다`() {
        val ringing = CareTimerTransitions.ring(CareTimerTransitions.start(preset, "t1", t0))

        assertEquals(ringing, CareTimerTransitions.pause(ringing, t0))
    }

    @Test
    fun `앱이 꺼져 있던 동안 만료했으면 다시 열 때 RINGING 으로 복원된다`() {
        val timer = CareTimerTransitions.start(preset, "t1", t0)

        val restored = CareTimerTransitions.restore(listOf(timer), t0 + 900_001L)

        assertEquals(TimerState.RINGING, restored.single().state)
    }

    @Test
    fun `일시정지된 타이머는 시간이 아무리 지나도 복원 시 울리지 않는다`() {
        val paused = CareTimerTransitions.pause(
            CareTimerTransitions.start(preset, "t1", t0),
            t0 + 300_000L
        )

        val restored = CareTimerTransitions.restore(listOf(paused), t0 + 999_999_999L)

        assertEquals(TimerState.PAUSED, restored.single().state)
    }

    @Test
    fun `플러스 1분은 남은 시간과 전체 시간을 함께 늘린다`() {
        val timer = CareTimerTransitions.start(preset, "t1", t0)

        val extended = CareTimerTransitions.extend(timer)

        assertEquals(960, extended.durationSeconds, "전체를 안 늘리면 진행률이 100%를 넘는다")
        assertEquals(960, extended.remainingAt(t0))
    }

    @Test
    fun `일시정지 중 플러스 1분은 고정된 남은 시간을 늘린다`() {
        val paused = CareTimerTransitions.pause(
            CareTimerTransitions.start(preset, "t1", t0),
            t0 + 300_000L
        )

        val extended = CareTimerTransitions.extend(paused)

        assertEquals(660, extended.remainingSeconds)
        assertEquals(660, extended.remainingAt(t0 + 999_999L))
    }

    @Test
    fun `진행률은 0에서 1 사이로 잘린다`() {
        val timer = CareTimerTransitions.start(preset, "t1", t0)

        assertEquals(0f, timer.progressAt(t0))
        assertEquals(1f, timer.progressAt(t0 + 900_000L))
        assertEquals(1f, timer.progressAt(t0 + 999_999_999L), "만료 후에도 1을 넘지 않는다")
    }

    @Test
    fun `알람 제목은 정본 형식을 따른다`() {
        val timer = CareTimerTransitions.start(
            preset.copy(label = "수혈 바이탈", category = TimerCategory.TREATMENT),
            "t1",
            t0
        )

        assertEquals("❗ [처치] 수혈 바이탈", timer.alarmTitle)
    }

    @Test
    fun `드래그 정렬은 sortOrder 를 0부터 다시 매긴다`() {
        val presets = listOf(
            preset.copy(id = "a", sortOrder = 5),
            preset.copy(id = "b", sortOrder = 2),
            preset.copy(id = "c", sortOrder = 9)
        )

        val reordered = CareTimerTransitions.reorder(presets)

        assertEquals(listOf(0, 1, 2), reordered.map { it.sortOrder })
        assertEquals(listOf("a", "b", "c"), reordered.map { it.id }, "담긴 순서를 그대로 쓴다")
    }

    @Test
    fun `노출은 sortOrder 오름차순이다`() {
        val presets = listOf(
            preset.copy(id = "a", sortOrder = 2),
            preset.copy(id = "b", sortOrder = 0),
            preset.copy(id = "c", sortOrder = 1)
        )

        assertEquals(listOf("b", "c", "a"), CareTimerTransitions.sorted(presets).map { it.id })
    }

    @Test
    fun `리스트는 울리는 것을 맨 위에 두고 나머지는 남은 시간 순으로 세운다`() {
        val running = CareTimerTransitions.start(preset, "running", t0)
        val soon = CareTimerTransitions.start(preset.copy(durationSeconds = 60), "soon", t0)
        val ringing = CareTimerTransitions.ring(CareTimerTransitions.start(preset, "ringing", t0))

        val ordered = CareTimerTransitions.ordered(listOf(running, soon, ringing), t0)

        assertEquals(listOf("ringing", "soon", "running"), ordered.map { it.id })
    }

    @Test
    fun `울리는 것끼리는 먼저 만료한 것이 위다`() {
        // 남은 시간은 전부 0 이라 순서를 가르지 못한다. 오래 놓친 것이 더 급하다.
        val old = CareTimerTransitions.ring(CareTimerTransitions.start(preset, "old", t0))
        val recent = CareTimerTransitions.ring(
            CareTimerTransitions.start(preset, "recent", t0 + 60_000L)
        )
        val now = t0 + 2_000_000L

        val ordered = CareTimerTransitions.ordered(listOf(recent, old), now)

        assertEquals(listOf("old", "recent"), ordered.map { it.id })
    }

    @Test
    fun `만료에서 얼마나 지났는지 센다`() {
        val timer = CareTimerTransitions.start(preset, "t1", t0)

        // 15분 타이머를 18분 12초 뒤에 보면 3분 12초 지났다.
        assertEquals(192, timer.overdueAt(t0 + 1_092_000L))
        // 아직 안 지났으면 0 이다 — 음수를 그대로 흘리면 화면이 `--03:12` 처럼 된다.
        assertEquals(0, timer.overdueAt(t0))
    }

    @Test
    fun `일시정지는 멈춘 남은 시간으로 줄을 선다`() {
        // 15분짜리를 바로 정지 → 900초 고정. 실행 중인 10분짜리보다 뒤에 서야 한다.
        val paused = CareTimerTransitions.pause(CareTimerTransitions.start(preset, "paused", t0), t0)
        val running = CareTimerTransitions.start(preset.copy(durationSeconds = 600), "running", t0)

        val ordered = CareTimerTransitions.ordered(listOf(paused, running), t0)

        assertEquals(listOf("running", "paused"), ordered.map { it.id })
    }

    @Test
    fun `기본 프리셋 6종이 정본 표와 일치한다`() {
        assertEquals(6, DEFAULT_TIMER_PRESETS.size)
        assertEquals(
            listOf("AST", "수혈 바이탈", "투약 반응 관찰", "해열 재검", "바이탈 재측정", "체위 변경"),
            DEFAULT_TIMER_PRESETS.map { it.label }
        )
        assertEquals(
            listOf(900, 900, 1800, 1800, 900, 7200),
            DEFAULT_TIMER_PRESETS.map { it.durationSeconds }
        )
        assertEquals(
            listOf(
                TimerCategory.TEST,
                TimerCategory.TREATMENT,
                TimerCategory.MEDICATION,
                TimerCategory.TEST,
                TimerCategory.TEST,
                TimerCategory.TREATMENT
            ),
            DEFAULT_TIMER_PRESETS.map { it.category }
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5), DEFAULT_TIMER_PRESETS.map { it.sortOrder })
        assertTrue(DEFAULT_TIMER_PRESETS.all { it.isDefault })
    }
}
