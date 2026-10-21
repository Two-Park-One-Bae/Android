package app.nursemate.review

/**
 * 스토어 리뷰를 요청해도 되는가 — spec `feature/pill-recognition/README.md` §스토어 리뷰 요청(NM-618) 그대로다.
 *
 * 순수 함수다. 세는 것은 [ReviewTracker], 창을 부르는 것은 [ReviewRequester] 가 한다.
 *
 * ## 만족도와 무관하다
 * 만족한 사용자만 골라 평가를 받는 것은 두 스토어 모두 금지한다. 그래서 여기 들어오는 것은 쓴 횟수 · 날짜뿐이다.
 *
 * ## 간격은 앱이 센다
 * Play 는 창을 띄웠는지 알려 주지 않는다. 한도도 공개하지 않는다 — 앱은 마지막 **요청** 뒤 30일만 둔다.
 */
object ReviewPolicy {

    /**
     * 이번에 요청할 묶음 번호(1 · 2 · 3). 어느 묶음도 다 차지 않았으면 null.
     *
     * @param unmodified 이번 식별에서 확정한 알약을 모두 사용자 수정 없이 골랐는가 — 식별일 때만 본다
     */
    fun bundleFor(
        stats: ReviewStats,
        trigger: ReviewTrigger,
        nowMs: Long,
        today: Long,
        unmodified: Boolean = false
    ): Int? {
        if (!intervalPassed(stats, nowMs)) return null
        return when {
            trigger == ReviewTrigger.IDENTIFIED && identifyBundle(stats, nowMs, today, unmodified) -> 1
            usageBundle(stats, today) -> 2
            trigger == ReviewTrigger.TIMER_ENDED && timerBundle(stats, nowMs) -> 3
            else -> null
        }
    }

    private fun intervalPassed(stats: ReviewStats, nowMs: Long): Boolean =
        stats.lastRequestAt?.let { nowMs - it >= REQUEST_INTERVAL_MS } ?: true

    private fun usedForDays(stats: ReviewStats, nowMs: Long, days: Long): Boolean =
        stats.firstUseAt?.let { nowMs - it >= days * DAY_MS } ?: false

    /** 묶음 1 — 식별을 3번 이상 저장했고, 쓴 지 3일이 지났고, 이번에 고치지 않고 골랐고, 오늘 한도 안내를 보지 않았다. */
    private fun identifyBundle(stats: ReviewStats, nowMs: Long, today: Long, unmodified: Boolean): Boolean =
        stats.identifyCount >= MIN_IDENTIFY &&
            usedForDays(stats, nowMs, MIN_DAYS_SINCE_FIRST_USE) &&
            unmodified &&
            stats.limitShownDay != today

    /** 묶음 2 — 최근 7일 중 3일 이상 썼고(위젯 · 워치만 쓴 날 포함), 식별 · 타이머를 합쳐 3번 이상 썼다. */
    private fun usageBundle(stats: ReviewStats, today: Long): Boolean {
        val recentDays = stats.usedDays.count { it in (today - RECENT_WINDOW_DAYS + 1)..today }
        return recentDays >= MIN_RECENT_DAYS && stats.identifyCount + stats.timerStartCount >= MIN_TOTAL_USES
    }

    /** 묶음 3 — 타이머를 끝냈을 때만. 쓴 지 3일이 지났고 타이머를 5번 이상 시작했다. */
    private fun timerBundle(stats: ReviewStats, nowMs: Long): Boolean =
        usedForDays(stats, nowMs, MIN_DAYS_SINCE_FIRST_USE) && stats.timerStartCount >= MIN_TIMER_STARTS

    const val DAY_MS = 24L * 60 * 60 * 1000
    private const val REQUEST_INTERVAL_MS = 30 * DAY_MS
    private const val MIN_IDENTIFY = 3
    private const val MIN_DAYS_SINCE_FIRST_USE = 3L
    private const val RECENT_WINDOW_DAYS = 7
    private const val MIN_RECENT_DAYS = 3
    private const val MIN_TOTAL_USES = 3
    private const val MIN_TIMER_STARTS = 5

    /** 쓴 날짜는 이만큼만 남긴다 — 판정은 최근 7일만 본다. */
    const val KEEP_DAYS = 14L
}

/** 요청을 검사하는 때. */
enum class ReviewTrigger {
    /** 식별 기록 저장에 성공하고 들어온 화면으로 돌아왔다. */
    IDENTIFIED,

    /** 타이머를 끝냈다 — 앱 안에서 끝냈으면 그 직후, 앱 밖에서 끝냈으면 다음에 앱을 열 때. */
    TIMER_ENDED
}

/**
 * 기기에만 두는 사용 기록 — 재설치하면 처음부터 센다(백업에서도 뺀다).
 *
 * @param usedDays 쓴 날짜(기기 시간대의 epoch day). 앱을 연 날 · 위젯 · 워치로 타이머를 시작한 날
 * @param firstUseAt 첫 사용 시각 — 앱을 연 것뿐 아니라 위젯 · 워치로 타이머를 쓴 것도 사용이다
 * @param limitShownDay 오늘 한도 안내를 본 날(epoch day)
 */
data class ReviewStats(
    val identifyCount: Int = 0,
    val timerStartCount: Int = 0,
    val usedDays: Set<Long> = emptySet(),
    val firstUseAt: Long? = null,
    val lastRequestAt: Long? = null,
    val limitShownDay: Long? = null
)
