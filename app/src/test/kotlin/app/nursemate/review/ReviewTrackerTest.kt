package app.nursemate.review

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.nursemate.review.ReviewPolicy.DAY_MS
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 기기에 쌓는 사용 기록 — NM-561. 날짜는 UTC 로 고정해 본다. */
class ReviewTrackerTest {

    private val dir: File = Files.createTempDirectory("review").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracker = ReviewTracker(
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "review_stats.preferences_pb") },
        ZoneOffset.UTC
    )
    private val day = 20_000L
    private fun at(d: Long, hour: Int = 9) = d * DAY_MS + hour * 60L * 60 * 1000

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `쓴 날을 모으고 첫 사용은 가장 이른 시각이다`() = runBlocking {
        tracker.used(at(day))
        tracker.used(at(day, hour = 20))
        // 워치에서 어제 시작한 타이머가 오늘 넘어왔다 — 실제로 쓴 날로 센다
        tracker.timerStarted(at(day - 1))

        val stats = tracker.snapshot()
        assertEquals(setOf(day - 1, day), stats.usedDays)
        assertEquals(at(day - 1), stats.firstUseAt)
        assertEquals(1, stats.timerStartCount)
    }

    @Test
    fun `쓴 날은 2주만 남긴다`() = runBlocking {
        tracker.used(at(day - 20))
        tracker.used(at(day - 13))
        tracker.used(at(day))

        assertEquals(setOf(day - 13, day), tracker.snapshot().usedDays)
    }

    @Test
    fun `판정은 기록을 바꾸지 않고 요청했을 때만 간격을 적는다`() = runBlocking {
        repeat(3) { tracker.identified() }
        tracker.used(at(day - 3))

        assertEquals(1, tracker.decide(ReviewTrigger.IDENTIFIED, unmodified = true, nowMs = at(day)))
        assertNull("판정만으로는 간격을 쓰지 않는다", tracker.snapshot().lastRequestAt)

        tracker.requested(at(day))
        assertNull(tracker.decide(ReviewTrigger.IDENTIFIED, unmodified = true, nowMs = at(day + 1)))
    }

    @Test
    fun `앱 밖에서 끝낸 표시는 한 번만 꺼내진다`() = runBlocking {
        assertFalse(tracker.consumeEndedOutside())
        tracker.timerEndedOutside()

        assertTrue(tracker.consumeEndedOutside())
        assertFalse(tracker.consumeEndedOutside())
    }
}
