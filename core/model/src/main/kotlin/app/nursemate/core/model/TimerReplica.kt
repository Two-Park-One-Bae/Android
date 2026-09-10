package app.nursemate.core.model

import kotlinx.serialization.Serializable

/**
 * 폰·워치가 각자 들고 서로 맞추는 타이머 집합 — 정본 개정 요청 중(`docs/SPEC-FEEDBACK.md`).
 *
 * ## 왜 스냅샷을 덮어쓰지 않는가
 * 지금까지는 폰이 주인이고 워치가 [TimerSnapshot] 을 받아 **통째로 덮어썼다.** 그 구조에서는
 * 워치가 끊긴 동안 아무것도 할 수 없다 — 시작도 못 하고, 만료를 알 수도 없다. 양쪽이 각자
 * 타이머를 갖게 하려면 "누가 최신인가"를 **타이머 하나 단위로** 가려야 한다.
 *
 * ## 벽시계로 승자를 정하지 않는다
 * 두 기기의 시계는 어긋난다. 실제로 이 프로젝트의 스냅샷 로그에 같은 밀리초가 찍혀
 * `at=1789029814427 < 1789029814427` 로 비교된 적이 있다. 그래서 [TimerRecord.rev] 라는
 * **논리 시계**를 쓴다 — 고칠 때마다 1씩 올리고, 큰 쪽이 이긴다.
 *
 * @param origin 이 복제본을 들고 있는 기기([ORIGIN_PHONE] 또는 [ORIGIN_WATCH])
 * @param records 살아 있는 타이머와 지워진 자리표(tombstone)를 함께 담는다
 */
@Serializable
data class TimerReplica(val origin: String, val records: List<TimerRecord> = emptyList()) {

    /** 화면·알람이 쓰는 살아 있는 타이머만. */
    val timers: List<CareTimer> get() = records.mapNotNull { it.timer }

    /** [id] 의 현재 기록. 지워졌으면 tombstone 이 나온다(없는 것과 구분된다). */
    fun record(id: String): TimerRecord? = records.firstOrNull { it.id == id }
}

/**
 * 타이머 하나의 복제 기록.
 *
 * ## 지운 것도 남긴다 (tombstone)
 * 완료·정지가 곧 삭제라(spec §생성 → 실행) 레코드를 그냥 없애면, 다시 연결됐을 때 상대가
 * **"내겐 있고 저쪽엔 없으니 저쪽이 새로 만들 걸 못 받았나"** 로 읽어 되살린다. 지웠다는
 * 사실 자체를 [removedAt] 을 가진 빈 기록으로 남겨야 삭제가 전파된다.
 *
 * @param id 타이머 식별자. tombstone 은 [timer] 가 없어 여기서만 알 수 있다
 * @param rev 논리 시계. 고칠 때마다 [bumped] 로 올린다
 * @param origin 이 판을 **마지막으로 고친** 기기. [rev] 가 같을 때의 결정론적 승부에 쓴다
 * @param timer 본문. `null` 이면 tombstone 이다
 * @param removedAt 지운 시각(벽시계). **승부에 쓰지 않고** 오래된 tombstone 청소에만 쓴다
 */
@Serializable
data class TimerRecord(
    val id: String,
    val rev: Long,
    val origin: String,
    val timer: CareTimer? = null,
    val removedAt: Long? = null
) {

    val isRemoved: Boolean get() = timer == null

    /** 이 기록을 [origin] 이 고쳤다고 한 판 올린다. */
    fun bumped(origin: String, timer: CareTimer?, removedAt: Long? = null) = TimerRecord(
        id = id,
        rev = rev + 1,
        origin = origin,
        timer = timer,
        removedAt = removedAt
    )
}

/** 폰이 만든 판. */
const val ORIGIN_PHONE = "phone"

/** 워치가 만든 판. */
const val ORIGIN_WATCH = "watch"

/**
 * tombstone 을 들고 있는 기간.
 *
 * 상대가 이 기간보다 오래 끊겨 있다 돌아오면 삭제를 못 받아 타이머가 되살아난다. 사흘이면
 * 실사용에서 넉넉하고, 그보다 길게 두면 DataItem 100KB 제한에 다가간다.
 */
const val TOMBSTONE_TTL_MILLIS = 3L * 24 * 60 * 60 * 1000

/** 청소 후에도 남길 tombstone 최대 개수. 오래된 것부터 버린다. */
const val MAX_TOMBSTONES = 200

/**
 * 두 복제본을 합친다. 결과는 **양쪽에서 같아야 한다** — 어느 쪽이 먼저 합치든 같은 값이 나온다.
 *
 * 타이머 하나씩 다음 순서로 이긴 쪽을 고른다:
 * 1. [TimerRecord.rev] 가 큰 쪽
 * 2. 같으면 [TimerRecord.origin] 이 사전순 뒤인 쪽 — 즉 `watch` 가 `phone` 을 이긴다
 *
 * 2번은 **끊긴 동안 같은 타이머를 양쪽에서 각각 고친** 드문 경우에만 쓰인다. 어느 쪽을 택해도
 * 한쪽 조작은 사라지므로, 사용자가 방금 손목에서 한 조작을 살리는 쪽으로 기울였다.
 *
 * ## 결과를 id 순으로 정렬한다
 * 양쪽이 **같은 순서까지** 내놓아야 한다. 그래야 합친 결과가 내가 갖고 있던 것과 같은지를
 * 그냥 `==` 로 볼 수 있고, 같으면 다시 발행하지 않아 **서로 되받는 무한 루프가 끊긴다.**
 *
 * @param now tombstone 청소 기준 시각
 */
fun mergeRecords(mine: List<TimerRecord>, theirs: List<TimerRecord>, now: Long): List<TimerRecord> {
    val merged = LinkedHashMap<String, TimerRecord>(mine.size + theirs.size)
    mine.forEach { merged[it.id] = it }
    theirs.forEach { incoming ->
        val current = merged[incoming.id]
        merged[incoming.id] = if (current == null) incoming else winnerOf(current, incoming)
    }
    return pruneTombstones(merged.values.sortedBy { it.id }, now)
}

/** [mergeRecords] 의 판정 하나. 규칙은 그쪽 문서에 적었다. */
fun winnerOf(a: TimerRecord, b: TimerRecord): TimerRecord = when {
    a.rev != b.rev -> if (a.rev > b.rev) a else b
    else -> if (a.origin >= b.origin) a else b
}

/**
 * 오래되거나 넘치는 tombstone 을 버린다. 살아 있는 타이머는 건드리지 않는다.
 *
 * [removedAt] 이 없는 tombstone 은 청소하지 않는다 — 언제 지웠는지 모르는 것을 버리면
 * 삭제가 전파되기 전에 사라질 수 있다.
 */
fun pruneTombstones(records: List<TimerRecord>, now: Long): List<TimerRecord> {
    val fresh = records.filter { !it.isRemoved || it.removedAt == null || now - it.removedAt < TOMBSTONE_TTL_MILLIS }
    val tombstones = fresh.filter { it.isRemoved }
    if (tombstones.size <= MAX_TOMBSTONES) return fresh

    val kept = tombstones.sortedByDescending { it.removedAt ?: Long.MAX_VALUE }.take(MAX_TOMBSTONES).toSet()
    return fresh.filter { !it.isRemoved || it in kept }
}
