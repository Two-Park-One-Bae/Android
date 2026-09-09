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
     * 두 시트를 [denied] 로 가른다. 한 번 요청했는데도 권한이 없으면 거부 문구로 바꿔
     * 설정으로 유도한다 — 같은 안내를 반복하면 사용자가 무엇이 잘못됐는지 모른다.
     */
    data class Permission(val denied: Boolean) : TimerGate

    /** 울림 방식 최초 1회 선택 — 정본 `첫 시작 — 울림 방식 선택`. */
    data object AlertMode : TimerGate
}
