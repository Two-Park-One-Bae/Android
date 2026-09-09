package app.nursemate.core.data.timer

import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저장과 알람 예약이 **항상 함께** 움직이는지 고정한다.
 *
 * 이 둘이 갈라지면 "앱에는 멈춰 있는데 알람은 울린다" 같은 상태가 생긴다. 병동에서 처치
 * 시각을 다루는 기능이라 그 어긋남이 제일 위험해서, 전이 규칙([CareTimerTransitionsTest])과
 * 별개로 여기서 한 번 더 잡는다.
 */
class TimerRepositoryTest {

    /** 예약된 알람을 기억하는 가짜. 실제 AlarmManager 는 `:app` 구현이다. */
    private class FakeScheduler : TimerAlarmScheduler {
        val scheduled = linkedMapOf<String, Long>()
        val cancelled = mutableListOf<String>()
        val dismissed = mutableListOf<String>()
        var alarmSweeps = 0

        override fun schedule(timer: CareTimer) {
            scheduled[timer.id] = timer.endAtEpochMillis
        }

        /** 예약이 없으면 명확히 터진다 — null 비교로 조용히 통과하는 걸 막는다. */
        fun scheduledAt(timerId: String): Long = scheduled.getValue(timerId)

        override fun dismissAllAlarms() {
            alarmSweeps++
        }

        override fun cancel(timerId: String) {
            scheduled.remove(timerId)
            cancelled += timerId
        }

        override fun dismiss(timerId: String) {
            scheduled.remove(timerId)
            dismissed += timerId
        }
    }

    private val preset = TimerPreset(
        id = "p1",
        label = "AST",
        category = TimerCategory.TEST,
        durationSeconds = 900,
        sortOrder = 0
    )

    private var now = 1_700_000_000_000L

    private fun repository(store: FakeTimerStore, scheduler: FakeScheduler) = TimerRepository(store, scheduler, { now })

    @Test
    fun `시작하면 저장과 예약이 함께 일어난다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)

        val timer = repo.start(preset)

        assertEquals(listOf(timer.id), store.savedTimers.map { it.id })
        assertEquals(now + 900_000L, scheduler.scheduledAt(timer.id))
    }

    @Test
    fun `일시정지하면 예약이 지워진다 — 멈췄는데 울리면 안 된다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)

        now += 300_000L
        repo.pause(timer.id)

        assertTrue("일시정지 중에는 예약이 남아 있으면 안 된다", scheduler.scheduled.isEmpty())
        assertEquals(TimerState.PAUSED, store.savedTimers.single().state)
        assertEquals(600, store.savedTimers.single().remainingSeconds)
    }

    @Test
    fun `재개하면 새 만료 시각으로 다시 예약된다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)
        now += 300_000L
        repo.pause(timer.id)

        now += 10_000_000L
        repo.resume(timer.id)

        assertEquals("멈춰 있던 만큼 뒤로 밀려야 한다", now + 600_000L, scheduler.scheduledAt(timer.id))
    }

    @Test
    fun `플러스 1분은 예약을 새 시각으로 덮어쓴다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)
        val before = scheduler.scheduledAt(timer.id)

        repo.extend(timer.id)

        assertEquals(before + 60_000L, scheduler.scheduledAt(timer.id))
    }

    @Test
    fun `정지하면 목록과 예약에서 모두 사라진다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)

        repo.remove(timer.id)

        assertTrue(store.savedTimers.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(listOf(timer.id), scheduler.cancelled)
    }

    @Test
    fun `울리는 중에 완료하면 알람도 함께 꺼진다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)
        repo.markRinging(timer.id)

        repo.remove(timer.id)

        assertEquals("cancel 이 아니라 dismiss 여야 한다", listOf(timer.id), scheduler.dismissed)
        assertTrue(store.savedTimers.isEmpty())
    }

    @Test
    fun `복원은 만료된 것을 울림으로 올리고 다시 예약하지 않는다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)
        scheduler.scheduled.clear() // 재부팅으로 예약이 비었다고 가정

        now += 900_001L
        repo.restore()

        assertEquals(TimerState.RINGING, store.savedTimers.single().state)
        assertTrue("이미 놓친 알람을 다시 예약하지 않는다", scheduler.scheduled.isEmpty())
    }

    @Test
    fun `복원은 아직 안 끝난 타이머의 예약을 되살린다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)
        scheduler.scheduled.clear() // 재부팅으로 AlarmManager 가 비었다

        now += 100_000L
        repo.restore()

        assertEquals(timer.endAtEpochMillis, scheduler.scheduledAt(timer.id))
    }

    @Test
    fun `프리셋 삭제 후 sortOrder 에 구멍이 남지 않는다`() = runBlocking {
        val store = FakeTimerStore()
        store.savedPresets = listOf(
            preset.copy(id = "a", sortOrder = 0),
            preset.copy(id = "b", sortOrder = 1),
            preset.copy(id = "c", sortOrder = 2)
        )
        val repo = TimerPresetRepository(store)

        repo.delete("b")

        assertEquals(listOf("a", "c"), store.savedPresets.map { it.id })
        assertEquals(listOf(0, 1), store.savedPresets.map { it.sortOrder })
    }

    @Test
    fun `없는 타이머를 건드려도 저장소는 그대로다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)

        repo.pause("없음")
        repo.resume("없음")
        repo.extend("없음")

        assertTrue(store.savedTimers.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `타이머가 이미 없어도 알림을 걷는다`() = runBlocking {
        // 화면에서 먼저 지웠거나 앱 데이터가 비워진 뒤 알림의 [완료] 를 누르는 경우다.
        // 예전에는 목록에서 못 찾으면 곧바로 돌아서서, 스와이프가 막힌 알림이 고아로 남았다.
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)

        repo.remove("없는-타이머")

        assertTrue("알림을 걷지 않았다", scheduler.cancelled.contains("없는-타이머"))
    }

    @Test
    fun `복원할 때 남아 있는 알림을 걷는다`() = runBlocking {
        // 재설치·데이터 삭제로 타이머만 사라지면 알림이 고아가 되는데, 스와이프가 막혀 있어
        // 사용자가 지울 방법이 없다.
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)

        repo.restore()

        assertEquals(1, scheduler.alarmSweeps)
    }
}
