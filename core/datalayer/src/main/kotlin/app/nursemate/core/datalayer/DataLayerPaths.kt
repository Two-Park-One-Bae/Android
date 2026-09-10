package app.nursemate.core.datalayer

/**
 * 폰↔워치 Wearable Data Layer 경로·키.
 *
 * 동기화 계약 정본: `spec/feature/care-timer/domain-model.md`
 * (전체 스냅샷 브로드캐스트, snapshotAt last-write-wins, 스냅샷에 없으면 삭제, 워치→폰은 명령만)
 *
 * ⚠️ **경로 문자열을 바꾸면 구버전과 말이 안 통한다.** 폰과 워치는 따로 배포된다.
 */
object DataLayerPaths {

    /** 폰 → 워치. 상태 전체. */
    const val TIMER_SNAPSHOT = "/nursemate/timer/snapshot"

    /** 워치 → 폰. 단발 명령. */
    const val TIMER_COMMAND = "/nursemate/timer/command"

    /** 스냅샷 DataItem 안에서 JSON 이 들어 있는 자리. */
    const val KEY_SNAPSHOT_JSON = "snapshot"
}
