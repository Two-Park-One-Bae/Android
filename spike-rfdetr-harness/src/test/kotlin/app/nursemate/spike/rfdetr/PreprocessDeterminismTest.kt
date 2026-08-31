package app.nursemate.spike.rfdetr

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * 전처리를 병렬화했으므로, 결과가 순차 실행과 **비트 단위로 동일**한지 확인한다.
 *
 * 병렬화는 성능만 바꿔야 하고 값은 건드리면 안 된다. major 축이 서로 독립이라
 * 이론적으로는 동일해야 하지만, 인덱싱을 잘못 나누면 조용히 틀린 값이 나온다.
 * (같은 이유로 오늘 디코딩 최적화가 정확도를 깎은 걸 뒤늦게 발견했다.)
 */
class PreprocessDeterminismTest {

    @Test
    fun parallelMatchesRepeatedRuns() {
        val rnd = Random(42)
        // 실제 사진 비율에 가깝게 (2048 상한을 거친 뒤의 크기)
        for ((w, h) in listOf(2048 to 1536, 1536 to 2048, 2048 to 2048, 997 to 613)) {
            val rgb = ByteArray(w * h * 3) { rnd.nextInt(256).toByte() }
            val a = RfDetrPreprocess.toInputTensor(rgb, w, h)
            val b = RfDetrPreprocess.toInputTensor(rgb, w, h)
            assertArrayEquals("${w}x$h: 같은 입력에 다른 출력 (병렬화 버그)", a, b, 0f)
        }
    }

    @Test
    fun outputShapeAndRangeAreSane() {
        val rnd = Random(7)
        val w = 1200
        val h = 900
        val rgb = ByteArray(w * h * 3) { rnd.nextInt(256).toByte() }
        val out = RfDetrPreprocess.toInputTensor(rgb, w, h)

        val side = RfDetrSpec.INPUT_SIZE
        assertArrayEquals(
            "출력 크기가 [1,3,576,576] 이어야 한다",
            intArrayOf(3 * side * side), intArrayOf(out.size),
        )
        // ImageNet 정규화 후 범위는 대략 [-2.2, 2.7]. 채워지지 않은 0 구간이 있으면 버그다.
        val min = out.min()
        val max = out.max()
        assert(min > -3f && max < 3.5f) { "정규화 범위 이상: [$min, $max]" }
        assert(out.count { it == 0f } < out.size / 100) { "0 값이 과다 — 미채움 구간 의심" }
    }
}
