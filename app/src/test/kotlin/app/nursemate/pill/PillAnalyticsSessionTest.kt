package app.nursemate.pill

import app.nursemate.telemetry.AnalyticsEvent
import app.nursemate.telemetry.AppAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 세션 지표의 **셈법**을 고정한다.
 *
 * 여기서 틀리면 앱은 멀쩡히 돌고 숫자만 조용히 틀린다 — 성공률이 100%를 넘거나, 체류시간이
 * 백그라운드만큼 부풀거나, 새 사진에 앞 사진의 수정 횟수가 얹힌다. 화면을 눌러 보는 것으로는
 * 알 수 없는 종류의 오류라 테스트로 막는다.
 */
class PillAnalyticsSessionTest {

    /** 보낸 이벤트를 그대로 모아 두는 가짜. */
    private class Recorder : AppAnalytics {
        val sent = mutableListOf<AnalyticsEvent>()
        override fun track(event: AnalyticsEvent) {
            sent += event
        }
    }

    private var now = 0L
    private val recorder = Recorder()
    private val session = PillAnalyticsSession(recorder) { now }

    private inline fun <reified T : AnalyticsEvent> sentOf(): List<T> = recorder.sent.filterIsInstance<T>()

    // ─────────────────────────── 분석 ───────────────────────────

    @Test
    fun `한 시도에 결과는 한 번만 실린다`() {
        // 결과 판정이 detection·attributes 두 흐름을 함께 보고 돌아, 상태가 갱신될 때마다
        // 부르면 같은 결과가 여러 번 실린다.
        session.analysisStarted()
        now = 4_000
        session.analysisFinished(outcome = "success", pillCount = 3)
        session.analysisFinished(outcome = "success", pillCount = 3)
        session.analysisFinished(outcome = "failure", pillCount = 0)

        assertEquals(1, sentOf<AnalyticsEvent.PillIdentifyResult>().size)
    }

    @Test
    fun `시작하지 않았으면 결과도 안 보낸다`() {
        session.analysisFinished(outcome = "success", pillCount = 1)
        assertTrue(recorder.sent.isEmpty())
    }

    @Test
    fun `대기 시간은 시작부터 결과까지다`() {
        session.analysisStarted()
        now = 12_900
        session.analysisFinished(outcome = "success", pillCount = 25)

        val result = sentOf<AnalyticsEvent.PillIdentifyResult>().single()
        assertEquals(12_900L, result.analysisMs)
        assertEquals("00:10-00:15", result.params["analysis_bucket"])
    }

    @Test
    fun `버린 시도의 대기 시간이 다음 시도에 얹히지 않는다`() {
        // 429 로 버린 시도의 분모를 안 닫으면 다음 시도의 analysis_ms 가 이번 대기까지 합쳐진다.
        session.analysisStarted()
        now = 30_000
        session.analysisDiscarded()

        session.analysisStarted()
        now = 32_000
        session.analysisFinished(outcome = "success", pillCount = 1)

        assertEquals(2_000L, sentOf<AnalyticsEvent.PillIdentifyResult>().single().analysisMs)
    }

    @Test
    fun `한도 소진은 한 세션에 한 번만 센다`() {
        session.limitReached()
        session.limitReached()
        assertEquals(1, sentOf<AnalyticsEvent.PillLimitReached>().size)
    }

    // ─────────────────────── 체류시간 ───────────────────────

    @Test
    fun `체류시간은 화면이 떠 있던 구간만 더한다`() {
        // 30초 백그라운드가 얹히면 구간이 한 칸 밀린다(iOS 실측).
        session.enterEdit("1")
        now = 20_000
        session.leaveEdit("1") // 20초 봤다

        now = 50_000 // 백그라운드 30초 — 안 세야 한다

        session.enterEdit("1")
        now = 55_000
        session.confirmed(pillId = "1", pillIndex = 1, candidateIndex = 1)

        assertEquals(25_000L, sentOf<AnalyticsEvent.PillConfirm>().single().dwellMs)
    }

    @Test
    fun `다른 알약의 구간은 섞이지 않는다`() {
        session.enterEdit("1")
        now = 10_000
        session.leaveEdit("1")

        session.enterEdit("2")
        now = 40_000
        session.confirmed(pillId = "2", pillIndex = 2, candidateIndex = 1)

        assertEquals(30_000L, sentOf<AnalyticsEvent.PillConfirm>().single().dwellMs)
    }

    @Test
    fun `엉뚱한 알약으로 구간을 닫으면 무시한다`() {
        session.enterEdit("1")
        now = 10_000
        session.leaveEdit("2") // 다른 알약 — 열린 구간을 건드리면 안 된다

        now = 15_000
        session.confirmed(pillId = "1", pillIndex = 1, candidateIndex = 1)
        assertEquals(15_000L, sentOf<AnalyticsEvent.PillConfirm>().single().dwellMs)
    }

    // ─────────────────────── 수정·확정 ───────────────────────

    @Test
    fun `수정 안 하고 확정하면 edited_attrs 가 none 이다`() {
        // 빈 문자열은 GA4 에서 (not set) 이라 "안 고쳤다"와 "안 왔다"가 구분되지 않는다.
        session.confirmed(pillId = "1", pillIndex = 1, candidateIndex = 1)
        assertEquals("none", sentOf<AnalyticsEvent.PillConfirm>().single().editedAttrs)
    }

    @Test
    fun `수정한 속성은 중복 없이 사전순으로 실린다`() {
        session.attrEdited("1", 1, attribute = "shape", manual = false)
        session.attrEdited("1", 1, attribute = "color", manual = false)
        session.attrEdited("1", 1, attribute = "color", manual = false)
        session.confirmed(pillId = "1", pillIndex = 1, candidateIndex = 2)

        val confirm = sentOf<AnalyticsEvent.PillConfirm>().single()
        assertEquals("color,shape", confirm.editedAttrs)
        // 횟수는 중복도 센다 — "몇 번 만졌나"라 종류와 다른 값이다.
        assertEquals(3, confirm.editCount)
    }

    @Test
    fun `수동 추가 알약은 수정 이벤트를 안 보내지만 횟수는 센다`() {
        // 모델이 뽑은 값을 얼마나 고치는가를 보는 지표라 사람이 처음부터 넣는 건 뺀다.
        // 다만 확정에 실리는 edit_count 는 그 알약의 사실이라 그대로 센다.
        session.attrEdited("m1", 4, attribute = "color", manual = true)
        assertTrue(sentOf<AnalyticsEvent.PillAttrEdit>().isEmpty())

        session.confirmed(pillId = "m1", pillIndex = 4, candidateIndex = 1)
        assertEquals(1, sentOf<AnalyticsEvent.PillConfirm>().single().editCount)
    }

    @Test
    fun `수동 추가 알약은 이탈도 안 보낸다`() {
        session.flowExited("m1", 4, editingAttribute = "imprint", enteredValues = "none", manual = true)
        assertTrue(recorder.sent.isEmpty())
    }

    // ─────────────────────── 세션 종료 ───────────────────────

    @Test
    fun `결과 화면을 본 적 없으면 중도이탈로 세지 않는다`() {
        session.sessionExited(PillSessionSummary(3, 0, 3, 0, 0))
        assertTrue(recorder.sent.isEmpty())
    }

    @Test
    fun `중도이탈 경과는 결과 화면이 뜬 시점부터다`() {
        now = 5_000
        session.resultShown()
        now = 95_000
        session.sessionExited(PillSessionSummary(3, 1, 2, 0, 0))

        val exit = sentOf<AnalyticsEvent.PillIdentifySessionExit>().single()
        assertEquals(90L, exit.elapsedSec)
        assertEquals(2, exit.unconfirmedCount)
    }

    @Test
    fun `결과 화면 기준 시각은 처음 한 번만 잡는다`() {
        // 탭을 오갔다고 기준이 뒤로 밀리면 체류가 실제보다 짧게 나온다.
        now = 1_000
        session.resultShown()
        now = 50_000
        session.resultShown()
        now = 61_000
        session.sessionExited(PillSessionSummary(1, 0, 1, 0, 0))

        assertEquals(60L, sentOf<AnalyticsEvent.PillIdentifySessionExit>().single().elapsedSec)
    }

    @Test
    fun `새 사진은 앞 사진의 수치를 물려받지 않는다`() {
        session.attrEdited("1", 1, attribute = "color", manual = false)
        session.enterEdit("1")
        now = 30_000
        session.leaveEdit("1")
        session.resultShown()
        session.limitReached()

        session.reset()
        recorder.sent.clear()

        session.confirmed(pillId = "1", pillIndex = 1, candidateIndex = 1)
        val confirm = sentOf<AnalyticsEvent.PillConfirm>().single()
        assertEquals(0, confirm.editCount)
        assertEquals("none", confirm.editedAttrs)
        assertEquals(0L, confirm.dwellMs)

        // 결과 화면 기준도 비워져 중도이탈이 안 나간다.
        session.sessionExited(PillSessionSummary(1, 1, 0, 0, 0))
        assertTrue(sentOf<AnalyticsEvent.PillIdentifySessionExit>().isEmpty())

        // 한도 소진도 다시 셀 수 있다 — 날짜가 바뀌면 다시 도달할 수 있다.
        session.limitReached()
        assertEquals(1, sentOf<AnalyticsEvent.PillLimitReached>().size)
    }

    @Test
    fun `권한 응답은 granted denied 로 보낸다`() {
        session.permissionAnswered(permission = "camera", granted = false, gate = "pill")
        val event = sentOf<AnalyticsEvent.PermissionResult>().single()
        assertEquals("denied", event.result)
        assertEquals("pill", event.gate)
    }
}
