package app.nursemate.core.datalayer

/**
 * 폰↔워치 Wearable Data Layer 경로·키.
 *
 * 동기화 계약 정본: `spec/feature/care-timer/domain-model.md` (개정 요청 중 —
 * 타이머는 이제 양쪽이 각자 갖고 복제로 맞춘다. 프리셋만 폰 → 워치 한 방향이다.)
 *
 * ⚠️ **경로 문자열을 바꾸면 구버전과 말이 안 통한다.** 폰과 워치는 따로 배포된다.
 */
object DataLayerPaths {

    /** 폰 → 워치. 프리셋 목록 — 편집이 폰 전용이라 한 방향이다. */
    const val PRESET_SNAPSHOT = "/nursemate/timer/presets"

    /**
     * 폰 ↔ 워치. 양쪽이 각자 자기가 아는 것을 여기 쓴다.
     *
     * ⚠️ **경로가 하나여도 서로 덮어쓰지 않는다.** DataItem 은 `wear://<노드id><경로>` 로
     * 구별돼 기기마다 자기 항목이 따로 생긴다. 그래서 읽을 때 `authority` 를 `*` 로 두면
     * 양쪽 항목이 다 나온다 — 경로를 폰용·워치용으로 나눌 이유가 없다.
     */
    const val TIMER_REPLICA = "/nursemate/timer/replica"

    /** 프리셋 DataItem 안에서 JSON 이 들어 있는 자리. */
    const val KEY_PRESET_JSON = "presets"

    /** 복제본 DataItem 안에서 JSON 이 들어 있는 자리. */
    const val KEY_REPLICA_JSON = "replica"
}
