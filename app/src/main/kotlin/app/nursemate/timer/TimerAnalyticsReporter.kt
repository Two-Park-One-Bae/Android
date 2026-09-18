package app.nursemate.timer

import app.nursemate.core.model.CareTimer
import app.nursemate.core.timer.TimerAnalytics
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
class TimerAnalyticsReporter @Inject constructor(private val analytics: AppAnalytics) : TimerAnalytics {

    override fun started(timer: CareTimer, source: String) {
        analytics.track(
            AnalyticsEvent.TimerStart(
                source = source,
                presetLabel = timer.label,
                category = timer.category.label,
                durationSec = timer.durationSeconds
            )
        )
    }

    override fun ended(timer: CareTimer, completed: Boolean, elapsedSec: Int, remainingSec: Int) {
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
    }
}
