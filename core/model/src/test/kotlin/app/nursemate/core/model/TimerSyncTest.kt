package app.nursemate.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * 폰↔워치 동기화 계약 고정 — 정본 `spec/feature/care-timer/domain-model.md`.
 *
 * 폰과 워치는 **따로 배포되는 두 앱**이라, 여기 값이 바뀌면 한쪽만 업데이트된 사용자에게서
 * 조용히 깨진다. 그래서 표기와 판정 규칙을 테스트로 못박는다.
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

    private fun snapshot(at: Long = t0, available: Boolean? = null, authorized: Boolean? = null) = TimerSnapshot(
        snapshotAt = at,
        timers = listOf(timer),
        presets = listOf(preset),
        alarmAvailable = available,
        alarmAuthorized = authorized
    )

    @Test
    fun `스냅샷은 오갔다 와도 그대로다`() {
        val original = snapshot(available = true, authorized = false)
        assertEquals(original, decodeSnapshot(encodeSnapshot(original)))
    }

    @Test
    fun `스냅샷 필드는 계약 그대로다`() {
        // ⚠️ **인코딩 결과가 아니라 필드 목록을 본다.** `encodeDefaults = false` 라 기본값을
        // 가진 필드는 JSON 에 안 찍힌다 — 결과만 보면 `alertMode` 를 붙여도 통과한다.
        // 실제로 이 테스트를 그렇게 썼다가 놓쳤다.
        //
        // 워치는 울림 방식과 무관하게 항상 햅틱이라 그 값이 계약에 들어오면 안 되고,
        // 필드가 빠지는 것도 같은 무게로 막아야 한다(따로 배포되는 두 앱이라 조용히 깨진다).
        assertEquals(
            listOf("snapshotAt", "timers", "presets", "alarmAvailable", "alarmAuthorized"),
            TimerSnapshot.serializer().descriptor.fieldNames(),
            "스냅샷 필드가 계약과 다르다"
        )
    }

    @Test
    fun `메모는 값이 없으면 나가지 않는다`() {
        // 프라이버시 공통 규칙 — 워치는 처치 키워드 + 분류 태그까지만 본다.
        // (메모가 있는 타이머는 폰이 지우고 보내는 게 아니라, 워치 화면이 안 그린다.
        //  여기서는 빈 값이 굳이 실려 나가지 않는지만 본다.)
        assertFalse(encodeSnapshot(snapshot()).contains("memo"))
    }

    @Test
    fun `모르는 필드가 있어도 읽는다`() {
        // 새 폰 → 옛 워치. 여기서 예외가 나면 동기화가 통째로 멈춘다.
        val json = """{"snapshotAt":$t0,"timers":[],"presets":[],"futureField":{"a":1}}"""
        val decoded = decodeSnapshot(json)
        assertNotNull(decoded, "모르는 필드 때문에 스냅샷을 통째로 버렸다")
        assertEquals(t0, decoded.snapshotAt)
    }

    @Test
    fun `깨진 스냅샷은 예외가 아니라 null 이다`() {
        assertNull(decodeSnapshot("{"))
        assertNull(decodeSnapshot(""))
    }

    @Test
    fun `모르지 않는 값만 차단한다`() {
        // ⚠️ null 은 "아직 모른다"다. 모르는 것을 이유로 잠그면 정상 기기에서도 못 쓴다.
        assertFalse(snapshot(available = null).timersBlocked, "모를 때 막았다")
        assertFalse(snapshot(available = true).timersBlocked)
        assertTrue(snapshot(available = false).timersBlocked, "확실히 안 되는데 안 막았다")

        assertFalse(snapshot(authorized = null).alarmPermissionMissing, "모를 때 권한 안내를 띄웠다")
        assertFalse(snapshot(authorized = true).alarmPermissionMissing)
        assertTrue(snapshot(authorized = false).alarmPermissionMissing)
    }

    @Test
    fun `늦게 만든 스냅샷이 이긴다`() {
        val old = snapshot(at = t0)
        val new = snapshot(at = t0 + 1)
        assertEquals(new, newerOf(old, new), "최신이 못 이겼다")
        // 도착 순서가 뒤집혀 옛 것이 나중에 와도 밀려나면 안 된다.
        assertEquals(new, newerOf(new, old), "옛 스냅샷이 최신을 덮었다")
    }

    @Test
    fun `같은 시각이면 갖고 있던 것을 유지한다`() {
        val current = snapshot(at = t0, available = true)
        val incoming = snapshot(at = t0, available = false)
        assertSame(current, newerOf(current, incoming), "동률인데 새 것으로 바뀌었다")
    }

    @Test
    fun `처음 받은 스냅샷은 그대로 채택한다`() {
        val first = snapshot()
        assertSame(first, newerOf(null, first))
    }

    @Test
    fun `명령은 종류가 구분되어 오간다`() {
        val commands = listOf(
            TimerCommand.Start("p1"),
            TimerCommand.Pause("t1"),
            TimerCommand.Resume("t1"),
            TimerCommand.Remove("t1")
        )
        commands.forEach { assertEquals(it, decodeCommand(encodeCommand(it)), "명령이 뭉개졌다: $it") }
    }

    @Test
    fun `명령 필드는 대상 id 하나뿐이다`() {
        // 전달이 지연돼도 **도착 시점 기준**으로 처리한다(spec §명령). 시각이 실리면
        // 폰이 과거 기준으로 되돌리는 구현이 나온다. 여기도 필드 목록으로 못박는다.
        assertEquals(listOf("presetId"), TimerCommand.Start.serializer().descriptor.fieldNames())
        assertEquals(listOf("timerId"), TimerCommand.Pause.serializer().descriptor.fieldNames())
        assertEquals(listOf("timerId"), TimerCommand.Resume.serializer().descriptor.fieldNames())
        assertEquals(listOf("timerId"), TimerCommand.Remove.serializer().descriptor.fieldNames())
    }

    @Test
    fun `모르는 명령은 조용히 버린다`() {
        // 새 워치 → 옛 폰. 여기서 던지면 리시버가 죽어 뒤이은 정상 명령까지 놓친다.
        assertNull(decodeCommand("""{"type":"snooze","timerId":"t1"}"""))
        assertNull(decodeCommand("아무거나"))
    }
}
