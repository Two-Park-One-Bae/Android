package app.nursemate.core.vision.mark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 마크 유무를 `hasMark` 로 접는 규칙 — NM-515. 계약이 정한 값이라 경계를 박아 둔다. */
class MarkPresenceTest {

    @Test
    fun `각인을 읽은 면은 0_80 이 임계다`() {
        assertEquals(true, MarkPresence.hasMark(score = 0.80f, imprint = "AZ"))
        assertEquals(true, MarkPresence.hasMark(score = 0.99f, imprint = "AZ"))
        assertNull(MarkPresence.hasMark(score = 0.79f, imprint = "AZ"))
        // 느슨한 쪽 임계는 넘지만 각인을 읽은 면이라 안 된다
        assertNull(MarkPresence.hasMark(score = 0.70f, imprint = "AZ"))
    }

    @Test
    fun `각인을 못 읽은 면은 0_60 이 임계다`() {
        assertEquals(true, MarkPresence.hasMark(score = 0.60f, imprint = null))
        assertEquals(true, MarkPresence.hasMark(score = 0.70f, imprint = null))
        assertNull(MarkPresence.hasMark(score = 0.59f, imprint = null))
    }

    /**
     * `false` 는 「마크 없음」이라는 **하드 조건**이라 모델이 보내면 안 된다. 놓친 면에서
     * `false` 가 나가면 서버가 정답 약을 통째로 떨어뜨린다.
     */
    @Test
    fun `모델은 false 를 내지 않는다`() {
        for (imprint in listOf(null, "AZ")) {
            for (i in 0..100) {
                val v = MarkPresence.hasMark(score = i / 100f, imprint = imprint)
                assertTrue("score=${i / 100f} imprint=$imprint 에서 false 가 나왔다", v == true || v == null)
            }
        }
    }

    /**
     * 임계를 모델 메타데이터에서 읽지 않는다. species 모델의 권장값은 0.5 · 0.8 이고
     * **0.6 은 거기 없다** — 계약이 정한 값이다.
     */
    @Test
    fun `임계 두 값이 계약대로다`() {
        assertEquals(0.80f, MarkPresence.WITH_IMPRINT, 0f)
        assertEquals(0.60f, MarkPresence.WITHOUT_IMPRINT, 0f)
    }

    /** `hasMark` 는 면 단위 필드다 — 앞면이 각인을 읽고 뒷면이 못 읽었으면 임계가 갈린다. */
    @Test
    fun `앞면과 뒷면이 다른 임계를 쓸 수 있다`() {
        val score = 0.70f
        assertNull("앞면(각인 읽음)은 0.80 이라 안 된다", MarkPresence.hasMark(score, imprint = "AZ"))
        assertEquals("뒷면(각인 못 읽음)은 0.60 이라 된다", true, MarkPresence.hasMark(score, imprint = null))
    }
}
