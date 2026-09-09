package app.nursemate.timer

/**
 * 타이머 시작을 가로막는 관문.
 *
 * spec §알람 권한의 흐름을 그대로 옮긴 것이다 — 첫 실행 시도 → A3 안내 → OS 권한 →
 * 울림 방식 최초 1회 → 시작. 관문마다 시트가 하나씩 대응한다.
 */
sealed interface TimerGate {
    /** 막는 것이 없다. */
    data object None : TimerGate

    /**
     * 알람 권한 — 정본 `A3 알람 권한 안내`(첫 안내) · `A3 알람 권한 거부`(요청 뒤에도 없을 때).
     *
     * @param step 지금 받아야 할 권한 하나. 화면이 무엇을 요청할지 이걸로 정한다.
     * @param denied **그 권한을 이미 한 번 요청했는데도** 없는 상태. 거부 시트로 바꿔 설정으로
     *               유도한다. 아직 안 물어본 권한이 남은 것과는 구분해야 한다 — Android 는
     *               받을 권한이 둘이라, 순서대로 받는 중인 것을 거부로 오해하면 첫 허용 직후
     *               곧바로 "알람이 꺼져 있어요"가 뜬다.
     */
    data class Permission(val step: PermissionStep, val denied: Boolean) : TimerGate

    /** 울림 방식 최초 1회 선택 — 정본 `첫 시작 — 울림 방식 선택`. */
    data object AlertMode : TimerGate
}

/**
 * 받아야 할 권한 하나.
 *
 * 받는 방법이 서로 달라 화면이 갈라 처리한다 — 알림은 런타임 팝업, 정확 알람은 시스템
 * 설정 왕복이다(Android 제약).
 */
enum class PermissionStep {
    /** `POST_NOTIFICATIONS` — 런타임 팝업으로 받는다. */
    NOTIFICATION,

    /** `SCHEDULE_EXACT_ALARM` — 팝업이 없어 시스템 설정으로 보냈다 돌아와야 한다. */
    EXACT_ALARM
}
