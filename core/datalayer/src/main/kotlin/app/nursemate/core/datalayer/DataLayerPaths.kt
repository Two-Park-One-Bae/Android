package app.nursemate.core.datalayer

/**
 * 폰↔워치 Wearable Data Layer 경로·키 상수.
 * 동기화 계약 정본: spec/feature/care-timer/domain-model.md
 * (전체 스냅샷 브로드캐스트, snapshotAt last-write-wins, 스냅샷에 없으면 삭제, 워치→폰은 명령만)
 * 타이머 V1 착수 시 구현 — 지금은 경로 계약만 고정한다.
 */
object DataLayerPaths {
    const val TIMER_SNAPSHOT = "/nursemate/timer/snapshot"
    const val TIMER_COMMAND = "/nursemate/timer/command"
}
