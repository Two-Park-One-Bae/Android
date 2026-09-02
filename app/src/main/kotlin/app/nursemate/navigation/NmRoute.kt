package app.nursemate.navigation

/**
 * 화면 경로.
 *
 * 알약 식별처럼 화면이 여러 개 이어지는 기능은 **중첩 그래프**로 묶어 별도 파일에서 등록한다
 * (NM-394). 여기에는 셸이 직접 아는 경로만 둔다.
 */
object NmRoute {
    /** 인증 — 스펙상 로그인 없이 홈에 도달하는 경로는 없다. */
    const val LOGIN = "login"

    const val HOME = "home"
    const val PILL = "pill"
    const val TIMER = "timer"
    const val SETTINGS = "settings"
}
