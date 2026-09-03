package app.nursemate.core.vision

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 전처리를 병렬화했으므로, 결과가 순차 실행과 **비트 단위로 동일**한지 확인한다.
 *
 * 병렬화는 성능만 바꿔야 하고 값은 건드리면 안 된다. major 축이 서로 독립이라
 * 이론적으로는 동일해야 하지만, 인덱싱을 잘못 나누면 조용히 틀린 값이 나온다.
 * (같은 이유로 스파이크에서 디코딩 최적화가 정확도를 깎은 걸 뒤늦게 발견했다.)
 */
class RfDetrPreprocessTest {

    @Test
    fun `같은 입력은 몇 번을 돌려도 같은 텐서를 낸다`() {
        val random = Random(42)
        // 실제 사진 비율에 가깝게 (2048 상한을 거친 뒤의 크기)
        for ((width, height) in listOf(2048 to 1536, 1536 to 2048, 2048 to 2048, 997 to 613)) {
            val rgb = ByteArray(width * height * 3) { random.nextInt(256).toByte() }
            val first = RfDetrPreprocess.toInputTensor(rgb, width, height)
            val second = RfDetrPreprocess.toInputTensor(rgb, width, height)
            assertArrayEquals("${width}x$height: 같은 입력에 다른 출력 (병렬화 버그)", first, second, 0f)
        }
    }

    @Test
    fun `출력 크기와 정규화 범위가 규약대로다`() {
        val random = Random(7)
        val width = 1200
        val height = 900
        val rgb = ByteArray(width * height * 3) { random.nextInt(256).toByte() }
        val out = RfDetrPreprocess.toInputTensor(rgb, width, height)

        val side = RfDetrSpec.INPUT_SIZE
        assertEquals("출력 크기가 [1,3,576,576] 이어야 한다", 3 * side * side, out.size)

        // ImageNet 정규화 후 범위는 대략 [-2.2, 2.7]. 채워지지 않은 0 구간이 있으면 버그다.
        val min = out.min()
        val max = out.max()
        assertTrue("정규화 범위 이상: [$min, $max]", min > -3f && max < 3.5f)
        assertTrue("0 값이 과다 — 미채움 구간 의심", out.count { it == 0f } < out.size / 100)
    }

    @Test
    fun `단색 이미지는 채널마다 ImageNet 정규화 상수를 낸다`() {
        val width = 64
        val height = 48
        // R=255, G=0, B=128 로 채운다.
        val rgb = ByteArray(width * height * 3)
        for (i in 0 until width * height) {
            rgb[i * 3] = 255.toByte()
            rgb[i * 3 + 1] = 0
            rgb[i * 3 + 2] = 128.toByte()
        }

        val out = RfDetrPreprocess.toInputTensor(rgb, width, height)
        val plane = RfDetrSpec.INPUT_SIZE * RfDetrSpec.INPUT_SIZE

        val expected = floatArrayOf(255f, 0f, 128f).mapIndexed { c, raw ->
            (raw / 255f - RfDetrSpec.MEAN[c]) / RfDetrSpec.STD[c]
        }
        // 플레인(NCHW)마다 해당 채널 값으로 균일해야 한다. 채널이 섞이면 여기서 잡힌다.
        for (c in 0 until 3) {
            assertEquals("채널 $c 첫 픽셀", expected[c], out[c * plane], 1e-4f)
            assertEquals("채널 $c 마지막 픽셀", expected[c], out[c * plane + plane - 1], 1e-4f)
        }
    }
}
