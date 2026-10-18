package app.nursemate.core.vision

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * LiteRT 로 넘어오며 바뀐 텐서 배치(NM-628). 틀리면 예외 없이 **답만** 틀어지므로 값을 박아 둔다.
 */
class TensorLayoutTest {

    @Test
    fun `NCHW 를 NHWC 로 바꾸면 화소마다 채널이 붙는다`() {
        // n=2, c=3, h=1, w=2 — 값 = 배치·채널·화소를 자릿수로
        val nchw = floatArrayOf(
            0f, 1f, 10f, 11f, 20f, 21f,
            100f, 101f, 110f, 111f, 120f, 121f
        )
        val nhwc = nchwToNhwc(nchw, FloatArray(nchw.size), n = 2, c = 3, plane = 2)
        assertArrayEquals(
            floatArrayOf(
                0f, 10f, 20f, 1f, 11f, 21f,
                100f, 110f, 120f, 101f, 111f, 121f
            ),
            nhwc,
            0f
        )
    }

    @Test
    fun `쿼리가 마지막 축인 마스크에서 한 쿼리의 평면을 모은다`() {
        val queries = RfDetrSpec.QUERY_COUNT
        val plane = 3
        // 화소 p 의 쿼리 q 자리에 p * 1000 + q
        val masks = FloatArray(plane * queries) { i -> (i / queries) * 1000f + (i % queries) }
        val out = FloatArray(plane)
        querySlice(masks, query = 7, out = out)
        assertArrayEquals(floatArrayOf(7f, 1007f, 2007f), out, 0f)
    }
}
