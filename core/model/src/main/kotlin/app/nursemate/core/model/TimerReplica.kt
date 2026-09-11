package app.nursemate.core.model

import kotlinx.serialization.Serializable

/**
 * 폰·워치가 각자 들고 서로 맞추는 타이머 집합 — 정본 개정 요청 중(`docs/SPEC-FEEDBACK.md`).
 *
 * ## 왜 스냅샷을 덮어쓰지 않는가
 * 지금까지는 폰이 주인이고 워치가 [PresetSnapshot] 을 받아 **통째로 덮어썼다.** 그 구조에서는
 * 워치가 끊긴 동안 아무것도 할 수 없다 — 시작도 못 하고, 만료를 알 수도 없다. 양쪽이 각자
 * 타이머를 갖게 하려면 "누가 최신인가"를 **타이머 하나 단위로** 가려야 한다.
 *
 * ## 벽시계로 승자를 정하지 않는다
 * 두 기기의 시계는 어긋난다. 실제로 이 프로젝트의 스냅샷 로그에 같은 밀리초가 찍혀
 * `at=1789029814427 < 1789029814427` 로 비교된 적이 있다. 그래서 [TimerRecord.rev] 라는
 * **논리 시계**를 쓴다 — 고칠 때마다 1씩 올리고, 큰 쪽이 이긴다.
 *
 * @param origin 이 복제본을 들고 있는 기기([ORIGIN_PHONE] 또는 [ORIGIN_WATCH])
 * @param seq 이 기기가 **자기 기록을 고친 횟수.** 합치기만 해서는 오르지 않는다 —
 *   오르면 상대가 다시 응답하고 그게 또 이쪽을 깨워 왕복이 멎지 않는다
 * @param ackSeq 상대에게서 받은 마지막 [seq]. 상대가 이 값을 보고 자기 자리표를 버린다
 * @param records 살아 있는 타이머와 지워진 자리표(tombstone)를 함께 담는다
 */
@Serializable
data class TimerReplica(
    val origin: String,
    val seq: Long = 0,
    val ackSeq: Long = 0,
    val records: List<TimerRecord> = emptyList()
) {

    /** 화면·알람이 쓰는 살아 있는 타이머만. */
    val timers: List<CareTimer> get() = records.mapNotNull { it.timer }

    /** [id] 의 현재 기록. 지워졌으면 tombstone 이 나온다(없는 것과 구분된다). */
    fun record(id: String): TimerRecord? = records.firstOrNull { it.id == id }
}

/**
 * 타이머 하나의 복제 기록.
 *
 * ## 지운 것도 남긴다 (tombstone)
 * 완료·정지가 곧 삭제라(spec §생성 → 실행) 레코드를 그냥 없애면, 상대가 **"내겐 있고
 * 저쪽엔 없으니 가져다줘야겠다"** 로 읽어 되살린다. 병합 규칙이 대칭이라 **연결이 멀쩡해도**
 * 그렇다 — 끊김과 무관한 문제다. 지웠다는 사실을 빈 기록으로 남겨야 삭제가 전파된다.
 *
 * @param id 타이머 식별자. tombstone 은 [timer] 가 없어 여기서만 알 수 있다
 * @param rev 논리 시계. 고칠 때마다 [bumped] 로 올린다
 * @param origin 이 판을 **마지막으로 고친** 기기. [rev] 가 같을 때의 결정론적 승부에 쓴다
 * @param timer 본문. `null` 이면 tombstone 이다
 * @param removedAt 지운 시각(벽시계). **승부에 쓰지 않고** 안전망 기한 계산에만 쓴다
 * @param removedSeq 지운 기기의 그때 [TimerReplica.seq]. 상대가 이 판을 확인했는지로
 *   자리표를 버릴 때를 정한다
 */
@Serializable
data class TimerRecord(
    val id: String,
    val rev: Long,
    val origin: String,
    val timer: CareTimer? = null,
    val removedAt: Long? = null,
    val removedSeq: Long? = null
) {

    val isRemoved: Boolean get() = timer == null

    /** 이 기록을 [origin] 이 고쳤다고 한 판 올린다. */
    fun bumped(origin: String, timer: CareTimer?, removedAt: Long? = null, removedSeq: Long? = null) = TimerRecord(
        id = id,
        rev = rev + 1,
        origin = origin,
        timer = timer,
        removedAt = removedAt,
        removedSeq = removedSeq
    )
}

/** 폰이 만든 판. */
const val ORIGIN_PHONE = "phone"

/** 워치가 만든 판. */
const val ORIGIN_WATCH = "watch"

/**
 * 상대가 영영 돌아오지 않을 때를 대비한 **안전망 기한.**
 *
 * 보통은 [prunedTombstones] 의 확인 규칙이 훨씬 먼저 자리표를 걷는다 — 붙어 있으면 왕복
 * 한 번이면 된다. 이 기한은 상대가 그 사이 한 번도 안 돌아온 경우에만 쓰이고, 그때는
 * 개수도 안 늘어나므로 넉넉히 잡아 삭제를 놓칠 여지를 줄인다.
 */
const val TOMBSTONE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000

/**
 * 청소 후에도 남길 자리표 최대 개수. 오래된 것부터 버린다.
 *
 * 상대가 아예 없는 기기(워치를 안 쓰는 사용자)에서는 확인이 영영 안 오므로 이 상한이
 * 유일한 방벽이다. 자리표 한 건이 100바이트 남짓이라 200개면 20KB — DataItem 100KB 제한
 * 안에 넉넉히 든다.
 */
const val MAX_TOMBSTONES = 200

/** 상대가 아직 아무것도 확인해 주지 않았다. */
private const val NO_ACK = -1L

/**
 * 상대 복제본을 합쳐 넣는다. 내 [TimerReplica.origin] 과 [TimerReplica.seq] 는 그대로다.
 *
 * 결과가 `this` 와 같으면 **아무것도 바뀌지 않았다는 뜻**이다 — 그때 다시 발행하지 않아야
 * 서로 되받는 무한 루프가 끊긴다([mergeRecords] 가 id 순으로 정렬해 주는 이유다).
 *
 * ⚠️ **[TimerReplica.seq] 를 여기서 올리지 않는다.** 올리면 상대가 그걸 확인해 응답하고,
 * 그 응답이 또 내 확인을 바꿔 발행이 멎지 않는다. 내 기록을 고칠 때만 오른다.
 */
fun TimerReplica.mergedWith(other: TimerReplica, now: Long): TimerReplica {
    val ack = maxOf(ackSeq, other.seq)
    return copy(
        ackSeq = ack,
        records = prunedTombstones(
            records = mergeRecords(records, other.records),
            myOrigin = origin,
            myAck = ack,
            peerAck = other.ackSeq,
            now = now
        )
    )
}

/**
 * 타이머 목록이 이렇게 바뀌었다고 장부에 적는다.
 *
 * 저장소는 여전히 "타이머 목록을 통째로 바꾼다"는 모양으로 쓰이고(`TimerStore.mutateTimers`),
 * 판 올리기와 자리표 만들기는 여기서 알아서 한다 — 부르는 쪽이 복제를 몰라도 된다.
 *
 * - 내용이 그대로인 타이머는 **판을 올리지 않는다.** 올리면 상대가 바뀐 줄 알고 되받아
 *   쓰고, 그게 또 이쪽을 깨워 발행이 멎지 않는다.
 * - [timers] 에서 빠진 것은 자리표로 남긴다 — 완료·정지가 곧 삭제라서다.
 *
 * @param now 자리표에 적을 시각. 승부에는 안 쓰고 안전망 기한에만 쓴다
 */
fun TimerReplica.withTimers(timers: List<CareTimer>, now: Long): TimerReplica {
    val nextSeq = seq + 1
    val byId = records.associateBy { it.id }
    val next = timers.map { timer ->
        val existing = byId[timer.id]
        when {
            existing == null -> TimerRecord(id = timer.id, rev = 1, origin = origin, timer = timer)
            existing.timer == timer -> existing
            else -> existing.bumped(origin, timer)
        }
    }

    val surviving = timers.mapTo(HashSet()) { it.id }
    val gone = records.filter { !it.isRemoved && it.id !in surviving }
        .map { it.bumped(origin, timer = null, removedAt = now, removedSeq = nextSeq) }
    val tombstones = records.filter { it.isRemoved }

    // 상대가 무엇을 확인했는지는 합칠 때만 알 수 있다. 여기서는 기한·개수만 본다.
    val pruned = prunedTombstones(
        records = (next + gone + tombstones).sortedBy { it.id },
        myOrigin = origin,
        myAck = ackSeq,
        peerAck = NO_ACK,
        now = now
    )
    return if (pruned == records) this else copy(seq = nextSeq, records = pruned)
}

/**
 * 두 복제본의 기록을 합친다. 결과는 **양쪽에서 같아야 한다** — 어느 쪽이 먼저 합치든 같은
 * 값이 나온다.
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
 */
fun mergeRecords(mine: List<TimerRecord>, theirs: List<TimerRecord>): List<TimerRecord> {
    val merged = LinkedHashMap<String, TimerRecord>(mine.size + theirs.size)
    mine.forEach { merged[it.id] = it }
    theirs.forEach { incoming ->
        val current = merged[incoming.id]
        merged[incoming.id] = if (current == null) incoming else winnerOf(current, incoming)
    }
    return merged.values.sortedBy { it.id }
}

/**
 * [mergeRecords] 의 판정 하나. 규칙은 그쪽 문서에 적었다.
 *
 * ⚠️ **전제: 한 기기가 같은 `rev` 로 두 가지 내용을 만들지 않는다.** `rev` 는 그 기기가
 * 고칠 때마다 오르므로 이 전제는 지금 구조에서 저절로 성립한다. 깨지면 — `rev` 도 `origin`
 * 도 같은데 내용만 다른 두 기록이 생기면 — `a` 를 고르는 쪽으로 기울어 **합치는 순서에 따라
 * 결과가 갈린다.**
 */
fun winnerOf(a: TimerRecord, b: TimerRecord): TimerRecord = when {
    a.rev != b.rev -> if (a.rev > b.rev) a else b
    else -> if (a.origin >= b.origin) a else b
}

/**
 * 자리표를 버린다. 살아 있는 타이머는 건드리지 않는다.
 *
 * ## 양쪽이 봤으면 버린다
 * 자리표는 **삭제를 상대에게 전하려고** 들고 있는 것이다. 상대가 그걸 받았다는 게 확인되면
 * 더 들고 있을 이유가 없다. 기한을 감으로 정하는 대신 확인으로 정한다.
 *
 * 판정은 **"자리표를 만든 쪽의 판을 반대쪽이 확인했는가"** 하나다. 양쪽이 같은 명제를
 * 평가하므로 같은 순간에 버리고, 한쪽만 버려 상대가 되돌려주는 진동이 생기지 않는다.
 * - 내가 만든 자리표 → 상대의 [TimerReplica.ackSeq] 가 [TimerRecord.removedSeq] 이상인가
 * - 상대가 만든 자리표 → 내 [TimerReplica.ackSeq] 가 그 이상인가
 *
 * ## 기한과 개수는 안전망이다
 * 상대가 영영 안 돌아오면 확인이 안 온다. 그때만 [TOMBSTONE_TTL_MILLIS] 와
 * [MAX_TOMBSTONES] 가 쓰인다. [TimerRecord.removedAt] 이 없는 자리표는 언제 지웠는지
 * 모르므로 기한으로 버리지 않는다 — 삭제가 닿기 전에 사라지면 안 된다.
 */
fun prunedTombstones(
    records: List<TimerRecord>,
    myOrigin: String,
    myAck: Long,
    peerAck: Long,
    now: Long
): List<TimerRecord> {
    val kept = records.filterNot { record ->
        if (!record.isRemoved) return@filterNot false
        val acknowledged = record.removedSeq?.let { seq ->
            if (record.origin == myOrigin) peerAck >= seq else myAck >= seq
        } == true
        val expired = record.removedAt != null && now - record.removedAt >= TOMBSTONE_TTL_MILLIS
        acknowledged || expired
    }

    val tombstones = kept.filter { it.isRemoved }
    if (tombstones.size <= MAX_TOMBSTONES) return kept

    val survivors = tombstones.sortedByDescending { it.removedAt ?: Long.MAX_VALUE }
        .take(MAX_TOMBSTONES)
        .toSet()
    return kept.filter { !it.isRemoved || it in survivors }
}
