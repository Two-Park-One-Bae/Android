package app.nursemate.core.model

import kotlinx.serialization.Serializable

/**
 * 폰 → 워치 프리셋 목록.
 *
 * ## 왜 프리셋만 오가나
 * 타이머는 양쪽이 각자 갖고 [TimerReplica] 로 맞춘다. 프리셋은 다르다 — **편집이 폰
 * 전용이라**(spec §워치) 주인이 하나뿐이고, 워치는 받아 읽기만 한다. 주인이 하나면 합칠
 * 일이 없으므로 통째로 덮어쓰는 편이 단순하다.
 *
 * ## 부분 갱신을 하지 않는다
 * 바뀐 것만 보내면 워치가 놓친 조각을 영영 모른 채로 남는다. 전체를 보내면 한 번만 닿아도
 * 완전히 복구된다 — 블루투스로 오가는 값이라 유실을 전제해야 한다.
 *
 * ⚠️ **[AlertMode] 는 싣지 않는다.** 워치는 그 값과 무관하게 항상 햅틱으로 울린다(spec §워치).
 * 넣으면 워치가 폰 설정을 따라야 하는 것처럼 읽힌다.
 *
 * @param snapshotAt 만든 시각. 어느 쪽이 최신인지 가리는 기준이다([newerOf])
 */
@Serializable
data class PresetSnapshot(val snapshotAt: Long, val presets: List<TimerPreset> = emptyList())

/**
 * 둘 중 이기는 쪽 — **`snapshotAt` 이 최신인 것**.
 *
 * 같은 시각이면 갖고 있던 것을 유지한다. 전달 순서가 뒤집혀 옛 것이 늦게 도착하는 경우가
 * 있어서, 도착 순서가 아니라 만든 시각으로 정한다.
 */
fun newerOf(current: PresetSnapshot?, incoming: PresetSnapshot): PresetSnapshot =
    if (current == null || incoming.snapshotAt > current.snapshotAt) incoming else current
