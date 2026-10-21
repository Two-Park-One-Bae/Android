package app.nursemate.timer

import app.nursemate.core.model.CareTimer
import app.nursemate.core.timer.TimerAnalytics
import app.nursemate.review.ReviewRequester
import app.nursemate.telemetry.AnalyticsEvent
import app.nursemate.telemetry.AppAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `core:timer` 가 알려 주는 타이머 사건을 지표로 옮긴다.
 *
 * 폰만 이걸 바인딩한다 — 워치는 [TimerAnalytics.None] 이다. 이유는 [TimerAnalytics] 주석에.
 *
 * ## 분류는 **한국어 라벨**을 보낸다
 * `TimerCategory.label`(`투약`·`처치`·`검사`)이다. iOS 가 `category.displayName` 으로
 * 같은 한국어를 보내고 있어, enum 이름(`MEDICATION`)을 쓰면 한 축이 두 값으로 쪼개진다.
 */
@Singleton
class TimerAnalyticsReporter @Inject constructor(
    private val analytics: AppAnalytics,
    private val review: ReviewRequester
) : TimerAnalytics {

    override suspend fun started(timer: CareTimer, source: String) {
        // 스토어 리뷰 집계(NM-561) — 폰 · 위젯 · 워치 시작이 모두 여기로 온다. 폰 · 위젯은 지금 시작한 것이고, 워치 시작은
        // 폰이 늦게(다시 연결될 때) 받을 수 있어 그 타이머가 실제로 시작한 날로 센다. 워치 시작 시각은 남은 끝 시각에서
        // 되짚는다 — 일시정지 · 연장을 거쳤으면 조금 어긋날 수 있다(시작 시각을 따로 싣지 않는다).
        val startedAt = if (source == TimerAnalytics.SOURCE_WATCH) {
            timer.endAtEpochMillis - timer.durationSeconds * MILLIS_PER_SECOND
        } else {
            System.currentTimeMillis()
        }
        review.timerStarted(startedAt)
        analytics.track(
            AnalyticsEvent.TimerStart(
                source = source,
                presetLabel = timer.label,
                category = timer.category.label,
                durationSec = timer.durationSeconds
            )
        )
    }

    override suspend fun ended(
        timer: CareTimer,
        completed: Boolean,
        elapsedSec: Int,
        remainingSec: Int,
        source: String
    ) {
        // 완료와 취소의 파라미터가 다르다. 취소는 "얼마나 쓰다 껐는지"가 분석 대상이라
        // 경과·잔여를 함께 남기고, 완료는 끝까지 간 것이라 그 값이 늘 같아 의미가 없다.
        val event = if (completed) {
            AnalyticsEvent.TimerComplete(
                category = timer.category.label,
                durationSec = timer.durationSeconds
            )
        } else {
            AnalyticsEvent.TimerCancel(
                category = timer.category.label,
                elapsedSec = elapsedSec,
                remainingSec = remainingSec
            )
        }
        analytics.track(event)
        // 끝까지 울린 뒤 「완료」만 — 울리기 전에 끈 것은 리뷰 시점이 아니다(spec §스토어 리뷰 요청 — 타이머를 끝내면).
        // 워치에서 끝낸 것은 늘 「앱 밖」이다 — 폰 앱이 앞에 있어도 맥락 없이 창을 띄우지 않고 다음에 앱을 열 때 본다.
        if (completed) review.timerCompleted(outside = source == TimerAnalytics.SOURCE_WATCH)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
