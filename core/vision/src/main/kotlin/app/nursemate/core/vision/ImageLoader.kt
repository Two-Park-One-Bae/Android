package app.nursemate.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * 촬영·선택한 사진을 검출이 요구하는 형태로 읽는다.
 *
 * ⚠️ **추론과 크롭이 반드시 같은 비트맵을 써야 한다.**
 * iOS는 이걸 어겨서 버그를 냈다(커밋 `c4f4b90`): 추론은 회전 정규화된 이미지로, 크롭은 회전이
 * 적용되지 않은 raw 버퍼로 해서 **BBOX는 정상인데 크롭만 엉뚱한 영역**이 나왔다.
 * 그래서 여기서는 진입점을 [load] 하나로 두고, EXIF 회전을 항상 픽셀에 굽는다.
 * (스파이크에는 `loadForInference`/`loadForCrop` 두 개가 있었으나 구현이 동일했다.)
 */
object ImageLoader {

    /**
     * EXIF 회전을 적용하고, **중앙 정사각으로 자른 뒤**, 한 변이
     * [RfDetrSpec.MAX_DIMENSION] 이하가 되게 줄인 비트맵을 돌려준다.
     *
     * ## 왜 정사각으로 자르는가 — 이게 모델 정확도의 전제다
     * 모델 입력은 576×576이고 전처리는 **stretch resize**(종횡비 무시)다. 4:3 사진을 그대로
     * 넣으면 가로세로가 찌그러진 채 학습 분포를 벗어난다. iOS는 촬영·갤러리 **양쪽 모두**
     * `CameraPicker.squareMode = true`로 **중앙 정사각 크롭 → 2048 축소** 순서를 밟는다
     * (`CameraPicker.cropToSquare` → `downscaled`). 원본이 이미 정사각이라 stretch가
     * 사실상 균일 축소가 되는 구조다.
     *
     * 촬영 화면의 정사각 가이드(`spec/design/DESIGN.pen` ① 촬영)가 딤으로 바깥을 가리는 것도
     * **잘려나갈 영역을 미리 보여주는 것**이지 단순 장식이 아니다.
     *
     * ⚠️ 순서를 지켜야 한다 — **크롭 먼저, 축소 나중**. 바꾸면 잘리는 영역이 달라진다.
     *
     * ## 2048 상한은 정확도 요건이지 성능 여유가 아니다
     * 학습 파이프라인이 `2048 축소 → 576`을 거치므로 같은 경로를 밟아야 결과가 일치한다.
     * 한때 [android.graphics.BitmapFactory.Options.inSampleSize]로 576 근처까지 먼저 줄여
     * 디코딩을 빠르게 하려 했으나(490 → 135 ms), **박스 IoU가 1.0000 → 0.9766으로 떨어졌다**
     * (검증 샘플 12건 전부 열세). `inSampleSize`는 2의 거듭제곱 박스 평균이라 축소 경로가
     * 달라지기 때문이다. 속도를 얻자고 iOS와의 결과 일치를 깨뜨릴 수는 없어 되돌렸다.
     */
    fun load(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "이미지를 읽지 못했습니다" }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(
                longest = maxOf(bounds.outWidth, bounds.outHeight),
                target = RfDetrSpec.MAX_DIMENSION
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("이미지 디코딩에 실패했습니다")

        val rotation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).rotationDegrees
        } ?: 0

        val upright = if (rotation != 0) {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                Matrix().apply { postRotate(rotation.toFloat()) },
                true
            ).also { if (it != decoded) decoded.recycle() }
        } else {
            decoded
        }

        // iOS CameraPicker.deliver() 와 같은 순서: 정사각 크롭 → 축소.
        return upright.cropToSquare().limitLongestSide(RfDetrSpec.MAX_DIMENSION)
    }

    /** 짧은 변 길이의 정사각형을 **중앙에서** 잘라낸다 (iOS `cropToSquare` 미러). */
    private fun Bitmap.cropToSquare(): Bitmap {
        val side = minOf(width, height)
        if (width == height) return this
        val left = (width - side) / 2
        val top = (height - side) / 2
        val square = Bitmap.createBitmap(this, left, top, side, side)
        if (square != this) recycle()
        return square
    }

    /**
     * 목표 크기의 **2배 이상**을 유지하는 선까지만 반으로 줄인다.
     *
     * `inSampleSize`는 2×2 블록 평균(박스 필터)이라 그 자체로 안티에일리어싱이 되지만,
     * **2의 거듭제곱으로만 줄일 수 있다**는 제약이 있다. 목표(2048)에 바짝 붙이면 남은 배율이
     * 얼마 안 남아 [RfDetrPreprocess]의 삼각 필터가 제 역할을 못 하고, 그만큼 최종 텐서가 달라진다.
     *
     * ⚠️ 실측: 목표에 바짝 붙였더니(3024×4032 → 756) 기존 2048 경로 대비
     * **박스 IoU가 0.9644까지 떨어졌다.** 2배 여유를 두면 0.99 이상으로 회복된다.
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
            this,
            (width * ratio).toInt(),
            (height * ratio).toInt(),
            true
        )
        if (target != this) recycle()
        return target
    }
}
