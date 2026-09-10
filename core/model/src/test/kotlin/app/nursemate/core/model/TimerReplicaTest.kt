package app.nursemate.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 폰·워치가 각자 타이머를 갖고 맞추는 규칙 고정.
 *
 * 여기 값이 흔들리면 **끊겼다 붙을 때 조용히 어긋난다** — 지운 게 되살아나거나, 워치에서
 * 만든 게 사라지거나, 서로 되받아 끝없이 발행한다. 셋 다 실기기에서만 드러나 잡기 어렵다.
 */
class TimerReplicaTest {

    private val t0 = 1_700_000_000_000L

    private fun timer(id: String, label: String = "AST", state: TimerState = TimerState.RUNNING) = CareTimer(
        id = id,
        label = label,
        category = TimerCategory.TEST,
        durationSeconds = 900,
        endAtEpochMillis = t0 + 900_000,
        state = state
    )

    private fun live(id: String, rev: Long, origin: String, label: String = "AST") =
        TimerRecord(id = id, rev = rev, origin = origin, timer = timer(id, label))

    private fun dead(id: String, rev: Long, origin: String, removedAt: Long? = t0, removedSeq: Long? = null) =
        TimerRecord(id = id, rev = rev, origin = origin, timer = null, removedAt = removedAt, removedSeq = removedSeq)

    /** 확인이 하나도 없는 상태의 청소 — 기한·개수만 본다. */
    private fun prune(records: List<TimerRecord>, now: Long) =
        prunedTombstones(records, myOrigin = ORIGIN_PHONE, myAck = -1, peerAck = -1, now = now)

    // ── 한쪽에만 있는 것 ──────────────────────────────────────────────

    @Test
    fun `상대에게만 있는 타이머를 가져온다`() {
        // 워치가 끊긴 동안 만든 타이머가 재연결 때 폰으로 넘어오는 경우.
        val merged = mergeRecords(mine = emptyList(), theirs = listOf(live("a", 1, ORIGIN_WATCH)))

        assertEquals(listOf("a"), merged.map { it.id })
        assertEquals(ORIGIN_WATCH, merged.single().origin)
    }

    @Test
    fun `내게만 있는 타이머는 남는다`() {
        val merged = mergeRecords(mine = listOf(live("a", 1, ORIGIN_PHONE)), theirs = emptyList())

        assertEquals(1, merged.size)
    }

    // ── 승부 ────────────────────────────────────────────────────────

    @Test
    fun `판이 큰 쪽이 이긴다`() {
        val merged = mergeRecords(
            mine = listOf(live("a", 1, ORIGIN_PHONE, label = "옛것")),
            theirs = listOf(live("a", 2, ORIGIN_WATCH, label = "새것"))
        )

        assertEquals("새것", merged.single().timer?.label)
    }

    @Test
    fun `판이 같으면 워치가 이긴다`() {
        // 끊긴 동안 같은 타이머를 양쪽에서 각각 고친 경우. 어느 쪽이든 한쪽은 사라지므로
        // 방금 손목에서 한 조작을 살린다 — 규칙이 결정론적이라는 게 핵심이다.
        val merged = mergeRecords(
            mine = listOf(live("a", 3, ORIGIN_PHONE, label = "폰")),
            theirs = listOf(live("a", 3, ORIGIN_WATCH, label = "워치"))
        )

        assertEquals("워치", merged.single().timer?.label)
    }

    @Test
    fun `합치는 쪽을 바꿔도 결과가 같다`() {
        // 폰이 합치든 워치가 합치든 같은 값이어야 한다. 아니면 둘이 영영 안 맞는다.
        val phone = listOf(live("a", 3, ORIGIN_PHONE), dead("b", 2, ORIGIN_PHONE), live("c", 1, ORIGIN_PHONE))
        val watch = listOf(live("a", 3, ORIGIN_WATCH), live("b", 1, ORIGIN_WATCH), live("d", 5, ORIGIN_WATCH))

        assertEquals(mergeRecords(phone, watch), mergeRecords(watch, phone))
    }

    @Test
    fun `자기 자신과 합치면 그대로다`() {
        // 3단계에서 "합친 결과가 내 것과 같으면 다시 발행하지 않는다"로 무한 루프를 끊는다.
        // 그 규칙은 이 성질이 성립해야만 성립한다.
        val mine = listOf(live("b", 2, ORIGIN_PHONE), dead("a", 4, ORIGIN_WATCH), live("c", 1, ORIGIN_WATCH))
        val once = mergeRecords(mine, mine)

        assertEquals(once, mergeRecords(once, once))
        assertEquals(once.map { it.id }, once.map { it.id }.sorted())
    }

    // ── 삭제 전파 ────────────────────────────────────────────────────

    @Test
    fun `지운 자리표가 살아 있는 기록을 이긴다`() {
        // 끊긴 채 워치에서 [완료] 를 누른 뒤 다시 붙는 경우. 폰의 살아 있는 기록이 이기면
        // 이미 끝낸 타이머가 되살아나 또 울린다.
        val merged = mergeRecords(
            mine = listOf(live("a", 1, ORIGIN_PHONE)),
            theirs = listOf(dead("a", 2, ORIGIN_WATCH))
        )

        assertTrue(merged.single().isRemoved)
        assertNull(merged.single().timer)
    }

    @Test
    fun `되살리려면 판을 올려야 한다`() {
        // 같은 id 를 다시 쓰는 일은 없지만, 규칙이 한 방향임을 못박아 둔다.
        val merged = mergeRecords(
            mine = listOf(dead("a", 2, ORIGIN_WATCH)),
            theirs = listOf(live("a", 3, ORIGIN_PHONE))
        )

        assertEquals(false, merged.single().isRemoved)
    }

    @Test
    fun `한 판 올리면 지운 것으로 바뀐다`() {
        val before = live("a", 7, ORIGIN_PHONE)
        val after = before.bumped(ORIGIN_WATCH, timer = null, removedAt = t0)

        assertEquals(8, after.rev)
        assertEquals(ORIGIN_WATCH, after.origin)
        assertTrue(after.isRemoved)
    }

    // ── 자리표 청소 ──────────────────────────────────────────────────

    @Test
    fun `기한이 지난 자리표는 버린다`() {
        val old = dead("a", 1, ORIGIN_PHONE, removedAt = t0)
        val merged = prune(listOf(old), now = t0 + TOMBSTONE_TTL_MILLIS + 1)

        assertTrue(merged.isEmpty())
    }

    @Test
    fun `기한 안의 자리표는 남긴다`() {
        val recent = dead("a", 1, ORIGIN_PHONE, removedAt = t0)
        val merged = prune(listOf(recent), now = t0 + TOMBSTONE_TTL_MILLIS - 1)

        assertEquals(1, merged.size)
    }

    @Test
    fun `살아 있는 타이머는 아무리 오래돼도 안 버린다`() {
        val merged = prune(listOf(live("a", 1, ORIGIN_PHONE)), now = t0 + TOMBSTONE_TTL_MILLIS * 10)

        assertEquals(1, merged.size)
    }

    @Test
    fun `지운 시각을 모르는 자리표는 안 버린다`() {
        // 언제 지웠는지 모르는 것을 버리면 삭제가 상대에게 닿기 전에 사라질 수 있다.
        val merged = prune(listOf(dead("a", 1, ORIGIN_PHONE, removedAt = null)), now = t0)

        assertEquals(1, merged.size)
    }

    @Test
    fun `자리표가 넘치면 오래된 것부터 버린다`() {
        val many = (1..MAX_TOMBSTONES + 50).map { dead("t%04d".format(it), 1, ORIGIN_PHONE, removedAt = t0 + it) }
        val merged = prune(many, now = t0)

        assertEquals(MAX_TOMBSTONES, merged.size)
        // 가장 최근 것들만 남는다.
        assertTrue(merged.all { (it.removedAt ?: 0) > t0 + 50 })
    }

    // ── 복제본 ──────────────────────────────────────────────────────

    @Test
    fun `살아 있는 타이머만 화면에 준다`() {
        val replica = TimerReplica(
            origin = ORIGIN_WATCH,
            records = listOf(live("a", 1, ORIGIN_WATCH), dead("b", 2, ORIGIN_PHONE))
        )

        assertEquals(listOf("a"), replica.timers.map { it.id })
        assertTrue(replica.record("b")!!.isRemoved)
        assertNull(replica.record("없는것"))
    }

    // ── 목록 반영 ────────────────────────────────────────────────────

    @Test
    fun `새 타이머는 첫 판으로 들어온다`() {
        val replica = TimerReplica(ORIGIN_PHONE).withTimers(listOf(timer("a")), t0)

        assertEquals(1, replica.records.single().rev)
        assertEquals(ORIGIN_PHONE, replica.records.single().origin)
    }

    @Test
    fun `내용이 그대로면 판을 안 올린다`() {
        // 올리면 상대가 바뀐 줄 알고 되받아 쓰고, 그게 또 이쪽을 깨워 발행이 안 멎는다.
        val before = TimerReplica(ORIGIN_PHONE, records = listOf(live("a", 5, ORIGIN_WATCH)))
        val after = before.withTimers(listOf(timer("a")), t0)

        assertEquals(before, after)
    }

    @Test
    fun `내용이 바뀌면 판을 올리고 내 이름을 적는다`() {
        val before = TimerReplica(ORIGIN_PHONE, records = listOf(live("a", 5, ORIGIN_WATCH)))
        val after = before.withTimers(listOf(timer("a", state = TimerState.PAUSED)), t0)

        assertEquals(6, after.records.single().rev)
        assertEquals(ORIGIN_PHONE, after.records.single().origin)
    }

    @Test
    fun `목록에서 빠지면 자리표가 된다`() {
        val before = TimerReplica(ORIGIN_PHONE, records = listOf(live("a", 2, ORIGIN_PHONE)))
        val after = before.withTimers(emptyList(), t0)

        val record = after.records.single()
        assertTrue(record.isRemoved)
        assertEquals(3, record.rev)
        assertEquals(t0, record.removedAt)
    }

    @Test
    fun `이미 지운 것을 다시 지우지 않는다`() {
        // 자리표는 그대로 둔다 — 다시 판을 올리면 상대와 끝없이 주고받는다.
        val before = TimerReplica(ORIGIN_PHONE, records = listOf(dead("a", 3, ORIGIN_WATCH)))
        val after = before.withTimers(emptyList(), t0 + 1)

        assertEquals(before.records, after.records)
    }

    @Test
    fun `반영 결과도 id 순이다`() {
        val replica = TimerReplica(ORIGIN_PHONE).withTimers(listOf(timer("c"), timer("a"), timer("b")), t0)

        assertEquals(listOf("a", "b", "c"), replica.records.map { it.id })
    }

    @Test
    fun `합쳐도 내 이름은 그대로다`() {
        val mine = TimerReplica(ORIGIN_PHONE, records = listOf(live("a", 1, ORIGIN_PHONE)))
        val theirs = TimerReplica(ORIGIN_WATCH, records = listOf(live("b", 1, ORIGIN_WATCH)))

        val merged = mine.mergedWith(theirs, t0)

        assertEquals(ORIGIN_PHONE, merged.origin)
        assertEquals(listOf("a", "b"), merged.records.map { it.id })
    }

    @Test
    fun `바뀐 게 없으면 합친 결과가 나와 같다`() {
        // 3단계에서 이 비교로 "다시 발행할지"를 정한다.
        val mine = TimerReplica(ORIGIN_PHONE, records = listOf(live("a", 2, ORIGIN_PHONE), dead("b", 1, ORIGIN_WATCH)))
        val theirs = TimerReplica(ORIGIN_WATCH, records = mine.records)

        assertEquals(mine, mine.mergedWith(theirs, t0))
    }

    // ── 확인 기반 청소 ──────────────────────────────────────────────

    @Test
    fun `상대가 내 판을 확인하면 내 자리표를 버린다`() {
        // 자리표는 삭제를 상대에게 전하려고 들고 있는 것이다. 상대가 받았다는 게 확인되면
        // 더 들고 있을 이유가 없다 — 기한을 감으로 정하는 대신 확인으로 정한다.
        val mine = TimerReplica(
            ORIGIN_PHONE,
            seq = 7,
            records = listOf(dead("a", 2, ORIGIN_PHONE, removedSeq = 7))
        )
        val theirs = TimerReplica(ORIGIN_WATCH, seq = 3, ackSeq = 7)

        assertTrue(mine.mergedWith(theirs, t0).records.isEmpty(), "확인했는데 자리표가 남았다")
    }

    @Test
    fun `상대가 아직 확인 안 했으면 자리표를 들고 있는다`() {
        val mine = TimerReplica(
            ORIGIN_PHONE,
            seq = 7,
            records = listOf(dead("a", 2, ORIGIN_PHONE, removedSeq = 7))
        )
        val theirs = TimerReplica(ORIGIN_WATCH, seq = 3, ackSeq = 6)

        assertEquals(1, mine.mergedWith(theirs, t0).records.size, "확인 전인데 버렸다")
    }

    @Test
    fun `상대가 만든 자리표는 내 확인으로 버린다`() {
        // 판정은 "만든 쪽의 판을 반대쪽이 확인했는가" 하나다. 양쪽이 같은 명제를 보므로
        // 한쪽만 버려 상대가 되돌려주는 진동이 생기지 않는다.
        val mine = TimerReplica(
            ORIGIN_WATCH,
            records = listOf(dead("a", 2, ORIGIN_PHONE, removedSeq = 5))
        )
        val theirs = TimerReplica(ORIGIN_PHONE, seq = 5)

        assertTrue(mine.mergedWith(theirs, t0).records.isEmpty(), "내가 봤는데 자리표가 남았다")
    }

    @Test
    fun `합치기만으로는 판 번호가 오르지 않는다`() {
        // ⚠️ 오르면 상대가 그걸 확인해 응답하고, 그 응답이 또 내 확인을 바꿔 발행이 멎지 않는다.
        val mine = TimerReplica(ORIGIN_PHONE, seq = 4, records = listOf(live("a", 1, ORIGIN_PHONE)))

        assertEquals(4, mine.mergedWith(TimerReplica(ORIGIN_WATCH, seq = 9), t0).seq)
    }

    @Test
    fun `내 기록을 고치면 판 번호가 오른다`() {
        val before = TimerReplica(ORIGIN_PHONE, seq = 4)

        assertEquals(5, before.withTimers(listOf(timer("a")), t0).seq)
    }

    @Test
    fun `삭제가 오간 뒤 자리표까지 사라지고 멎는다`() {
        // 실제 왕복을 그대로 흉내 낸다. 자리표가 남거나 서로 되돌려주며 진동하면 여기서 걸린다.
        var phone = TimerReplica(ORIGIN_PHONE).withTimers(listOf(timer("a")), t0)
        var watch = TimerReplica(ORIGIN_WATCH).mergedWith(phone, t0)
        phone = phone.mergedWith(watch, t0)

        phone = phone.withTimers(emptyList(), t0) // 폰에서 완료

        var rounds = 0
        while (rounds < MAX_ROUNDS) {
            val nextWatch = watch.mergedWith(phone, t0)
            val nextPhone = phone.mergedWith(nextWatch, t0)
            if (nextWatch == watch && nextPhone == phone) break
            watch = nextWatch
            phone = nextPhone
            rounds++
        }

        assertTrue(rounds < MAX_ROUNDS, "수렴하지 않고 계속 주고받았다")
        assertTrue(phone.records.isEmpty(), "폰에 자리표가 남았다: ${phone.records}")
        assertTrue(watch.records.isEmpty(), "워치에 자리표가 남았다: ${watch.records}")
    }

    private companion object {
        const val MAX_ROUNDS = 20
    }
}
