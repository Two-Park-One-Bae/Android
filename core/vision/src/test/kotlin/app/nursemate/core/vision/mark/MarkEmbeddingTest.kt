package app.nursemate.core.vision.mark

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 마크 임베딩 전송 형식 — NM-515. 계약이 못박은 값이라 경계를 박아 둔다.
 *
 * 어긋나도 서버는 에러를 내지 않고 **후보 순서만** 틀어지므로, 여기서 잡지 못하면
 * 실기기에서도 못 잡는다.
 */
class MarkEmbeddingTest {

    private fun decode(base64: String): ShortArray {
        val bytes = Base64.getDecoder().decode(base64)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(bytes.size / 2) { buffer.short }
    }

    @Test
    fun `크기가 계약대로다`() {
        val encoded = MarkEmbedding.encode(FloatArray(MarkEmbedding.FLOATS))!!
        assertEquals("원본 12,288 B", 12_288, MarkEmbedding.BYTES)
        assertEquals("base64 16,384 자", 16_384, encoded.length)
    }

    /** 8 × 768 이 아닌 것은 보내지 않는다 — 서버가 400 을 준다. */
    @Test
    fun `크기가 다르면 안 보낸다`() {
        assertNull(MarkEmbedding.encode(FloatArray(MarkEmbedding.FLOATS - 1)))
        assertNull(MarkEmbedding.encode(FloatArray(0)))
    }

    /** little-endian 이 아니면 서버가 전혀 다른 수를 읽는다. 1.0f = 0x3C00 이다. */
    @Test
    fun `fp16 리틀엔디안이다`() {
        val bytes = Base64.getDecoder().decode(MarkEmbedding.encode(FloatArray(MarkEmbedding.FLOATS) { 1f })!!)
        assertEquals("낮은 바이트가 먼저다", 0x00.toByte(), bytes[0])
        assertEquals(0x3C.toByte(), bytes[1])
    }

    @Test
    fun `알려진 값들이 맞다`() {
        val samples = floatArrayOf(0f, -0f, 1f, -1f, 0.5f, 2f, 0.0999755859375f)
        val expected = intArrayOf(0x0000, -0x8000, 0x3C00, -0x4400, 0x3800, 0x4000, 0x2E66)
        val input = FloatArray(MarkEmbedding.FLOATS)
        samples.copyInto(input)
        val half = decode(MarkEmbedding.encode(input)!!)
        for (i in samples.indices) {
            assertEquals("${samples[i]} 를 접은 값", expected[i].toShort(), half[i])
        }
    }

    /**
     * 자르지 않고 **최근접 짝수**로 반올림한다. 기준 구현이 numpy `astype(float16)` 이다.
     *
     * 0.1f 는 fp16 두 값의 정확히 중간이 아니라 위쪽에 가까워 0x2E66 으로 올라가야 한다 —
     * 자르면 0x2E65 가 된다. 한 ulp 씩 아래로 쏠리면 코사인이 미세하게 틀어진다.
     */
    @Test
    fun `자르지 않고 반올림한다`() {
        val input = FloatArray(MarkEmbedding.FLOATS)
        input[0] = 0.1f
        assertEquals(0x2E66.toShort(), decode(MarkEmbedding.encode(input)!!)[0])
    }

    /** 순서를 바꾸지 않는다 — 회전 8개가 바깥인 row-major 평탄화 그대로 간다. */
    @Test
    fun `순서를 그대로 둔다`() {
        val input = FloatArray(MarkEmbedding.FLOATS) { (it % 3).toFloat() }
        val half = decode(MarkEmbedding.encode(input)!!)
        val zero = 0x0000.toShort()
        val one = 0x3C00.toShort()
        val two = 0x4000.toShort()
        assertEquals(MarkEmbedding.FLOATS, half.size)
        for (i in 0 until MarkEmbedding.FLOATS) {
            assertEquals(
                "$i 번째",
                when (i % 3) {
                    0 -> zero
                    1 -> one
                    else -> two
                },
                half[i]
            )
        }
    }
}
