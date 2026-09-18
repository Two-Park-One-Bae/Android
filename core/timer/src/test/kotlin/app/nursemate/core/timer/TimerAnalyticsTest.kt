package app.nursemate.core.timer

import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.ORIGIN_WATCH
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerRecord
import app.nursemate.core.model.TimerReplica
import app.nursemate.core.model.TimerState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 타이머 지표의 **집계 지점**을 고정한다.
 *
 * 끝나는 경로가 넷(목록 [정지] · 전체화면 알람 [완료] · 알림 액션 · 워치)이라 한 곳이라도
 * 새면 완료율이 조용히 낮게 나온다. 반대로 워치와 폰이 같은 사건을 둘 다 세면 두 배가 된다.
 * 둘 다 화면을 눌러 보는 것으로는 안 드러나서 여기서 잡는다.
 */
class TimerAnalyticsTest {

    private class FakeScheduler : TimerAlarmScheduler {
        override fun schedule(timer: CareTimer) = Unit
        override fun cancel(timerId: String) = Unit
        override fun dismiss(timerId: String) = Unit
        override fun dismissAllAlarms() = Unit
    }

    private data class Started(val timer: CareTimer, val source: String)
    private data class Ended(val timer: CareTimer, val completed: Boolean, val elapsed: Int, val remaining: Int)

    private class Recorder : TimerAnalytics {
        val started = mutableListOf<Started>()
        val ended = mutableListOf<Ended>()

        override fun started(timer: CareTimer, source: String) {
            started += Started(timer, source)
        }

        override fun ended(timer: CareTimer, completed: Boolean, elapsedSec: Int, remainingSec: Int) {
            ended += Ended(timer, completed, elapsedSec, remainingSec)
        }
    }

    private val preset = TimerPreset(
        id = "p1",
        label = "수혈 바이탈",
        category = TimerCategory.TREATMENT,
        durationSeconds = 900,
        sortOrder = 0
    )

    private var now = 1_700_000_000_000L
    private val recorder = Recorder()
    private val store = FakeTimerStore()

    private fun repository() = TimerRepository(store, FakeScheduler(), { now }, recorder)

    @Test
    fun `시작하면 어디서 눌렀는지와 함께 센다`() = runBlocking {
        repository().start(preset, TimerAnalytics.SOURCE_WIDGET)

        val started = recorder.started.single()
        assertEquals(TimerAnalytics.SOURCE_WIDGET, started.source)
        assertEquals("수혈 바이탈", started.timer.label)
        assertEquals(900, started.timer.durationSeconds)
    }

    @Test
    fun `source 를 안 주면 폰이다`() = runBlocking {
        repository().start(preset)
        assertEquals(TimerAnalytics.SOURCE_PHONE, recorder.started.single().source)
    }

    @Test
    fun `울린 뒤 지우면 완료다`() = runBlocking {
        val repo = repository()
        val timer = repo.start(preset)
        now += 900_000
        repo.markRinging(timer.id)
        repo.remove(timer.id)

        val ended = recorder.ended.single()
        assertTrue("울린 뒤 [완료]는 완료여야 한다", ended.completed)
        assertEquals(0, ended.remaining)
        assertEquals(900, ended.elapsed)
    }

    @Test
    fun `울리기 전에 지우면 취소이고 언제 껐는지가 남는다`() = runBlocking {
        val repo = repository()
        val timer = repo.start(preset)
        now += 120_000 // 2분 쓰다 껐다
        repo.remove(timer.id)

        val ended = recorder.ended.single()
        assertTrue("울리기 전 정지는 취소여야 한다", !ended.completed)
        assertEquals(120, ended.elapsed)
        assertEquals(780, ended.remaining)
    }

    @Test
    fun `없는 타이머를 지우면 아무것도 안 센다`() = runBlocking {
        repository().remove("없는id")
        assertTrue(recorder.ended.isEmpty())
    }

    @Test
    fun `워치가 시작한 타이머는 복제본으로 들어올 때 센다`() = runBlocking {
        // 워치 앱은 지표를 안 보낸다(TimerAnalytics.None). 폰이 여기서 대신 센다.
        val repo = repository()
        val incoming = CareTimer(
            id = "w1",
            label = "항생제",
            category = TimerCategory.MEDICATION,
            durationSeconds = 1_800,
            endAtEpochMillis = now + 1_800_000
        )
        repo.mergeReplica(
            TimerReplica(ORIGIN_WATCH, records = listOf(TimerRecord("w1", 1, ORIGIN_WATCH, incoming)))
        )

        val started = recorder.started.single()
        assertEquals(TimerAnalytics.SOURCE_WATCH, started.source)
        assertEquals("항생제", started.timer.label)
    }

    @Test
    fun `워치가 끝낸 타이머도 복제본으로 빠질 때 센다`() = runBlocking {
        val repo = repository()
        val timer = repo.start(preset)
        recorder.started.clear()

        now += 60_000
        // 상대가 지웠다는 자리표(tombstone). 그냥 빼면 "저쪽에 없으니 보내주자"로 읽혀 되살아난다.
        repo.mergeReplica(tombstoneOf(timer.id))

        val ended = recorder.ended.single()
        assertTrue(!ended.completed)
        assertEquals(60, ended.elapsed)
        assertEquals(timer.id, ended.timer.id)
    }

    @Test
    fun `내가 끝낸 것이 복제본으로 되돌아와도 두 번 세지 않는다`() = runBlocking {
        // 폰이 지우면 복제본이 워치로 갔다가 되돌아온다. 그때 또 세면 완료가 두 배가 된다.
        val repo = repository()
        val timer = repo.start(preset)
        now += 30_000
        repo.remove(timer.id)
        assertEquals(1, recorder.ended.size)

        repo.mergeReplica(tombstoneOf(timer.id))
        assertEquals("되돌아온 복제본이 한 번 더 셌다", 1, recorder.ended.size)
    }

    @Test
    fun `이미 아는 타이머가 갱신돼도 시작으로 세지 않는다`() = runBlocking {
        // 일시정지·재개·[+1분]이 복제본으로 오가는데, 그때마다 시작으로 세면 시작 수가 부푼다.
        val repo = repository()
        val timer = repo.start(preset)
        recorder.started.clear()

        val paused = timer.copy(state = TimerState.PAUSED, remainingSeconds = 500)
        repo.mergeReplica(
            TimerReplica(ORIGIN_WATCH, records = listOf(TimerRecord(timer.id, 9, ORIGIN_WATCH, paused)))
        )
        assertTrue(recorder.started.isEmpty())
    }

    /** 상대가 지웠다고 알리는 빈 기록. */
    private fun tombstoneOf(id: String) = TimerReplica(
        origin = ORIGIN_WATCH,
        records = listOf(TimerRecord(id, rev = 9, origin = ORIGIN_WATCH, removedAt = now, removedSeq = 1))
    )
}
