package app.nursemate.core.vision

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 검출기(RF-DETR-Seg, LiteRT)가 **같은 기기에서 GPU 와 CPU 로 같은 답**을 내는지 본다 — NM-628.
 *
 * 각인 · 마크는 기준 구현 값과 맞대는 대조 테스트가 있는데 검출은 없었다(ORT 시절 `DetectorEpTest` 는 EP 측정용).
 * 여기서는 CPU(fp32)를 기준으로 GPU(FP32 계약)가 개수 · 상자 · 점수 · 마스크 크롭까지 같은지 본다.
 * CPU 결과가 맞는 배치인지(NHWC · 마스크 쿼리 마지막 축)는 검출 개수와 크롭 크기가 말해 준다 —
 * 배치가 틀리면 0개이거나 크롭이 엉뚱한 크기로 나온다.
 *
 * ## 모델 · 사진이 없으면 건너뛴다
 * 둘 다 저장소에 넣지 않는다. 사진은 ML 레포 예제(환자 정보 없음)다.
 * ```
 * D=/sdcard/Android/data/app.nursemate.core.vision.test/files
 * adb push app/src/main/assets/rfdetr_seg_small_f16w.tflite $D/
 * adb push ../ML/models/seg/20260610-rfdetr-seg-small/sample_pouch.jpg $D/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class DetectorParityTest {

    @After
    fun restore() {
        VisionBackend.allowGpu = { true }
    }

    @Test
    fun `GPU_와_CPU_의_검출이_같다`() {
        val dir = InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null)
        val model = File(dir, PillDetector.MODEL_FILE_NAME)
        val photo = File(dir, PHOTO)
        assumeTrue("모델 · 사진이 없어 건너뛴다", model.isFile && photo.isFile)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(photo.path)) { "사진을 못 읽었다" }

        VisionBackend.allowGpu = { true }
        val gpu = runBlocking {
            PillDetector(model).use {
                // GPU 로 못 열려 CPU 로 대체됐으면 CPU 끼리 맞댄 셈이 된다 — 그건 통과가 아니다.
                assertTrue("GPU 로 열리지 않았다(대체됨) — logcat 「LiteRt」", it.onGpu)
                it.detect(bitmap)
            }
        }
        VisionBackend.allowGpu = { false }
        val cpu = runBlocking { PillDetector(model).use { it.detect(bitmap) } }

        Log.i(
            TAG,
            "${bitmap.width}×${bitmap.height} · CPU ${cpu.pills.size}개 ${cpu.timings.inferenceMs}ms · " +
                "GPU ${gpu.pills.size}개 ${gpu.timings.inferenceMs}ms"
        )
        assertTrue("CPU 가 하나도 못 찾았다 — 입출력 배치를 의심한다", cpu.pills.isNotEmpty())
        assertEquals("검출 개수", cpu.pills.size, gpu.pills.size)

        for (c in cpu.pills) {
            val g = gpu.pills.maxBy { it.detection.iou(c.detection) }
            val iou = g.detection.iou(c.detection)
            Log.i(
                TAG,
                "q${c.detection.query} 점수 ${c.detection.score} / ${g.detection.score} · IoU $iou · " +
                    "크롭 ${c.crop.width}×${c.crop.height} / ${g.crop.width}×${g.crop.height}"
            )
            assertTrue("상자 IoU $iou", iou >= MIN_IOU)
            assertEquals("점수", c.detection.score, g.detection.score, SCORE_TOLERANCE)
            assertEquals("크롭 폭", c.crop.width.toFloat(), g.crop.width.toFloat(), c.crop.width * CROP_TOLERANCE)
            assertEquals("크롭 높이", c.crop.height.toFloat(), g.crop.height.toFloat(), c.crop.height * CROP_TOLERANCE)
        }
    }

    private companion object {
        const val TAG = "DetectorParity"
        const val PHOTO = "sample_pouch.jpg"

        /** ML 공식 세트에서 fp32 대비 상자 IoU 0.998 이었다 — 같은 기기 CPU 대비는 그보다 가까워야 한다. */
        const val MIN_IOU = 0.98f
        const val SCORE_TOLERANCE = 0.02f

        /** 마스크 경계가 한 칸(144 격자) 움직이면 크롭이 몇 % 바뀐다. */
        const val CROP_TOLERANCE = 0.05f
    }
}
