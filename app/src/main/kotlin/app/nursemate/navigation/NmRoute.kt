package app.nursemate.navigation

/**
 * 화면 경로.
 *
 * 알약 식별은 화면이 여러 개 이어지므로 **중첩 그래프**([PILL_GRAPH])로 묶는다.
 * 그래야 촬영 → 미리보기 → 결과가 백스택으로 쌓이고, ViewModel 을 그래프 단위로 공유할 수 있다.
 */
object NmRoute {
    /** 인증 — 스펙상 로그인 없이 홈에 도달하는 경로는 없다. */
    const val LOGIN = "login"

    /** 동의 온보딩 — 로그인은 됐지만 필수 동의가 남은 상태. 동의 없이 홈으로 가는 경로도 없다. */
    const val CONSENT = "consent"

    const val HOME = "home"
    const val TIMER = "timer"
    const val SETTINGS = "settings"

    /** 알약 식별 플로우. 탭바가 없는 전체화면이라 탭 루트로 감싸지 않는다. */
    const val PILL_GRAPH = "pill"
    const val PILL_CAPTURE = "pill/capture"
    const val PILL_PREVIEW = "pill/preview"
    const val PILL_LOADING = "pill/loading"
    const val PILL_RESULT = "pill/result"
    const val PILL_NOT_FOUND = "pill/not-found"
    const val PILL_FAILED = "pill/failed"

    /** 수정·후보 선택. 어느 알약인지 pillId 로 받는다. */
    const val PILL_EDIT = "pill/edit/{pillId}"

    fun pillEdit(pillId: String): String = "pill/edit/$pillId"
}
