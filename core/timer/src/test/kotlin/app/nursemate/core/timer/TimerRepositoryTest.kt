package app.nursemate.core.timer

import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.ORIGIN_WATCH
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerRecord
import app.nursemate.core.model.TimerReplica
import app.nursemate.core.model.TimerState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
        // 실제로 만료해야 울림으로 올라간다.
        now += 900_000L
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

    @Test
    fun `만료 직전에 연장하면 옛 알람이 발화해도 울리지 않는다`() = runBlocking {
        // [+1분] 은 `endAt` 만 밀 뿐, 이미 큐에 들어간 옛 알람은 그대로 발화한다.
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)

        now += 899_000L
        repo.extend(timer.id)

        // 옛 알람이 원래 만료 시각에 발화
        now += 1_000L
        repo.markRinging(timer.id)

        assertEquals(TimerState.RUNNING, store.savedTimers.single().state)
        assertEquals(now + 60_000L, scheduler.scheduledAt(timer.id))
    }

    @Test
    fun `진짜 만료했으면 울림으로 올린다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)

        now += 900_000L
        repo.markRinging(timer.id)

        assertEquals(TimerState.RINGING, store.savedTimers.single().state)
    }

    @Test
    fun `동시에 만료해도 둘 다 울림으로 올라간다`() = runBlocking {
        // 알람 리시버는 자기 코루틴에서 돌아 화면과 독립이다. 만료가 거의 같이 오면 리시버
        // 둘이 같은 목록을 읽고 각자 자기 것만 올려 쓰는데, 읽기-수정-쓰기를 따로 하면
        // **나중 쓰기가 앞의 울림을 지운다.** 읽기를 늦춰 그 창을 벌린다.
        val store = FakeTimerStore(readDelayMillis = 5)
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val a = repo.start(preset)
        val b = repo.start(preset)

        now += 900_000L
        coroutineScope {
            launch { repo.markRinging(a.id) }
            launch { repo.markRinging(b.id) }
        }

        assertEquals(
            "하나가 다른 하나의 울림을 덮어썼다",
            2,
            store.savedTimers.count { it.state == TimerState.RINGING }
        )
    }

    @Test
    fun `동시에 시작해도 서로를 지우지 않는다`() = runBlocking {
        val store = FakeTimerStore(readDelayMillis = 5)
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)

        coroutineScope {
            launch { repo.start(preset) }
            launch { repo.start(preset) }
        }

        assertEquals(2, store.savedTimers.size)
    }

    @Test
    fun `울리는 중에는 연장하지 않는다`() = runBlocking {
        // `endAt` 만 밀면 남은 시간이 양수가 되는데 상태는 RINGING 이라, 카드는 만료로
        // 그려지고 예약도 안 되는 어긋난 상태가 된다.
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)
        now += 900_000L
        repo.markRinging(timer.id)

        repo.extend(timer.id)

        val saved = store.savedTimers.single()
        assertEquals(TimerState.RINGING, saved.state)
        assertEquals(timer.endAtEpochMillis, saved.endAtEpochMillis)
    }

    @Test
    fun `복원할 때 남은 타이머가 있으면 알림을 걷지 않는다`() = runBlocking {
        // `cancelAll()` 은 채널을 가리지 않아, 앱이 죽어 있는 동안 울린 알람의 알림까지 지운다.
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        repo.start(preset)

        repo.restore()

        assertEquals("타이머가 남아 있는데 알림을 걷었다", 0, scheduler.alarmSweeps)
    }

    // ── 복제본을 합친 뒤의 예약 ────────────────────────────────────────

    @Test
    fun `상대가 만료를 먼저 알려도 내 예약을 취소하지 않는다`() = runBlocking {
        // ⚠️ 이걸 취소하면 **이 기기만 조용해진다.** 상대 소식이 내 알람보다 몇 백 밀리초
        // 먼저 닿는 일이 흔한데(양쪽이 같은 시각에 걸려 있으므로), 그때마다 안 울리게 된다.
        // 실기기에서 폰이 그렇게 안 울렸다.
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)

        repo.mergeReplica(
            TimerReplica(
                origin = ORIGIN_WATCH,
                records = listOf(
                    TimerRecord(
                        id = timer.id,
                        rev = 9,
                        origin = ORIGIN_WATCH,
                        timer = timer.copy(state = TimerState.RINGING)
                    )
                )
            )
        )

        assertEquals(now + 900_000L, scheduler.scheduledAt(timer.id))
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun `처음 보는 만료 타이머는 예약을 걸어 곧바로 울린다`() = runBlocking {
        // 끊겨 있는 동안 상대에서 만료한 것이 재연결 때 넘어오는 경우. 이 기기엔 예약이
        // 없으므로 걸어 준다 — `endAt` 이 지난 값이라 시스템이 즉시 발화한다.
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)

        val expired = CareTimer(
            id = "from-watch",
            label = "AST",
            category = TimerCategory.TEST,
            durationSeconds = 900,
            endAtEpochMillis = now - 1_000,
            state = TimerState.RINGING
        )
        repo.mergeReplica(
            TimerReplica(ORIGIN_WATCH, records = listOf(TimerRecord("from-watch", 1, ORIGIN_WATCH, expired)))
        )

        assertEquals(now - 1_000, scheduler.scheduledAt("from-watch"))
    }

    @Test
    fun `합쳐서 일시정지로 바뀌면 예약을 지운다`() = runBlocking {
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)
        val timer = repo.start(preset)

        repo.mergeReplica(
            TimerReplica(
                origin = ORIGIN_WATCH,
                records = listOf(
                    TimerRecord(timer.id, 9, ORIGIN_WATCH, timer.copy(state = TimerState.PAUSED))
                )
            )
        )

        assertTrue(scheduler.cancelled.contains(timer.id))
    }

    @Test
    fun `복원이 두 번 돌아도 고아 알림은 한 번만 걷는다`() = runBlocking {
        // 설치 직후 시스템이 `BOOT_COMPLETED` 를 함께 배달해 앱 시작과 부팅 리시버가
        // **동시에** 복원을 돌린다(실기기 확인). 걷는 것은 프로세스마다 한 번이면 된다 —
        // 두 번 걷으면 그 사이에 막 뜬 만료 알림을 지울 창이 두 배가 된다.
        val store = FakeTimerStore()
        val scheduler = FakeScheduler()
        val repo = repository(store, scheduler)

        repo.restore()
        repo.restore()

        assertEquals(1, scheduler.alarmSweeps)
    }
}
