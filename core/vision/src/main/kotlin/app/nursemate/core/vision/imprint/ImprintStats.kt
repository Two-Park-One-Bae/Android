package app.nursemate.core.vision.imprint

import org.opencv.core.Mat

/**
 * numpy 와 같은 값을 내는 통계 도우미 — 각인 전처리 전용.
 *
 * [ImprintPreprocess] 에 같이 두었더니 함수가 14개가 되어 떼어 냈다. 여기 있는 것은 전부
 * **numpy 의 규칙을 그대로 따르는 것이 목적**이라, 「더 나은 방법」으로 바꾸면 기준 구현과
 * 답이 갈린다.
 */
internal object ImprintStats {

    /**
     * `int(np.median(g[:3, :3]))` — 좌상단 3×3 의 중앙값.
     *
     * ⚠️ numpy 의 짝수 규칙을 따른다. 원소가 짝수면 가운데 둘의 **평균**이고, 그 뒤 `int()` 가
     * 절사한다. 홀수만 생각해 가운데 값을 쓰면 크롭이 3픽셀보다 작을 때 어긋난다.
     */
    fun medianOfCorner(gray: Mat): Int {
        val h = minOf(3, gray.rows())
        val w = minOf(3, gray.cols())
        val values = IntArray(h * w)
        val buffer = ByteArray(1)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                gray.get(y, x, buffer)
                values[i++] = buffer[0].toInt() and 0xFF
            }
        }
        values.sort()
        return medianOfSorted(values, values.size)
    }

    /**
     * `_fill_bg` 의 `int(np.median(gray[mask > 0]))`. 마스크가 비면 0.
     *
     * 화소가 수만 개라 정렬하지 않고 히스토그램으로 센다 — uint8 이라 정확히 같은 값이 나온다.
     */
    fun medianInside(gray: Mat, mask: Mat): Int {
        val histogram = IntArray(256)
        val rows = gray.rows()
        val cols = gray.cols()
        val grayRow = ByteArray(cols)
        val maskRow = ByteArray(cols)
        var count = 0
        for (y in 0 until rows) {
            gray.get(y, 0, grayRow)
            mask.get(y, 0, maskRow)
            for (x in 0 until cols) {
                if (maskRow[x].toInt() != 0) {
                    histogram[grayRow[x].toInt() and 0xFF]++
                    count++
                }
            }
        }
        if (count == 0) return 0
        return medianOfHistogram(histogram, count)
    }

    /**
     * `zoom_frame` 의 중심 — `(gray < 250)` 인 화소의 평균 좌표.
     *
     * 화소가 50개 이하면 중심을 믿지 않고 그림 한가운데를 쓴다. `int()` 는 절사이고 좌표가
     * 음수가 아니므로 내림과 같다.
     */
    fun centroidOfInk(padded: Mat, side: Int): Pair<Int, Int> {
        val cols = padded.cols()
        val row = ByteArray(cols)
        var sumY = 0L
        var sumX = 0L
        var count = 0
        for (y in 0 until padded.rows()) {
            padded.get(y, 0, row)
            for (x in 0 until cols) {
                if ((row[x].toInt() and 0xFF) < INK_THRESHOLD) {
                    sumY += y
                    sumX += x
                    count++
                }
            }
        }
        if (count <= MIN_INK_PIXELS) return (side / 2) to (side / 2)
        return (sumY / count).toInt() to (sumX / count).toInt()
    }

    /** 정렬된 배열의 numpy 식 중앙값. */
    fun medianOfSorted(sorted: IntArray, count: Int): Int = if (count % 2 == 1) {
        sorted[count / 2]
    } else {
        (sorted[count / 2 - 1] + sorted[count / 2]) / 2
    }

    /** 히스토그램에서 같은 규칙으로 중앙값을 뽑는다. */
    fun medianOfHistogram(histogram: IntArray, count: Int): Int {
        fun valueAt(rank: Int): Int {
            var seen = 0
            for (value in 0..255) {
                seen += histogram[value]
                if (seen > rank) return value
            }
            return 255
        }
        return if (count % 2 == 1) {
            valueAt(count / 2)
        } else {
            (valueAt(count / 2 - 1) + valueAt(count / 2)) / 2
        }
    }

    private const val INK_THRESHOLD = 250
    private const val MIN_INK_PIXELS = 50
}
