package app.nursemate.core.timer

import app.nursemate.core.model.CareTimer

/**
 * 타이머의 수명 사건을 밖으로 알린다. 지표가 이걸 듣는다.
 *
 * ## 왜 화면이 아니라 여기서 부르나
 * 타이머가 끝나는 경로가 넷이다 — 목록의 [정지], 전체화면 알람의 [완료], 알림 액션,
 * 그리고 워치에서 끝낸 것이 복제본으로 넘어오는 경우. 화면마다 지표를 찍으면 **그중
 * 하나만 빠뜨려도 완료율이 조용히 낮게 나온다.** 넷이 전부 [TimerRepository] 로 합류하므로
 * 집계 지점은 여기 하나면 충분하다.
 *
 * ## 구현은 앱마다 다르다
 * `:app` 은 Firebase 로 보내고, `:wear` 는 아무것도 하지 않는다([None]).
 * 워치도 보내면 **한 번의 시작이 두 번 잡힌다** — 워치가 시작한 타이머는 복제본으로
 * 폰에 넘어와 폰이 `source=watch` 로 이미 세기 때문이다.
 * [TimerAlarmScheduler] 와 같은 구조다.
 */
interface TimerAnalytics {

    /** 타이머가 시작됐다. [source] 는 `phone`/`widget`/`watch`. */
    fun started(timer: CareTimer, source: String)

    /**
     * 타이머가 끝났다.
     *
     * @param completed 끝까지 울린 뒤 [완료] 면 true, 울리기 전에 껐으면 false.
     * @param elapsedSec 시작부터 끈 시각까지. 취소가 언제 일어나는지 보는 값이다.
     */
    fun ended(timer: CareTimer, completed: Boolean, elapsedSec: Int, remainingSec: Int)

    /** 아무 데도 안 보낸다. 워치가 쓴다. */
    object None : TimerAnalytics {
        override fun started(timer: CareTimer, source: String) = Unit
        override fun ended(timer: CareTimer, completed: Boolean, elapsedSec: Int, remainingSec: Int) = Unit
    }

    companion object {
        /** 폰 앱 화면에서 시작. */
        const val SOURCE_PHONE = "phone"

        /** 홈 화면 위젯에서 시작. */
        const val SOURCE_WIDGET = "widget"

        /**
         * 워치에서 시작 — 복제본이 넘어와 폰이 대신 센다.
         *
         * 워치 앱이 직접 보내지 않는 이유는 [TimerAnalytics] 클래스 주석에 있다.
         */
        const val SOURCE_WATCH = "watch"
    }
}
