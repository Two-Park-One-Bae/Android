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

        // 링은 **남은 시간**만큼 찬다 — 시작에 가득, 만료에 빈다(정본 `sweepAngle -302`).
        assertEquals(1f, timer.ringFractionAt(t0))
        assertEquals(0f, timer.ringFractionAt(t0 + 900_000L))
        assertEquals(0f, timer.ringFractionAt(t0 + 999_999_999L), "만료 후에도 0 아래로 안 간다")
    }

    /**
     * spec 안에서 글과 그림이 갈렸을 때 **글을 따랐다** — 자세한 사정은
     * `docs/SPEC-FEEDBACK.md` 와 [CareTimer.alarmTitle] 주석에 있다.
     */
    @Test
    fun `알람 제목은 spec 의 대괄호 분류 표기를 따른다`() {
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
    fun `일시정지는 진행 중인 것보다 뒤에 선다`() {
        // 남은 시간만으로 세우면 **시간이 흐르는 것만으로 순서가 뒤집힌다.** 멈춰 있는
        // 15분짜리와 도는 10분짜리를 두면, 도는 쪽이 15분 아래로 내려가는 순간 앞뒤가 바뀐다.
        val paused = CareTimerTransitions.pause(CareTimerTransitions.start(preset, "paused", t0), t0)
        val running = CareTimerTransitions.start(preset.copy(durationSeconds = 600), "running", t0)

        // 뒤집힐 법한 시점(멈춘 900초 > 도는 590초)에도 순서가 그대로다.
        val early = CareTimerTransitions.ordered(listOf(paused, running), t0)
        val later = CareTimerTransitions.ordered(listOf(paused, running), t0 + 10_000L)

        assertEquals(listOf("running", "paused"), early.map { it.id })
        assertEquals(listOf("running", "paused"), later.map { it.id })
    }

    @Test
    fun `연장을 거듭해도 진행률이 어긋나지 않는다`() {
        // `durationSeconds` 와 `endAt` 을 함께 늘리지 않으면 링이 거꾸로 차거나 넘친다.
        var timer = CareTimerTransitions.start(preset, "t1", t0)
        repeat(3) { timer = CareTimerTransitions.extend(timer) }

        assertEquals(900 + 180, timer.durationSeconds)
        assertEquals(t0 + 1_080_000L, timer.endAtEpochMillis)
        assertEquals(1f, timer.ringFractionAt(t0))
        // 절반이 지났으면 절반만 찬다.
        assertEquals(0.5f, timer.ringFractionAt(t0 + 540_000L))
    }

    @Test
    fun `일시정지한 채 복원해도 남은 시간이 그대로다`() {
        // 앱이 죽었다 살아나도 멈춰 있던 것은 흐르면 안 된다.
        val paused = CareTimerTransitions.pause(
            CareTimerTransitions.start(preset, "t1", t0),
            t0 + 300_000L
        )

        val restored = CareTimerTransitions.restore(listOf(paused), t0 + 10_000_000L).single()

        assertEquals(TimerState.PAUSED, restored.state)
        assertEquals(600, restored.remainingSeconds)
    }

    @Test
    fun `재개하면 멈춰 있던 만큼만 남는다`() {
        val paused = CareTimerTransitions.pause(
            CareTimerTransitions.start(preset, "t1", t0),
            t0 + 300_000L
        )

        // 한참 멈춰 있다가 재개
        val resumeAt = t0 + 10_000_000L
        val resumed = CareTimerTransitions.resume(paused, resumeAt)

        assertEquals(TimerState.RUNNING, resumed.state)
        assertEquals(resumeAt + 600_000L, resumed.endAtEpochMillis)
        assertNull(resumed.remainingSeconds, "재개 뒤에는 endAt 만 쓴다")
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

    @Test
    fun `일시정지를 연장하면 순서가 바뀐다`() {
        // `extend` 가 PAUSED 에서는 `remainingSeconds` 만 바꾼다 — `state`·`endAt` 은 그대로다.
        // 그래도 정렬의 마지막 키가 남은 시간이라 순서는 뒤집힌다.
        val short = CareTimerTransitions.pause(
            CareTimerTransitions.start(preset.copy(durationSeconds = 600), "short", t0),
            t0
        )
        val long = CareTimerTransitions.pause(CareTimerTransitions.start(preset, "long", t0), t0)

        assertEquals(listOf("short", "long"), CareTimerTransitions.ordered(listOf(short, long), t0).map { it.id })

        // 짧은 쪽을 여섯 번 늘리면 960 초가 되어 long(900) 보다 뒤로 간다.
        // ⚠️ 다섯 번이면 정확히 900 이라 long 과 동점이 된다 — 안정 정렬이라 순서가
        //    그대로 남아, 시그니처를 되돌려도 통과하는 테스트가 된다.
        var extended = short
        repeat(6) { extended = CareTimerTransitions.extend(extended) }

        assertEquals(960, extended.remainingSeconds)
        assertEquals(TimerState.PAUSED, extended.state)
        assertEquals(short.endAtEpochMillis, extended.endAtEpochMillis)

        // ⚠️ 이 파일은 `kotlin.test` 라 메시지가 **뒤**에 온다(JUnit 과 반대다).
        assertEquals(
            listOf("long", "short"),
            CareTimerTransitions.ordered(listOf(extended, long), t0).map { it.id },
            "state·endAt 이 그대로라도 남은 시간이 바뀌면 순서가 뒤집힌다"
        )
    }

    @Test
    fun `링은 정본 프레임과 같은 각도로 찬다`() {
        // 정본 `DESIGN.pen` 의 `타이머 카드 — AST`: 15분 중 `12:34` 남은 상태를
        // `sweepAngle: -302` 로 그린다. 폰 C1·워치 W1·W2 세 프레임이 같은 값이다.
        //
        // 이 수치를 못박아 두는 이유는, 폰과 워치가 **반대로 돌았던 적이 있어서다** —
        // 한쪽은 뒤집어 쓰고 한쪽은 그대로 써서 실기기에서야 드러났다.
        val timer = CareTimer(
            id = "t1",
            label = "AST",
            category = TimerCategory.TEST,
            durationSeconds = 900,
            endAtEpochMillis = t0 + 900_000
        )
        val remaining754 = t0 + 900_000 - 754_000

        val degrees = timer.ringFractionAt(remaining754) * 360f

        assertEquals(302f, degrees, 1f, "정본 프레임의 링 각도와 다르다")
    }

    @Test
    fun `알람이 못 온 만료 타이머가 나중에 만료한 울림보다 위다`() {
        // 저장 상태를 RINGING 으로 올리는 건 알람 리시버 몫인데 못 오는 경우가 있다 —
        // 정확 알람 권한이 꺼졌거나 예약이 실패했거나. 그 타이머는 `RUNNING` 인 채 만료한다.
        //
        // ⚠️ **정렬을 먼저 하면 이 타이머가 진행 중 묶음에 남아**, 나중에 만료해 제대로
        // 울린 타이머보다 아래로 간다. "울리는 것끼리는 먼저 만료한 것이 위"라는 규칙이
        // 깨진다. 투영을 먼저 해야 둘이 같은 묶음에서 만료 시각으로 겨룬다.
        fun timer(id: String, endAt: Long, state: TimerState) = CareTimer(
            id = id,
            label = id,
            category = TimerCategory.TEST,
            durationSeconds = 900,
            endAtEpochMillis = endAt,
            state = state
        )
        // 오래전에 만료했지만 알람이 못 와서 아직 RUNNING 인 것
        val missed = timer("놓친것", t0 - 600_000, TimerState.RUNNING)
        // 방금 만료해 제대로 울리고 있는 것
        val ringing = timer("울리는것", t0 - 1_000, TimerState.RINGING)

        val result = CareTimerTransitions.projectedAndOrdered(
            listOf(ringing, missed),
            t0
        )

        assertEquals(listOf("놓친것", "울리는것"), result.map { it.id }, "오래 놓친 것이 위로 안 왔다")
        assertTrue(result.all { it.state == TimerState.RINGING })
    }
}
