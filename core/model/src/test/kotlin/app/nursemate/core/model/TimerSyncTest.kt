package app.nursemate.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * 폰↔워치를 오가는 값의 계약 고정.
 *
 * 폰과 워치는 **따로 배포되는 두 앱**이라, 여기 값이 바뀌면 한쪽만 업데이트된 사용자에게서
 * 조용히 깨진다. 그래서 필드 이름과 판정 규칙을 테스트로 못박는다.
 *
 * 오가는 것은 둘이다 — 프리셋([PresetSnapshot], 폰 → 워치 한 방향)과
 * 타이머 복제본([TimerReplica], 양방향).
 */
class TimerSyncTest {

    /** 선언 순서 그대로의 필드 이름. `elementNames` 는 실험 API 라 안정 API 로 훑는다. */
    private fun SerialDescriptor.fieldNames() = (0 until elementsCount).map(::getElementName)

    private val t0 = 1_700_000_000_000L

    private val preset = TimerPreset(
        id = "p1",
        label = "AST",
        category = TimerCategory.TEST,
        durationSeconds = 900,
        sortOrder = 0
    )

    private val timer = CareTimer(
        id = "t1",
        label = "AST",
        category = TimerCategory.TEST,
        durationSeconds = 900,
        endAtEpochMillis = t0 + 900_000
    )

    private fun snapshot(at: Long = t0) = PresetSnapshot(snapshotAt = at, presets = listOf(preset))

    private fun replica() = TimerReplica(
        origin = ORIGIN_PHONE,
        records = listOf(TimerRecord(id = timer.id, rev = 1, origin = ORIGIN_PHONE, timer = timer))
    )

    // ── 프리셋 ──────────────────────────────────────────────────────

    @Test
    fun `프리셋 목록은 오갔다 와도 그대로다`() {
        assertEquals(snapshot(), decodePresets(encodePresets(snapshot())))
    }

    @Test
    fun `프리셋 목록 필드는 계약 그대로다`() {
        // ⚠️ **인코딩 결과가 아니라 필드 목록을 본다.** `encodeDefaults = false` 라 기본값을
        // 가진 필드는 JSON 에 안 찍힌다 — 결과만 보면 `alertMode` 를 붙여도 통과한다.
        // 실제로 이 테스트를 그렇게 썼다가 놓쳤다.
        assertEquals(
            listOf("snapshotAt", "presets"),
            PresetSnapshot.serializer().descriptor.fieldNames(),
            "프리셋 목록 필드가 계약과 다르다"
        )
    }

    @Test
    fun `모르는 필드가 있어도 읽는다`() {
        // 새 폰 → 옛 워치. 여기서 예외가 나면 동기화가 통째로 멈춘다.
        val json = """{"snapshotAt":$t0,"presets":[],"futureField":{"a":1}}"""
        val decoded = decodePresets(json)
        assertNotNull(decoded, "모르는 필드 때문에 통째로 버렸다")
        assertEquals(t0, decoded.snapshotAt)
    }

    @Test
    fun `깨진 프리셋 목록은 예외가 아니라 null 이다`() {
        assertNull(decodePresets("{"))
        assertNull(decodePresets(""))
    }

    // ── 어느 쪽이 최신인가 ──────────────────────────────────────────

    @Test
    fun `늦게 만든 것이 이긴다`() {
        val old = snapshot(at = t0)
        val new = snapshot(at = t0 + 1)
        assertEquals(new, newerOf(old, new), "최신이 못 이겼다")
        // 도착 순서가 뒤집혀 옛 것이 나중에 와도 밀려나면 안 된다.
        assertEquals(new, newerOf(new, old), "옛 것이 최신을 덮었다")
    }

    @Test
    fun `같은 시각이면 갖고 있던 것을 유지한다`() {
        val current = snapshot(at = t0)
        val incoming = snapshot(at = t0)
        assertSame(current, newerOf(current, incoming), "동률인데 새 것으로 바뀌었다")
    }

    @Test
    fun `처음 받은 것은 그대로 채택한다`() {
        val first = snapshot()
        assertSame(first, newerOf(null, first))
    }

    // ── 타이머 복제본 ───────────────────────────────────────────────

    @Test
    fun `복제본은 오갔다 와도 그대로다`() {
        assertEquals(replica(), decodeReplica(encodeReplica(replica())))
    }

    @Test
    fun `복제본 필드는 계약 그대로다`() {
        assertEquals(
            listOf("origin", "records"),
            TimerReplica.serializer().descriptor.fieldNames(),
            "복제본 필드가 계약과 다르다"
        )
        assertEquals(
            listOf("id", "rev", "origin", "timer", "removedAt"),
            TimerRecord.serializer().descriptor.fieldNames(),
            "복제 기록 필드가 계약과 다르다"
        )
    }

    @Test
    fun `메모는 값이 없으면 나가지 않는다`() {
        // 프라이버시 공통 규칙 — 워치는 처치 키워드 + 분류 태그까지만 본다.
        // (메모가 있는 타이머는 폰이 지우고 보내는 게 아니라, 워치 화면이 안 그린다.
        //  여기서는 빈 값이 굳이 실려 나가지 않는지만 본다.)
        assertFalse(encodeReplica(replica()).contains("memo"))
    }

    @Test
    fun `깨진 복제본은 예외가 아니라 null 이다`() {
        assertNull(decodeReplica("{"))
        assertNull(decodeReplica(""))
    }

    @Test
    fun `자리표는 본문 없이도 읽힌다`() {
        // 삭제 전파의 핵심이다. `timer` 가 빠진 모양을 못 읽으면 지운 것이 되살아난다.
        val json = """{"origin":"watch","records":[{"id":"t1","rev":2,"origin":"watch","removedAt":$t0}]}"""
        val decoded = decodeReplica(json)
        assertNotNull(decoded)
        assertEquals(1, decoded.records.size)
        assertEquals(true, decoded.records.single().isRemoved)
    }
}
