package app.nursemate.core.vision.mark

import android.graphics.Bitmap
import android.os.PowerManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * 마크 판독 소요 측정 — NM-515 「앱 용량과 저사양 기기 추론 시간을 측정하고 허용치를 정했다」.
 *
 * ## 실측 (Galaxy S24 · SM-S921N · Exynos 2400 · 4스레드 · 8방향)
 * ORT 1.30 + WebGPU (NM-534). fp16 모델이다.
 * ```
 * 세션 생성   약 150 ms   (한 번만)
 * 전처리       약 80 ms
 * 추론        약 180 ms
 * 디코딩         1 ms 미만
 * ─────────────────────────
 * 한 면       약 260 ms
 * ```
 * 각인(144장 · 1796 ms)의 1/7 이다 — 8장뿐이고 모델도 작다. 검출 크롭이 여럿이면 면마다 든다.
 *
 * | 실행 공급자 | 추론 |
 * |---|---|
 * | CPU 4스레드 | 601 ms |
 * | **WebGPU** | **178 ms** |
 * | (참고) fp32 · WebGPU | 292 ms |
 *
 * ⚠️ **에뮬레이터 값은 믿지 말 것.** 데스크톱 CPU 위에서 돌아 실기기와 다르고, Apple Silicon
 * 에뮬레이터는 OpenCV 4.12 의 SVE 경로에서 `SIGILL` 로 죽는다(NM-531 — 에뮬 결함이다).
 *
 * ## 모델이 없으면 건너뛴다
 * ```
 * adb push mark_species_fp16.onnx /sdcard/Android/data/app.nursemate.core.vision.test/files/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class MarkReaderBenchmark {

    @Test
    fun `한_면_판독_소요를_단계별로_잰다`() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        val context = InstrumentationRegistry.getInstrumentation().context

        // 발열 상태에서 잰 값은 대표성이 없다. 기기를 더 굽지 않고 접는다.
        val status = context.getSystemService(PowerManager::class.java)?.currentThermalStatus
        assumeTrue("발열 상태 $status — 재지 않는다", status == PowerManager.THERMAL_STATUS_NONE)

        val model = File(context.getExternalFilesDir(null), MarkReader.MODEL_FILE_NAME)
        assumeTrue("모델이 없어 건너뛴다", model.isFile && model.length() > 0)

        val fixtures = JSONObject(
            context.assets.open("mark/fixtures.json").use { it.readBytes().decodeToString() }
        ).getJSONObject("cases")
        val name = fixtures.keys().next()
        val case = fixtures.getJSONObject(name)

        val openStart = System.nanoTime()
        val reader = MarkReader(model)
        val openMs = (System.nanoTime() - openStart) / 1_000_000

        try {
            val crop = loadCrop(context, name, case)
            val gray = MarkPreprocess.toGrayOnWhite(crop)
            try {
                // 첫 회는 네이티브 준비·셰이더 컴파일이 섞여 대표성이 없다. 재지 않고 버린다.
                reader.readGray(gray)

                val runs = (1..ROUNDS).map { reader.readGray(gray).timings }
                Log.i(TAG, "세션 생성 ${openMs}ms (한 번만) · 모델 ${model.length() / 1024 / 1024}MB")
                Log.i(TAG, "전처리  중앙값 ${runs.map { it.preprocessMs }.median()}ms")
                Log.i(TAG, "추론    중앙값 ${runs.map { it.inferenceMs }.median()}ms")
                Log.i(TAG, "디코딩  중앙값 ${runs.map { it.decodeMs }.median()}ms")
                Log.i(TAG, "한 면 합계 중앙값 ${runs.map { it.totalMs }.median()}ms")
            } finally {
                gray.release()
            }
        } finally {
            reader.close()
        }
    }

    private fun loadCrop(context: android.content.Context, name: String, case: JSONObject): Bitmap {
        val bytes = context.assets.open("mark/$name.rgba").use { it.readBytes() }
        val bitmap = Bitmap.createBitmap(case.getInt("width"), case.getInt("height"), Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return bitmap
    }

    private fun List<Long>.median(): Long = sorted()[size / 2]

    private companion object {
        const val TAG = "NM515"
        const val ROUNDS = 5
    }
}
