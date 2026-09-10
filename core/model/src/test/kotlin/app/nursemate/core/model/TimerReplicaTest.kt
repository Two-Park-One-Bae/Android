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

    private fun dead(id: String, rev: Long, origin: String, removedAt: Long? = t0) =
        TimerRecord(id = id, rev = rev, origin = origin, timer = null, removedAt = removedAt)

    // ── 한쪽에만 있는 것 ──────────────────────────────────────────────

    @Test
    fun `상대에게만 있는 타이머를 가져온다`() {
        // 워치가 끊긴 동안 만든 타이머가 재연결 때 폰으로 넘어오는 경우.
        val merged = mergeRecords(mine = emptyList(), theirs = listOf(live("a", 1, ORIGIN_WATCH)), now = t0)

        assertEquals(listOf("a"), merged.map { it.id })
        assertEquals(ORIGIN_WATCH, merged.single().origin)
    }

    @Test
    fun `내게만 있는 타이머는 남는다`() {
        val merged = mergeRecords(mine = listOf(live("a", 1, ORIGIN_PHONE)), theirs = emptyList(), now = t0)

        assertEquals(1, merged.size)
    }

    // ── 승부 ────────────────────────────────────────────────────────

    @Test
    fun `판이 큰 쪽이 이긴다`() {
        val merged = mergeRecords(
            mine = listOf(live("a", 1, ORIGIN_PHONE, label = "옛것")),
            theirs = listOf(live("a", 2, ORIGIN_WATCH, label = "새것")),
            now = t0
        )

        assertEquals("새것", merged.single().timer?.label)
    }

    @Test
    fun `판이 같으면 워치가 이긴다`() {
        // 끊긴 동안 같은 타이머를 양쪽에서 각각 고친 경우. 어느 쪽이든 한쪽은 사라지므로
        // 방금 손목에서 한 조작을 살린다 — 규칙이 결정론적이라는 게 핵심이다.
        val merged = mergeRecords(
            mine = listOf(live("a", 3, ORIGIN_PHONE, label = "폰")),
            theirs = listOf(live("a", 3, ORIGIN_WATCH, label = "워치")),
            now = t0
        )

        assertEquals("워치", merged.single().timer?.label)
    }

    @Test
    fun `합치는 쪽을 바꿔도 결과가 같다`() {
        // 폰이 합치든 워치가 합치든 같은 값이어야 한다. 아니면 둘이 영영 안 맞는다.
        val phone = listOf(live("a", 3, ORIGIN_PHONE), dead("b", 2, ORIGIN_PHONE), live("c", 1, ORIGIN_PHONE))
        val watch = listOf(live("a", 3, ORIGIN_WATCH), live("b", 1, ORIGIN_WATCH), live("d", 5, ORIGIN_WATCH))

        assertEquals(mergeRecords(phone, watch, t0), mergeRecords(watch, phone, t0))
    }

    @Test
    fun `자기 자신과 합치면 그대로다`() {
        // 3단계에서 "합친 결과가 내 것과 같으면 다시 발행하지 않는다"로 무한 루프를 끊는다.
        // 그 규칙은 이 성질이 성립해야만 성립한다.
        val mine = listOf(live("b", 2, ORIGIN_PHONE), dead("a", 4, ORIGIN_WATCH), live("c", 1, ORIGIN_WATCH))
        val once = mergeRecords(mine, mine, t0)

        assertEquals(once, mergeRecords(once, once, t0))
        assertEquals(once.map { it.id }, once.map { it.id }.sorted())
    }

    // ── 삭제 전파 ────────────────────────────────────────────────────

    @Test
    fun `지운 자리표가 살아 있는 기록을 이긴다`() {
        // 끊긴 채 워치에서 [완료] 를 누른 뒤 다시 붙는 경우. 폰의 살아 있는 기록이 이기면
        // 이미 끝낸 타이머가 되살아나 또 울린다.
        val merged = mergeRecords(
            mine = listOf(live("a", 1, ORIGIN_PHONE)),
            theirs = listOf(dead("a", 2, ORIGIN_WATCH)),
            now = t0
        )

        assertTrue(merged.single().isRemoved)
        assertNull(merged.single().timer)
    }

    @Test
    fun `되살리려면 판을 올려야 한다`() {
        // 같은 id 를 다시 쓰는 일은 없지만, 규칙이 한 방향임을 못박아 둔다.
        val merged = mergeRecords(
            mine = listOf(dead("a", 2, ORIGIN_WATCH)),
            theirs = listOf(live("a", 3, ORIGIN_PHONE)),
            now = t0
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
        val merged = mergeRecords(listOf(old), emptyList(), now = t0 + TOMBSTONE_TTL_MILLIS + 1)

        assertTrue(merged.isEmpty())
    }

    @Test
    fun `기한 안의 자리표는 남긴다`() {
        val recent = dead("a", 1, ORIGIN_PHONE, removedAt = t0)
        val merged = mergeRecords(listOf(recent), emptyList(), now = t0 + TOMBSTONE_TTL_MILLIS - 1)

        assertEquals(1, merged.size)
    }

    @Test
    fun `살아 있는 타이머는 아무리 오래돼도 안 버린다`() {
        val merged = mergeRecords(listOf(live("a", 1, ORIGIN_PHONE)), emptyList(), now = t0 + TOMBSTONE_TTL_MILLIS * 10)

        assertEquals(1, merged.size)
    }

    @Test
    fun `지운 시각을 모르는 자리표는 안 버린다`() {
        // 언제 지웠는지 모르는 것을 버리면 삭제가 상대에게 닿기 전에 사라질 수 있다.
        val merged = mergeRecords(listOf(dead("a", 1, ORIGIN_PHONE, removedAt = null)), emptyList(), now = t0)

        assertEquals(1, merged.size)
    }

    @Test
    fun `자리표가 넘치면 오래된 것부터 버린다`() {
        val many = (1..MAX_TOMBSTONES + 50).map { dead("t%04d".format(it), 1, ORIGIN_PHONE, removedAt = t0 + it) }
        val merged = mergeRecords(many, emptyList(), now = t0)

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
}
