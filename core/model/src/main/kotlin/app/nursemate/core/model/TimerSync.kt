package app.nursemate.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 폰→워치 스냅샷 — 정본 `spec/feature/care-timer/domain-model.md` §폰↔워치 동기화 계약.
 *
 * ## 폰이 상태의 주인이다
 * 워치는 **받아 그리고 명령을 돌려보낼 뿐** 상태를 직접 바꾸지 않는다. 그래서 부분 갱신이
 * 아니라 **바뀔 때마다 전체 목록**을 보낸다 — 유실돼도 다음 스냅샷이 전부 덮어써 복구된다.
 *
 * ⚠️ **[AlertMode] 는 싣지 않는다.** 워치는 그 값과 무관하게 항상 햅틱으로 울린다(spec §워치).
 * 넣으면 워치가 폰 설정을 따라야 하는 것처럼 읽힌다.
 *
 * @param snapshotAt 만든 시각. 충돌 판정 기준이다([newerOf])
 * @param alarmAvailable 폰이 요건을 만족하는 알람을 **예약할 수 있는가**(플랫폼·OS 판정)
 * @param alarmAuthorized 폰에서 **지금 타이머를 시작할 수 있는가** — 알람 권한 셋과 울림 방식
 *        최초 선택을 모두 지났는가. 권한만 담으면 워치가 "울림 방식을 아직 안 골랐다"를 몰라,
 *        눌러도 폰이 조용히 거절하는 상태가 된다.
 */
@Serializable
data class TimerSnapshot(
    val snapshotAt: Long,
    val timers: List<CareTimer> = emptyList(),
    val presets: List<TimerPreset> = emptyList(),
    val alarmAvailable: Boolean? = null,
    val alarmAuthorized: Boolean? = null
)

/**
 * 워치가 타이머 기능 자체를 막아야 하는가.
 *
 * ⚠️ **`null` 은 막지 않는다.** 스냅샷을 아직 못 받았거나 구버전 폰인 경우라, 모르는 것을
 * 이유로 기능을 잠그면 정상 기기에서도 못 쓰게 된다. **확실히 `false` 일 때만** 막는다.
 */
val TimerSnapshot.timersBlocked: Boolean get() = alarmAvailable == false

/** 시작을 눌렀을 때 권한 안내를 띄워야 하는가. 판정 규칙은 [timersBlocked] 와 같다. */
val TimerSnapshot.alarmPermissionMissing: Boolean get() = alarmAuthorized == false

/**
 * 둘 중 이기는 스냅샷 — **`snapshotAt` 이 최신인 쪽**(spec §충돌·삭제).
 *
 * 같은 시각이면 갖고 있던 것을 유지한다. 전달 순서가 뒤집혀 옛 스냅샷이 늦게 도착하는 경우가
 * 있어서, 도착 순서가 아니라 만든 시각으로 정한다.
 */
fun newerOf(current: TimerSnapshot?, incoming: TimerSnapshot): TimerSnapshot =
    if (current == null || incoming.snapshotAt > current.snapshotAt) incoming else current

/**
 * 워치→폰 명령 — 정본 §명령.
 *
 * ## 시각을 싣지 않는다
 * 전달이 지연되면 **도착 시점 기준으로 처리**된다(spec). 워치가 "3초 전에 눌렀다"를 주장해
 * 폰이 과거로 되돌리는 일을 만들지 않는다.
 *
 * 프리셋 추가·편집·삭제 명령은 없다 — 프리셋 편집은 폰 전용이다(spec §워치).
 */
@Serializable
sealed interface TimerCommand {

    /** 프리셋 원탭. 폰이 새 타이머를 만들어 다음 스냅샷에 담아 돌려준다. */
    @Serializable
    @SerialName("start")
    data class Start(val presetId: String) : TimerCommand

    @Serializable
    @SerialName("pause")
    data class Pause(val timerId: String) : TimerCommand

    @Serializable
    @SerialName("resume")
    data class Resume(val timerId: String) : TimerCommand

    /** 완료·정지 — 폰과 마찬가지로 **둘 다 삭제**다(spec §생성 → 실행). */
    @Serializable
    @SerialName("remove")
    data class Remove(val timerId: String) : TimerCommand
}
