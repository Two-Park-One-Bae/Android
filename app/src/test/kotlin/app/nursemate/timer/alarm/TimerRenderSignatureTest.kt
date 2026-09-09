package app.nursemate.timer.alarm

import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerPreset
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 진행 중 알림을 다시 그릴지 가르는 시그니처.
 *
 * `TimerOngoingNotifier` 는 이 값이 그대로면 재렌더를 건너뛴다. 알림에 드러나는 변화가
 * 여기에 안 담기면 화면과 알림이 어긋난 채 굳는다.
 */
class TimerRenderSignatureTest {

    private val t0 = 1_700_000_000_000L
    private val preset = TimerPreset(
        id = "p1",
        label = "AST",
        category = TimerCategory.TEST,
        durationSeconds = 900,
        sortOrder = 0
    )

    @Test
    fun `일시정지를 연장하면 시그니처가 바뀐다`() {
        // `extend` 가 PAUSED 에서는 `remainingSeconds` 만 바꾼다 — `state`·`endAt` 은 그대로다.
        // 그래도 정렬 순서가 뒤집히므로 알림을 다시 그려야 한다.
        val paused = CareTimerTransitions.pause(
            CareTimerTransitions.start(preset, "t1", t0),
            t0
        )
        val extended = CareTimerTransitions.extend(paused)

        assertNotEquals(
            "남은 시간이 바뀌었는데 시그니처가 같으면 알림이 옛 순서를 붙든다",
            timerRenderSignature(listOf(paused)),
            timerRenderSignature(listOf(extended))
        )
    }

    @Test
    fun `상태와 만료 시각이 바뀌어도 시그니처가 바뀐다`() {
        val running = CareTimerTransitions.start(preset, "t1", t0)
        val paused = CareTimerTransitions.pause(running, t0 + 60_000L)
        val resumed = CareTimerTransitions.resume(paused, t0 + 120_000L)

        assertNotEquals(timerRenderSignature(listOf(running)), timerRenderSignature(listOf(paused)))
        assertNotEquals(timerRenderSignature(listOf(paused)), timerRenderSignature(listOf(resumed)))
    }
}
