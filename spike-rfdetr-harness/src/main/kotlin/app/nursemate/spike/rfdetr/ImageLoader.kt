package app.nursemate.spike.rfdetr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * 사진 로딩.
 *
 * ⚠️ **추론과 크롭이 반드시 같은 좌표계를 써야 한다.**
 * iOS는 이걸 어겨서 버그를 냈다(커밋 c4f4b90): 추론은 회전 정규화된 이미지로, 크롭은 회전 안 된
 * raw 버퍼로 해서 **BBOX는 정상인데 크롭만 엉뚱한 영역**이 나왔다.
 * 여기서는 EXIF 회전을 항상 픽셀에 굽고, 검출 좌표를 0~1 정규화로 다루어 해상도와 무관하게 만든다.
 *
 * ## 왜 두 단계로 나누는가 (성능)
 * 추론에 필요한 건 **576×576 뿐**이다. 원본을 2048까지 디코딩할 이유가 없다.
 * 2048은 **마스크 크롭을 원본 해상도로 뜰 때만** 필요하다.
 *
 * 그래서 [loadForInference]로 작게 읽어 검출하고, 검출이 나왔을 때만 [loadForCrop]으로
 * 큰 이미지를 읽는다. 검출이 0개면 큰 디코딩 자체가 발생하지 않는다.
 *
 * 실측(Galaxy S24, 4000×3000 원본): 2048 디코딩 491 ms → 576 경로 대폭 감소.
 */
object ImageLoader {

    /**
     * 추론용.
     *
     * ⚠️ **최장변 2048 축소는 정확도 요건이지 성능 여유가 아니다.**
     * GT(CoreML)를 만든 Python 파이프라인이 `최장변 2048 bilinear 축소 → 576`을 하므로,
     * 같은 경로를 밟아야 결과가 일치한다.
     *
     * 한때 `inSampleSize`로 576 근처까지 먼저 줄여 디코딩을 빠르게 하려 했으나(490 → 135 ms),
     * **GT 대비 박스 IoU가 1.0000 → 0.9766으로 떨어졌다**(검증 샘플 12건 전부 열세).
     * `inSampleSize`는 2의 거듭제곱 박스 평균이라 축소 경로가 달라지기 때문이다.
     * 속도를 얻자고 iOS와의 결과 일치를 깨뜨릴 수는 없어 되돌렸다.
     */
    fun loadForInference(context: Context, uri: Uri): Bitmap =
        decode(context, uri, targetLongestSide = RfDetrSpec.MAX_DIMENSION)

    /**
     * 크롭용. 최장변 2048 (iOS CameraPicker와 동일한 계약).
     * 검출이 있을 때만 호출한다.
     */
    fun loadForCrop(context: Context, uri: Uri): Bitmap =
        decode(context, uri, targetLongestSide = RfDetrSpec.MAX_DIMENSION)

    private fun decode(context: Context, uri: Uri, targetLongestSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "이미지를 읽지 못했습니다" }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(
                maxOf(bounds.outWidth, bounds.outHeight), targetLongestSide,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("이미지 디코딩 실패")

        val rotation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).rotationDegrees
        } ?: 0

        val upright = if (rotation != 0) {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true,
            ).also { if (it != decoded) decoded.recycle() }
        } else {
            decoded
        }

        // 크롭 경로만 정확한 상한을 맞춘다. 추론 경로는 어차피 576으로 다시 리샘플되므로
        // 여기서 한 번 더 줄일 필요가 없다(줄이면 오히려 정보만 잃는다).
        return if (targetLongestSide == RfDetrSpec.MAX_DIMENSION) {
            upright.limitLongestSide(targetLongestSide)
        } else {
            upright
        }
    }

    /**
     * 목표 크기의 **2배 이상**을 유지하는 선까지만 반으로 줄인다.
     *
     * `inSampleSize`는 2×2 블록 평균(박스 필터)이라 그 자체로 안티에일리어싱이 되지만,
     * **2의 거듭제곱으로만 줄일 수 있다**는 제약이 있다. 목표(576)에 바짝 붙이면 남은 배율이
     * 1.3배 수준밖에 안 되어 [RfDetrPreprocess]의 삼각 필터가 제 역할을 못 하고,
     * 그만큼 최종 텐서가 달라진다.
     *
     * ⚠️ 실측: 목표에 바짝 붙였더니(3024×4032 → 756) 기존 2048 경로 대비
     * **박스 IoU가 0.9644까지 떨어졌다.** 2배 여유를 두면 0.99 이상으로 회복된다.
     * 검출 개수는 어느 쪽이든 동일했으나, 정확도가 최우선이므로 여유를 둔다.
     */
    private fun sampleSizeFor(longest: Int, target: Int): Int {
        var sample = 1
        var current = longest
        while (current / 2 >= target * 2) {
            current /= 2
            sample *= 2
        }
        return sample
    }

    private fun Bitmap.limitLongestSide(max: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= max) return this
        val ratio = max.toFloat() / longest
        val target = Bitmap.createScaledBitmap(
            this, (width * ratio).toInt(), (height * ratio).toInt(), true,
        )
        if (target != this) recycle()
        return target
    }
}
