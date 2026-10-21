package app.nursemate.pill

import java.util.Random
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PillLabelsTest {

    @Test
    fun `UUID v7 은 버전 7 · 변형 2 이고 앞 48비트가 만든 시각이다`() {
        val now = 1_792_374_271_556L
        val uuid = UUID.fromString(UuidV7.next(nowMs = now, random = Random(1)))

        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
        assertEquals(now, uuid.mostSignificantBits ushr 16)
    }

    @Test
    fun `같은 밀리초라도 난수로 갈린다`() {
        val now = 1_792_374_271_556L
        val a = UuidV7.next(nowMs = now, random = Random(1))
        val b = UuidV7.next(nowMs = now, random = Random(2))

        assert(a != b)
    }

    @Test
    fun `모델 알약 번호는 pillId 그대로 · 수동 추가는 100 부터`() {
        assertEquals(0, labelIndexOf(pillId(0)))
        assertEquals(99, labelIndexOf(pillId(99)))
        assertEquals(100, labelIndexOf(manualPillId(0)))
        assertEquals(199, labelIndexOf(manualPillId(99)))
    }

    @Test
    fun `범위를 벗어나면 보내지 않는다`() {
        assertNull(labelIndexOf("100"))
        assertNull(labelIndexOf(manualPillId(100)))
        assertNull(labelIndexOf("abc"))
    }
}
