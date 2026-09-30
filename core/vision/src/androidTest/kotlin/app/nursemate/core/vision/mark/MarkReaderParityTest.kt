package app.nursemate.core.vision.mark

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * 마크 판독의 **끝단**이 기준 구현과 같은 답을 내는지 본다 (NM-515 DoD).
 *
 * [MarkPreprocessParityTest] 가 전처리를 화소까지 맞추고, 여기서는 그 뒤 추론·디코딩까지
 * 이어 붙인 결과를 본다 — 유무 · 종 · 임베딩 셋 다.
 *
 * ## 임베딩이 제일 예민하다
 * 서버가 카탈로그 48,883장과 코사인으로 후보를 재정렬하는 값이라, 조금만 틀어져도 **예외가
 * 아니라 순서로만** 드러난다. DoD 가 코사인 0.99 이상을 요구하는데 실제로는 훨씬 높게 나와야
 * 정상이다 — 전처리가 바이트 단위로 같으니 남는 차이는 실행 공급자(WebGPU)의 fp16 누적뿐이다.
 *
 * ## 모델이 없으면 건너뛴다
 * ```
 * adb push mark_species_fp16.onnx /sdcard/Android/data/app.nursemate.core.vision.test/files/
 * ```
 * 53MB 를 저장소에 둘 수 없어 계측 테스트는 밀어 넣은 파일을 쓴다. 릴리스는 `app/models.md5`
 * 가 정한 경로로 받는다(`docs/RELEASE.md`).
 */
@RunWith(AndroidJUnit4::class)
class MarkReaderParityTest {

    private lateinit var fixtures: JSONObject
    private lateinit var answers: JSONObject
    private lateinit var model: File

    @Before
    fun setUp() {
        check(OpenCVLoader.initLocal()) { "OpenCV 네이티브를 초기화하지 못했습니다" }
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        fixtures = JSONObject(assets.open("mark/fixtures.json").use { it.readBytes().decodeToString() })
        answers = JSONObject(assets.open("mark/answers.json").use { it.readBytes().decodeToString() })
        model = File(
            InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null),
            MarkReader.MODEL_FILE_NAME
        )
        assumeTrue("모델이 없어 건너뛴다", model.isFile && model.length() > 0)
    }

    @Test
    fun `유무와_종이_기준과_같다`() {
        MarkReader(model).use { reader ->
            forEachCase { name, case ->
                val expected = answers.getJSONObject(name)
                val actual = readCase(reader, name, case)
                assertEquals(
                    "$name 종",
                    expected.getInt("species"),
                    actual.species
                )
                assertTrue(
                    "$name 유무 기준 ${expected.getDouble("presence")} · 실제 ${actual.presence}",
                    abs(expected.getDouble("presence").toFloat() - actual.presence) < PRESENCE_TOLERANCE
                )
            }
        }
    }

    @Test
    fun `임베딩_코사인이_0_99_이상이다`() {
        MarkReader(model).use { reader ->
            var worst = 1.0
            forEachCase { name, case ->
                val expected = loadEmbedding(name)
                val actual = readCase(reader, name, case).embedding
                assertEquals("$name 임베딩 길이", expected.size, actual.size)
                for (i in 0 until MarkPreprocess.ROTATIONS) {
                    val from = i * MarkReader.EMBEDDING_DIM
                    val cos = cosine(expected, actual, from, MarkReader.EMBEDDING_DIM)
                    worst = minOf(worst, cos)
                    assertTrue("$name 회전 $i 코사인 $cos", cos >= MIN_COSINE)
                }
            }
            // 통과 기준은 0.99 지만 전처리가 바이트 단위로 같으니 실제로는 훨씬 높아야 한다.
            assertTrue("최악 코사인 $worst — 0.999 도 못 넘으면 어딘가 틀어진 것이다", worst >= 0.999)
        }
    }

    /**
     * 모델이 **L2 정규화까지 마친 값**을 준다는 것을 확인한다.
     *
     * ⚠️ **이 테스트로는 「앱이 다시 정규화했는지」를 못 잡는다.** 길이가 1.001 인 벡터를 다시
     * 정규화하면 원소가 1e-4 쯤 움직이는데, WebGPU fp16 잡음이 그보다 큰 2e-3 이라 묻힌다.
     * 코사인도 크기에 무관해서 못 잡는다. 「다시 걸지 않는다」는 [MarkReader] 에 정규화 호출이
     * 아예 없다는 사실로 지키고, 여기서는 **모델이 주는 값이 단위 벡터가 맞는지**만 본다.
     */
    @Test
    fun `임베딩이_이미_L2_정규화되어_있다`() {
        MarkReader(model).use { reader ->
            forEachCase { name, case ->
                val embedding = readCase(reader, name, case).embedding
                for (i in 0 until MarkPreprocess.ROTATIONS) {
                    var sum = 0.0
                    val from = i * MarkReader.EMBEDDING_DIM
                    for (j in 0 until MarkReader.EMBEDDING_DIM) {
                        val v = embedding[from + j].toDouble()
                        sum += v * v
                    }
                    val norm = sqrt(sum)
                    assertTrue("$name 회전 $i 길이 $norm", abs(norm - 1.0) < NORM_TOLERANCE)
                }
            }
        }
    }

    /**
     * 임계 두 갈래가 실제 값에서 갈리는지 본다.
     *
     * `none_set-19_p3_back` 은 유무가 0.72 라 **0.60 은 넘고 0.80 은 못 넘는다** — 같은 면에
     * 각인이 있었느냐로 답이 갈리는 자리다.
     */
    @Test
    fun `각인_유무가_마크_판정을_가른다`() {
        MarkReader(model).use { reader ->
            val name = "none_set-19_p3_back"
            val case = fixtures.getJSONObject("cases").getJSONObject(name)
            val score = readCase(reader, name, case).presence
            assertTrue("이 표본은 0.6~0.8 사이여야 의미가 있다 — 실제 $score", score in 0.60f..0.80f)
            assertEquals("각인을 못 읽었으면 마크로 본다", true, MarkPresence.hasMark(score, imprint = null))
            assertEquals("각인을 읽었으면 판단을 보류한다", null, MarkPresence.hasMark(score, imprint = "AZ"))
        }
    }

    // ── 거들기 ───────────────────────────────────────────────────────

    private fun forEachCase(block: (String, JSONObject) -> Unit) {
        val cases = fixtures.getJSONObject("cases")
        for (name in cases.keys()) block(name, cases.getJSONObject(name))
    }

    private fun readCase(reader: MarkReader, name: String, case: JSONObject): MarkReader.Result {
        val gray = MarkPreprocess.toGrayOnWhite(loadCrop(name, case))
        return reader.readGray(gray).also { gray.release() }
    }

    /** `*.rgba` 원시 바이트 → **곱하지 않은** `ARGB_8888` 비트맵. [MarkPreprocessParityTest] 와 같은 이유다. */
    private fun loadCrop(name: String, case: JSONObject): Bitmap {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("mark/$name.rgba").use { it.readBytes() }
        val bitmap = Bitmap.createBitmap(case.getInt("width"), case.getInt("height"), Bitmap.Config.ARGB_8888)
        bitmap.setPremultiplied(false)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return bitmap
    }

    private fun loadEmbedding(name: String): FloatArray {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("mark/$name.embed.f32").use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun cosine(a: FloatArray, b: FloatArray, from: Int, length: Int): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until length) {
            val x = a[from + i].toDouble()
            val y = b[from + i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        return dot / (sqrt(na) * sqrt(nb))
    }

    private companion object {
        /**
         * DoD 가 정한 값. 실측은 **0.99995** 라 한참 위다 — 전처리가 바이트 단위로 같아
         * 남는 차이가 WebGPU fp16 누적뿐이기 때문이다. 0.999 를 못 넘으면 어딘가 틀어진 것이다.
         */
        const val MIN_COSINE = 0.99

        /**
         * 실측 최대 **1.29e-3** (WebGPU fp16 대 파이썬 CPU fp16). 4배 여유를 뒀다.
         *
         * 이 정도는 판정을 못 바꾼다 — 임계가 0.60 · 0.80 이라, 점수가 임계에서 5e-3 안쪽에
         * 있을 때만 갈리고 그런 값은 애초에 어느 쪽으로 읽어도 무방한 자리다.
         */
        const val PRESENCE_TOLERANCE = 5e-3f

        /** 실측 최대 **1.08e-3**. 모델은 fp32 로 정규화하지만 WebGPU fp16 누적이 길이를 흔든다. */
        const val NORM_TOLERANCE = 5e-3
    }
}
