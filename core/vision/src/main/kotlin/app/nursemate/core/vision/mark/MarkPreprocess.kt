package app.nursemate.core.vision.mark

import android.graphics.Bitmap
import app.nursemate.core.vision.imprint.ImprintPreprocess
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * 마크 판독 전처리 — 기준은 ML 레포 `models/mark/20260925-convnext-species/mark_species_infer.py` 다.
 *
 * ## 각인과 같은 함수를 쓴다
 * 정본 파이썬에서 `_to_square` · `_mask_pill` · `clahe` 는 각인 쪽과 **글자 하나 다르지 않다**.
 * 그래서 [ImprintPreprocess] 의 것을 그대로 부른다 — 베껴 두면 한쪽만 고쳐지는 날이 온다.
 * 크기만 128 이 아니라 [SIDE] 다.
 *
 * 여기서 새로 하는 일은 둘뿐이다.
 * - **알파 합성 순서** — 아래 참조
 * - **ImageNet 정규화** — 각인 모델은 1채널 0~1 을 받지만 이 모델은 3채널 정규화값을 받는다
 *
 * ## ⚠️ 알파는 **컬러에서** 합성한다
 * 정본 `load_face` 는 이 순서다.
 * ```python
 * al = a[:, :, 3:4].astype(np.float32) / 255
 * a = (a[:, :, :3].astype(np.float32) * al + 255 * (1 - al)).astype(np.uint8)   # 컬러에서 합성
 * return _gray(a)                                                               # 그 뒤 흑백
 * ```
 * 흑백으로 먼저 바꾸고 합성해도 **수식은 같다** — 흑백 계수(0.299·0.587·0.114)의 합이 1이라
 * `gray(c·a + 255(1−a)) = a·gray(c) + (1−a)·255` 가 된다. 그런데 `cvtColor` 는 부동소수가
 * 아니라 **15비트 고정소수점**으로 돌아 반올림 지점이 달라지고, 반투명 가장자리 화소가 ±1
 * 어긋난다. iOS 도 같은 자리를 맞췄다(iOS#178).
 *
 * ⚠️ `ImprintReader.toGrayOnWhite` 는 **반대 순서**로 되어 있다. 각인 대조 테스트는 흑백
 * 픽스처에서 시작해 이 단계를 보지 않아 드러나지 않았다. 각인 답에 영향이 있는지는 따로 볼 일이라
 * 여기서는 건드리지 않는다.
 *
 * ## 세그 크롭은 미리 곱한 알파다
 * `PillDetector` 가 주는 크롭은 Android 가 저장할 때 곱해 둔 것이라 [toGrayOnWhite] 가 풀어서
 * 받는다. 푸는 것은 `c = c_pre / a` 라 **되돌릴 수 없는 나눗셈**이므로, 반투명 가장자리 화소는
 * 정본과 ±1 다를 수 있다. 알약 안쪽(알파 255)과 바깥(알파 0)은 정확히 같다.
 */
internal object MarkPreprocess {

    /** 모델 입력 한 변. `mark_species_infer.py` 의 `meta["img"]`. */
    const val SIDE = 224

    /** 8방향 45° 간격. 각인(24방향)과 달리 이 모델은 8방향으로 학습됐다. */
    const val ROTATIONS = 8

    /** 한 장의 float 개수 — 3채널이다. */
    const val PLANE = 3 * SIDE * SIDE

    /** `[8,3,224,224]` 한 배치. */
    const val BATCH = ROTATIONS * PLANE

    /**
     * `load_face` — 투명 배경 크롭을 흰 바탕 흑백으로.
     *
     * 합성을 **컬러에서** 하는 이유는 이 파일 KDoc 참조.
     */
    fun toGrayOnWhite(crop: Bitmap): Mat {
        val rgba = Mat()
        // ⚠️ **미리 곱한 알파를 풀어서 받는다.** Android 는 `createBitmap(IntArray, …)` 입력을
        //    비프리멀티플라이로 보고 저장할 때 곱하므로(`PillDetector.cropWithMask` 주석),
        //    그대로 받으면 아래 합성에서 알파가 **두 번** 곱해져 가장자리가 어두워진다.
        //    비트맵이 스스로 어느 쪽인지 알고 있으니 그걸 따른다.
        Utils.bitmapToMat(crop, rgba, crop.isPremultiplied)

        val count = rgba.rows() * rgba.cols()
        val src = ByteArray(count * 4)
        rgba.get(0, 0, src)

        // ⚠️ **합성을 OpenCV 산술로 하지 않는다.** 정본은 `.astype(np.uint8)` 로 **절삭**하는데
        //    `convertTo(CV_8U)` 는 **반올림**한다. 0.5 를 빼서 내림을 흉내 내면 불투명 화소가
        //    망가진다 — 알파 255 이면 합성값이 정확히 정수라 255.0 → 254.5 → 254 가 된다.
        //    화소 루프가 절삭을 그대로 쓰므로 어긋날 자리가 없다. 크롭은 한 면에 한 번뿐이다.
        val bgr = ByteArray(count * 3)
        for (i in 0 until count) {
            val a = (src[i * 4 + 3].toInt() and 0xFF) / 255f
            val inv = WHITE_F * (1f - a)
            // split 순서는 R·G·B·A, cvtColor(BGR2GRAY) 는 B·G·R 을 받으므로 뒤집어 넣는다.
            for (c in 0 until 3) {
                val v = (src[i * 4 + c].toInt() and 0xFF) * a + inv
                bgr[i * 3 + (2 - c)] = v.toInt().toByte() // toInt() 가 절삭이다
            }
        }

        val merged = Mat(rgba.rows(), rgba.cols(), CvType.CV_8UC3)
        merged.put(0, 0, bgr)
        val gray = Mat()
        Imgproc.cvtColor(merged, gray, Imgproc.COLOR_BGR2GRAY)

        rgba.release()
        merged.release()
        return gray
    }

    /** `_to_square(g, 224)` — 각인과 같은 함수, 크기만 다르다. */
    fun toSquare(gray: Mat): Mat = ImprintPreprocess.toSquare(gray, SIDE)

    /** `getRotationMatrix2D` + `warpAffine(borderValue=255)` — 각인과 같다. */
    fun rotate(frame: Mat, degrees: Double): Mat = ImprintPreprocess.rotate(frame, degrees)

    /** 알약 안쪽만 CLAHE(clip 3.0 · 8×8), 배경은 흰색 — 각인과 같다. */
    fun applyClahe(gray: Mat): Mat = ImprintPreprocess.applyClahe(gray)

    /**
     * `to_tensor3` — 흑백을 3채널로 복제하고 ImageNet 정규화해서 [out] 에 쓴다.
     *
     * ```python
     * a = np.stack([g.astype(np.float32) / 255.0] * 3, 0)
     * return (a - MEAN[:, None, None]) / STD[:, None, None]
     * ```
     *
     * 채널이 바깥이라(CHW) 채널마다 평면 하나씩 채운다. 세 채널의 값이 같고 평균·표준편차만
     * 다르므로 원본 화소를 한 번만 읽는다.
     *
     * ⚠️ **iOS CoreML 판은 이걸 모델 안에서 한다**(입력이 `gray` [N,1,224,224] 0~1). 우리가 쓰는
     * ONNX 는 입력이 `image` [N,3,224,224] 라 앱이 해야 한다. 같은 모델이지만 계약이 다르다.
     *
     * @param offset [out] 안에서 이 장이 시작하는 위치
     */
    fun writeNormalized(gray: Mat, out: FloatArray, offset: Int) {
        val pixels = ByteArray(SIDE * SIDE)
        gray.get(0, 0, pixels)
        for (c in 0 until 3) {
            val mean = MEAN[c]
            val std = STD[c]
            val base = offset + c * SIDE * SIDE
            for (i in pixels.indices) {
                val v = (pixels[i].toInt() and 0xFF) / 255f
                out[base + i] = (v - mean) / std
            }
        }
    }

    private const val WHITE_F = 255f

    /** ImageNet 통계 — `mark_species_infer.py` 의 `MEAN`·`STD`. */
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
}
