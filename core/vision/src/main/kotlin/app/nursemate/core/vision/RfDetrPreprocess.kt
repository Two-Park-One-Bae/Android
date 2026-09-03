package app.nursemate.core.vision

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 전처리 — RGB888 바이트 → `[1,3,576,576]` fp32 NCHW.
 *
 * `android.graphics`에 의존하지 않는다. 같은 코드를 JVM 단위 테스트가 그대로 쓰기 위해서다.
 *
 * 정본(iOS `makeInputArray`)과 맞춘 규칙:
 *  - **stretch resize** (letterbox 아님). 종횡비를 무시하고 576×576으로 늘린다.
 *    역변환 시 letterbox 보정을 넣으면 좌표가 전부 어긋난다.
 *  - 채널 순서 RGB, 레이아웃 NCHW(플레인 단위), dtype fp32
 *  - ImageNet 정규화를 `x * scale + offset` 으로 융합
 *
 * ⚠️ **y-flip 없음.** iOS는 `CGContext`에 `translateBy + scaleBy(1,-1)`을 걸어 그리는 바람에
 *    입력이 상하 반전된 채 모델에 들어가고, 후처리 4곳에서 되돌린다. 여기서는 뒤집지 않으므로
 *    후처리에서도 되돌리지 않는다. 5만 장 데이터셋을 만든 Python 파이프라인과 같은 좌표계다.
 *
 * ## 리샘플러가 정확도에 직접 영향을 준다 (실측)
 * 2048px 원본을 576으로 줄일 때 **단순 bilinear를 쓰면 박스 IoU가 0.9745 → 0.9154로 떨어졌다.**
 * 검출 개수와 score는 유지되지만 박스 기하가 밀린다. 원인은 다운스케일 시 필터 support를
 * 배율만큼 넓히지 않아(안티에일리어싱 부재) 원본의 고주파가 에일리어싱되기 때문이다.
 * 그래서 여기서는 Pillow와 동일하게 **support를 스케일링한 분리형 삼각(bilinear) 필터**를 쓴다.
 * Android `Bitmap.createScaledBitmap(filter = true)`는 단순 bilinear라 이 문제를 그대로 갖는다.
 */
object RfDetrPreprocess {

    /**
     * @param rgb   RGB888 연속 바이트 (length == width * height * 3)
     * @param dst   재사용할 출력 버퍼. null이면 새로 할당한다.
     * @return `[1,3,576,576]` 순서의 fp32 배열 (contiguous NCHW)
     */
    fun toInputTensor(rgb: ByteArray, width: Int, height: Int, dst: FloatArray? = null): FloatArray {
        val side = RfDetrSpec.INPUT_SIZE
        require(rgb.size == width * height * 3) {
            "rgb 길이 불일치: ${rgb.size} != ${width * height * 3}"
        }

        // 분리형(separable) 리샘플: 가로 먼저 → 세로. 중간 버퍼는 float 로 둔다.
        val horizontal = resampleAxis(
            src = { y, x, c -> (rgb[(y * width + x) * 3 + c].toInt() and 0xFF).toFloat() },
            srcMajor = height,
            srcMinor = width,
            dstMinor = side
        )
        val both = resampleAxis(
            src = { x, y, c -> horizontal[(y * side + x) * 3 + c] },
            srcMajor = side,
            srcMinor = height,
            dstMinor = side
        )
        // both 는 [x][y][c] 순서로 나온다 (두 번째 패스에서 축을 바꿔 돌았으므로).

        val plane = side * side
        val out = dst ?: FloatArray(3 * plane)
        val scale = FloatArray(3) { 1f / (255f * RfDetrSpec.STD[it]) }
        val offset = FloatArray(3) { -RfDetrSpec.MEAN[it] / RfDetrSpec.STD[it] }

        for (y in 0 until side) {
            for (x in 0 until side) {
                val srcBase = (x * side + y) * 3
                val dstIndex = y * side + x
                for (c in 0 until 3) {
                    out[c * plane + dstIndex] = both[srcBase + c] * scale[c] + offset[c]
                }
            }
        }
        return out
    }

    /**
     * 한 축만 리샘플한다. 결과는 `[major][dstMinor][channel]` 평탄 배열.
     *
     * Pillow `ImagingResample`과 동일한 규약:
     *  - `filterScale = max(1, srcMinor / dstMinor)` — **다운스케일일 때만 support를 넓힌다**
     *  - 삼각 필터 support = 1.0, 실제 support = 1.0 * filterScale
     *  - 가중치는 정규화해서 합이 1이 되게 한다
     */
    private inline fun resampleAxis(
        crossinline src: (major: Int, minor: Int, channel: Int) -> Float,
        srcMajor: Int,
        srcMinor: Int,
        dstMinor: Int
    ): FloatArray {
        val out = FloatArray(srcMajor * dstMinor * 3)
        val scale = srcMinor.toFloat() / dstMinor
        val filterScale = max(1f, scale)
        val support = filterScale // 삼각 필터의 support 는 1.0

        // 가중치는 출력 인덱스 d 에만 의존하므로 미리 전부 계산해 둔다.
        val los = IntArray(dstMinor)
        val ws = arrayOfNulls<FloatArray>(dstMinor)
        for (d in 0 until dstMinor) {
            val center = (d + 0.5f) * scale
            var lo = floor(center - support + 0.5f).toInt()
            var hi = ceil(center + support - 0.5f).toInt()
            if (lo < 0) lo = 0
            if (hi > srcMinor) hi = srcMinor
            if (hi <= lo) {
                lo = (center.toInt()).coerceIn(0, srcMinor - 1)
                hi = lo + 1
            }

            val n = hi - lo
            val weights = FloatArray(n)
            var total = 0f
            for (k in 0 until n) {
                val w = triangle(((lo + k) + 0.5f - center) / filterScale)
                weights[k] = w
                total += w
            }
            if (total == 0f) {
                weights[0] = 1f
                total = 1f
            }
            for (k in 0 until n) weights[k] /= total
            los[d] = lo
            ws[d] = weights
        }

        // major 축으로 병렬화한다. 각 major 행은 서로 독립이라 결과가 순차 실행과 동일하다.
        // (d 축으로 나누면 출력 인덱스마다 작업을 쪼개게 되어 오버헤드가 더 크다.)
        // iOS도 같은 이유로 DispatchQueue.concurrentPerform 을 쓴다.
        parallelFor(srcMajor) { m ->
            for (d in 0 until dstMinor) {
                val lo = los[d]
                val weights = ws[d]!!
                val base = (m * dstMinor + d) * 3
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in weights.indices) {
                    val w = weights[k]
                    val s = lo + k
                    r += src(m, s, 0) * w
                    g += src(m, s, 1) * w
                    b += src(m, s, 2) * w
                }
                out[base] = r
                out[base + 1] = g
                out[base + 2] = b
            }
        }
        return out
    }

    /**
     * 코어 수만큼 나눠 실행한다. 각 인덱스가 독립이라 결과는 순차 실행과 동일하다.
     *
     * 풀은 [POOL] 하나를 공유한다. 호출마다 새로 만들면 스레드 생성·소멸 비용이 붙는데,
     * 이 함수는 사진 한 장당 두 번(가로·세로 패스) 불린다.
     */
    private inline fun parallelFor(count: Int, crossinline body: (Int) -> Unit) {
        val workers = minOf(THREADS, count)
        if (workers <= 1) {
            for (i in 0 until count) body(i)
            return
        }
        val chunk = (count + workers - 1) / workers
        val latch = CountDownLatch(workers)
        for (w in 0 until workers) {
            val from = w * chunk
            val to = minOf(from + chunk, count)
            if (from >= to) {
                latch.countDown()
                continue
            }
            POOL.execute {
                try {
                    for (i in from until to) body(i)
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
    }

    private val THREADS = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

    /** 데몬 스레드로 둔다 — 단위 테스트가 이 풀 때문에 종료를 못 하는 일이 없도록. */
    private val POOL: ExecutorService = Executors.newFixedThreadPool(THREADS) { runnable ->
        Thread(runnable, "rfdetr-preprocess").apply { isDaemon = true }
    }

    private fun triangle(t: Float): Float {
        val a = if (t < 0f) -t else t
        return if (a < 1f) 1f - a else 0f
    }
}
