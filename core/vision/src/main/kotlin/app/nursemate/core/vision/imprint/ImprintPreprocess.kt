package app.nursemate.core.vision.imprint

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * 각인 판독 전처리 — **ML 레포 `infer.py` 와 한 줄씩 대응한다**(NM-485 DoD).
 *
 * 기준은 `models/imprint/20260907-crnn-ep60-s1/` 의 `code/train.py`(`_to_square`·`zoom_frame`·
 * `read_image`)와 `code/preprocess.py`(`v_clahe`)다. 함수 이름을 그쪽에 맞춰 두어 대조할 때
 * 짝이 바로 보이게 했다.
 *
 * ## 왜 OpenCV 인가
 * 기준 구현이 OpenCV 다. 손으로 옮기면 「같은 답」이 증명 과제가 되고, 틀려도 조용하다 —
 * CLAHE 가 미세하게 달라지면 글자 확률이 흔들려 0.949 채택 임계가 뒤집히고 각인이, 그다음
 * 후보가 달라진다. 크래시가 아니라 순위로만 나타난다. 대가는 arm64 다운로드 +8.9MB.
 *
 * ## 순서
 * ```
 * 크롭 → toSquare(128) → 배율 6 × 회전 24 = 144장
 *                          각 장: warpAffine(border 255) → applyClahe → [-1,1] 정규화
 * ```
 *
 * ⚠️ **`toSquare` 가 먼저다.** 기준 구현의 `read_one` 이 `_to_square(load_crop(path))` 로
 * 128 로 줄인 뒤 `read_image` 에 넘긴다. `zoom_frame` 의 주석은 「원본 해상도에서 자른다」고
 * 하지만 이 호출 경로에서는 이미 128 이다 — 고치지 않고 그대로 맞춘다. 답을 맞추는 것이
 * 목적이지 알고리즘을 개선하는 자리가 아니다.
 */
internal object ImprintPreprocess {

    /** 모델 입력 한 변. ONNX 입력은 `image [N,1,128,128]`. */
    const val SIDE = 128

    /** 회전 TTA 방향 수. */
    const val ROTATIONS = 24

    /**
     * 확대 TTA 배율.
     *
     * 감사셋 331면·3시드로 재현된 유일한 이득이다 — 정밀도 93% 지점에서 시드별 +17·+10·+4 알약.
     * 방향만 6배로 늘린 대조군은 시드 2 에서 −12 라 후보 수 효과가 아니다.
     */
    val ZOOMS = floatArrayOf(1.0f, 1.2f, 1.4f, 1.7f, 2.0f, 2.4f)

    /** 한 크롭이 만드는 TTA 장수 — 144. */
    const val SAMPLES = ROTATIONS * 6

    /** `_to_square` — 정사각으로 패딩한 뒤 [SIDE] 로 줄인다. */
    fun toSquare(gray: Mat, size: Int = SIDE): Mat {
        val padded = squarePad(gray)
        val out = Mat()
        Imgproc.resize(padded, out, Size(size.toDouble(), size.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        padded.release()
        return out
    }

    /**
     * `_square_pad` — 리사이즈 없이 정사각 패딩만.
     *
     * 채우는 값은 **좌상단 3×3 의 중앙값**이다. 배경이 완전한 흰색이 아닌 크롭에서 테두리가
     * 도드라지지 않게 하려는 것이라, 255 로 바꾸면 기준과 달라진다.
     */
    private fun squarePad(gray: Mat): Mat {
        val h = gray.rows()
        val w = gray.cols()
        if (h == w) return gray.clone()
        val s = maxOf(h, w)
        val fill = ImprintStats.medianOfCorner(gray)
        val top = (s - h) / 2
        val left = (s - w) / 2
        val out = Mat()
        Core.copyMakeBorder(
            gray,
            out,
            top,
            s - h - top,
            left,
            s - w - left,
            Core.BORDER_CONSTANT,
            Scalar(fill.toDouble())
        )
        return out
    }

    /**
     * `zoom_frame` — 알약 중심을 기준으로 [zoom] 배 확대해 정사각으로.
     *
     * 배율 1.0 은 `frame` 과 **정확히 같은 그림**이어야 한다. 기준선이 재현되지 않으면 비교가
     * 무의미하다.
     */
    fun zoomFrame(gray: Mat, zoom: Float, size: Int = SIDE): Mat {
        // 배율이 1 이하이거나 너무 잘게 잘리면 기준 그림으로 돌아간다 — 기준 구현과 같다.
        val framed = if (zoom <= 1.0f) null else cutAroundInk(gray, zoom)
        if (framed == null) return toSquare(gray, size)

        val out = Mat()
        Imgproc.resize(framed, out, Size(size.toDouble(), size.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        framed.release()
        return out
    }

    /**
     * `zoom_frame` 의 잘라내기 — 알약 중심 둘레를 `s / zoom` 만큼 남긴다.
     *
     * @return 잘라낸 정사각. 네 변 중 하나라도 4 미만이면 null(호출부가 기준 그림으로 돌아간다)
     */
    private fun cutAroundInk(gray: Mat, zoom: Float): Mat? {
        val p = squarePad(gray)
        val s = p.rows()
        val (cy, cx) = ImprintStats.centroidOfInk(p, s)
        val half = maxOf(8, (s / zoom / 2f).toInt())

        val y0 = cy - half
        val y1 = cy + half
        val x0 = cx - half
        val x1 = cx + half
        val cut = Mat(
            p,
            Rect(maxOf(0, x0), maxOf(0, y0), minOf(s, x1) - maxOf(0, x0), minOf(s, y1) - maxOf(0, y0))
        )

        val padTop = maxOf(0, -y0)
        val padBottom = maxOf(0, y1 - s)
        val padLeft = maxOf(0, -x0)
        val padRight = maxOf(0, x1 - s)
        val framed = if (padTop or padBottom or padLeft or padRight != 0) {
            Mat().also {
                Core.copyMakeBorder(
                    cut,
                    it,
                    padTop,
                    padBottom,
                    padLeft,
                    padRight,
                    Core.BORDER_CONSTANT,
                    Scalar(WHITE)
                )
            }
        } else {
            cut.clone()
        }
        p.release()

        return if (minOf(framed.rows(), framed.cols()) < 4) {
            framed.release()
            null
        } else {
            framed
        }
    }

    /** `cv2.getRotationMatrix2D` + `warpAffine(borderValue=255)`. 중심은 한 변의 절반. */
    fun rotate(frame: Mat, degrees: Double): Mat {
        val center = Point(frame.cols() / 2.0, frame.rows() / 2.0)
        val m = Imgproc.getRotationMatrix2D(center, degrees, 1.0)
        val out = Mat()
        Imgproc.warpAffine(
            frame,
            out,
            m,
            Size(frame.cols().toDouble(), frame.rows().toDouble()),
            Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT,
            Scalar(WHITE)
        )
        m.release()
        return out
    }

    /**
     * `apply_prep(r, "clahe")` — 알약 안쪽만 CLAHE, 배경은 흰색.
     *
     * ⚠️ **회전 뒤에 건다.** 학습이 그 순서였다. 먼저 걸고 회전하면 타일 경계가 함께 돌아가
     * 같은 그림이 나오지 않는다.
     *
     * 기준 구현은 마스크 밖을 0 으로 칠한 뒤(`_apply_mask`) 곧바로 255 로 덮는다
     * (`apply_prep` 끝줄). 중간의 0 은 남지 않으므로 여기서는 한 번에 255 로 칠한다.
     */
    fun applyClahe(gray: Mat): Mat {
        val mask = maskPill(gray)
        val filled = fillBackground(gray, mask)
        val out = Mat()
        Imgproc.createCLAHE(CLAHE_CLIP, Size(CLAHE_TILE, CLAHE_TILE)).apply(filled, out)
        filled.release()
        // 마스크 밖 = 흰색. setTo 는 마스크가 0 이 아닌 곳에 칠하므로 반전해 넘긴다.
        val outside = Mat()
        Core.compare(mask, Scalar(0.0), outside, Core.CMP_EQ)
        out.setTo(Scalar(WHITE), outside)
        outside.release()
        mask.release()
        return out
    }

    /**
     * `_mask_pill` — 흰 배경에서 알약 안쪽 마스크(0/1).
     *
     * 문턱이 245 인 것은 선명화·JPEG 가 배경을 255 에서 살짝 흔들 수 있어서다.
     */
    private fun maskPill(gray: Mat): Mat {
        val m = Mat()
        Imgproc.threshold(gray, m, MASK_THRESHOLD, 1.0, Imgproc.THRESH_BINARY_INV)

        val kernel = Mat.ones(5, 5, CvType.CV_8U)
        Imgproc.morphologyEx(m, m, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()

        keepLargestComponent(m)
        fillHolesFromCorner(m)
        return m
    }

    /** `connectedComponentsWithStats` 에서 배경을 뺀 최대 면적 하나만 남긴다. */
    private fun keepLargestComponent(m: Mat) {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(m, labels, stats, centroids, 8)
        if (count > 1) {
            var best = 1
            var bestArea = -1.0
            for (i in 1 until count) {
                val area = stats.get(i, Imgproc.CC_STAT_AREA)[0]
                if (area > bestArea) {
                    bestArea = area
                    best = i
                }
            }
            Core.compare(labels, Scalar(best.toDouble()), m, Core.CMP_EQ)
            // compare 는 255 를 쓴다. 기준 구현은 0/1 이므로 되돌린다.
            Core.divide(m, Scalar(255.0), m)
        }
        labels.release()
        stats.release()
        centroids.release()
    }

    /**
     * `floodFill((0,0), 1)` 뒤 `m | (1 - ff)` — 알약 안쪽의 구멍을 메운다.
     *
     * ⚠️ 좌상단이 이미 1 이면 floodFill 이 그 덩어리를 1 로 덮어 `ff == m` 이 되고,
     * `1 - ff` 가 배경 전체를 가리켜 **마스크가 전부 1** 이 된다. 기준 구현이 그렇게
     * 동작하므로 여기서도 막지 않는다.
     */
    private fun fillHolesFromCorner(m: Mat) {
        val flood = m.clone()
        val ffMask = Mat.zeros(m.rows() + 2, m.cols() + 2, CvType.CV_8U)
        Imgproc.floodFill(flood, ffMask, Point(0.0, 0.0), Scalar(1.0))
        ffMask.release()

        // holes = 1 - flood, 결과 = m | holes
        val holes = Mat()
        Core.subtract(Mat.ones(m.size(), CvType.CV_8U), flood, holes)
        Core.bitwise_or(m, holes, m)
        holes.release()
        flood.release()
    }

    /** `_fill_bg` — 배경을 **알약 안쪽 중앙값**으로 메운다. 윤곽선이 가짜 구조로 잡히는 걸 막는다. */
    private fun fillBackground(gray: Mat, mask: Mat): Mat {
        val out = gray.clone()
        val median = ImprintStats.medianInside(gray, mask)
        val outside = Mat()
        Core.compare(mask, Scalar(0.0), outside, Core.CMP_EQ)
        out.setTo(Scalar(median.toDouble()), outside)
        outside.release()
        return out
    }

    private const val WHITE = 255.0
    private const val MASK_THRESHOLD = 244.0 // `gray < 245` 와 같다(THRESH_BINARY_INV 는 초과 비교)
    private const val CLAHE_CLIP = 3.0
    private const val CLAHE_TILE = 8.0
}
