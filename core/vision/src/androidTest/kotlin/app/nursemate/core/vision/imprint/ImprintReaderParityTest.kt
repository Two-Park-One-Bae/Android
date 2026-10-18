package app.nursemate.core.vision.imprint

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nursemate.core.vision.GpuOverride
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * 끝까지의 답이 기준 구현과 같은지 — NM-485 DoD 의 본체.
 *
 * [ImprintPreprocessParityTest] 가 전처리 144장이 바이트까지 같음을 보였다. 여기서는 그 위에
 * 모델을 태워 **같은 답이 나오는지**까지 본다.
 *
 * ## 모델을 저장소에 넣지 않는다
 * 조각 다섯(p1~p5, 합 23MB)은 `.gitignore` 대상이다. 기기에 밀어 넣고 읽는다:
 * ```
 * adb push app/src/main/assets/p?_*.tflite /sdcard/Android/data/app.nursemate.core.vision.test/files/
 * ```
 *
 * ⚠️ **모델이 없으면 건너뛴다 — 통과가 아니다.** `assumeTrue` 는 리포트에 skipped 로 남는다.
 * 조용히 초록불이 되면 「대조했다」고 잘못 읽힌다.
 *
 * ## 기준값
 * `sample_crop.png` → `YS`, best_pair 점수 0.969883, 회전 90°, 배율 1.0.
 * `INFO.md` 의 「결과 `YS` 0.970」과 같다. LiteRT G5(NM-628)도 이 값에 맞아야 한다 — 허용 오차는 아래.
 */
@RunWith(AndroidJUnit4::class)
class ImprintReaderParityTest {

    @Test
    fun `sample_crop_의_답이_기준과_같다`() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        // 모델을 열기 전이어야 한다 — 연 뒤에 바꾸면 이미 열린 가속기로 돈다.
        val path = GpuOverride.apply()

        val context = InstrumentationRegistry.getInstrumentation().context
        val parts = ImprintReader.PART_FILE_NAMES.map { File(context.getExternalFilesDir(null), it) }
        assumeTrue(
            "조각이 없어 건너뛴다 — adb push p?_*.tflite ${context.getExternalFilesDir(null)}/",
            parts.all { it.isFile && it.length() > 0 }
        )

        val bytes = context.assets.open("imprint/input_gray.bin").use { it.readBytes() }
        val gray = Mat(ImprintPreprocess.SIDE, ImprintPreprocess.SIDE, CvType.CV_8UC1)
            .also { it.put(0, 0, bytes) }

        val result = ImprintReader(parts).use { it.readGray(gray) }
        gray.release()

        assertEquals("[$path] 확신 글자", "YS", result.imprint)
        assertEquals("[$path] best_pair", "YS", result.pair)
        assertEquals("[$path] best_pair 점수", 0.969883f, result.score, 1e-4f)
    }
}
