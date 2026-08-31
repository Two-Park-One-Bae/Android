package app.nursemate.spike.rfdetr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

/**
 * 실행 백엔드.
 *
 * ⚠️ **가속기가 그래프를 못 받으면 조용히 CPU로 폴백한다.** LiteRT GPU 델리게이트를 실측했을 때
 * 정확히 그랬고(2210/2385 노드가 XNNPACK CPU로 감), 전송 비용만 붙어 오히려 느려졌다
 * (5899 ms → 7049 ms). 그래서 EP를 켰다고 빨라졌다고 단정하면 안 되고, 반드시 수치로 확인해야 한다.
 *
 * 이 모델은 DINOv2 ViT 백본 + deformable attention이라 CNN용 가속 경로가 잘 안 붙는다.
 */
enum class ExecutionProvider(val label: String) {
    CPU("CPU"),
    XNNPACK("XNNPACK"),
    NNAPI("NNAPI"),

    /**
     * WebGPU (Android에서는 Vulkan). **999개 중 997개가 GPU에 배정**된다 —
     * LiteRT GPU 델리게이트가 `BATCH_MATMUL`을 못 받아 2210/2385를 CPU로 떨어뜨린 것과 대조적이다.
     */
    WEBGPU("WebGPU"),

    /**
     * WebGPU + 튜닝. 1.75배(848 ms)에 그친 원인을 로그로 확인하고 대응한 조합이다.
     *
     * 확인된 병목:
     *  - `Transpose` 131개가 각각 별도 디스패치로 실행됨 (메모리 재배치라 연산 이득 없음)
     *  - 워크그룹이 `(2592,1,1)` 형태의 1차원 → GPU 유닛 점유율 낮음
     *  - `memory pattern` 최적화가 WebGPU에서 자동 비활성화됨
     *  - CPU에 남은 `Unsqueeze`/`Tile` 2개 때문에 그래프 중간에 GPU↔CPU 왕복 발생
     *
     * 대응: graph capture(디스패치 오버헤드 제거) · 검증 비활성 · 버퍼 캐시 · 고성능 GPU 선택.
     */
    WEBGPU_TUNED("WebGPU+튜닝"),
}

/** 한 번의 식별에서 걸린 시간(ms). 어디가 병목인지 보려고 단계별로 나눈다. */
data class Timings(
    val decodeImageMs: Long,
    val preprocessMs: Long,
    val inferenceMs: Long,
    val postprocessMs: Long,
) {
    val totalMs: Long get() = decodeImageMs + preprocessMs + inferenceMs + postprocessMs
}

data class DetectionResult(
    val detections: List<Detection>,
    val timings: Timings,
    val imageWidth: Int,
    val imageHeight: Int,
)

/**
 * RF-DETR-Seg 온디바이스 검출기 (ONNX Runtime).
 *
 * 모델은 현규님이 준 `rfdetr_seg_small.onnx` — iOS가 쓰는 CoreML(.mlpackage)을 만든
 * **바로 그 원본 그래프**다. 입출력 이름이 CoreML과 동일하고(`image` / `pred_logits` /
 * `pred_boxes` / `pred_masks`), CoreML 결과와 IoU 1.0000으로 일치한다.
 *
 * 세션 로드가 수백 ms 걸리므로 한 번만 만들고 재사용한다.
 */
class PillDetector(
    modelFile: File,
    provider: ExecutionProvider = ExecutionProvider.CPU,
    threads: Int = DEFAULT_THREADS,
) : Closeable {

    /** EP 적용 중 실패하면 여기 사유가 남는다. 조용히 CPU로 떨어지면 측정이 오염되므로 표면화한다. */
    var providerNote: String = ""
        private set

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // ⚠️ VERBOSE 로그는 디스패치마다 로그를 찍어 성능을 크게 왜곡한다.
            // 노드 배정 확인이 끝났으므로 WARNING으로 되돌린다.
            // (배정을 다시 보려면 ORT_LOGGING_LEVEL_VERBOSE 로 바꾸되, 그때 측정한 시간은 믿지 말 것.)
            setSessionLogLevel(ai.onnxruntime.OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING)
            when (provider) {
                ExecutionProvider.CPU -> providerNote = "CPU (기본)"
                ExecutionProvider.XNNPACK -> runCatching {
                    addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
                }.fold(
                    { providerNote = "XNNPACK EP 적용됨" },
                    { providerNote = "XNNPACK 실패 → CPU: ${it.message}" },
                )
                ExecutionProvider.NNAPI -> runCatching { addNnapi() }.fold(
                    { providerNote = "NNAPI EP 적용됨" },
                    { providerNote = "NNAPI 실패 → CPU: ${it.message}" },
                )
                ExecutionProvider.WEBGPU -> runCatching { addWebGPU(emptyMap()) }.fold(
                    { providerNote = "WebGPU EP 적용됨" },
                    { providerNote = "WebGPU 실패 → CPU: ${it.message}" },
                )
                ExecutionProvider.WEBGPU_TUNED -> runCatching {
                    addWebGPU(
                        mapOf(
                            // 매 추론마다 커맨드를 다시 기록하지 않고 캡처된 그래프를 재생한다.
                            // Transpose 131개처럼 작은 디스패치가 많을수록 이득이 크다.
                            "ep.webgpuexecutionprovider.enableGraphCapture" to "1",
                            // WGSL 검증은 개발용이다. 매 실행 비용을 없앤다.
                            "ep.webgpuexecutionprovider.validationMode" to "disabled",
                            // memory pattern이 꺼지는 대신 버퍼를 버킷 캐시로 재사용한다.
                            "ep.webgpuexecutionprovider.storageBufferCacheMode" to "bucket",
                            "ep.webgpuexecutionprovider.defaultBufferCacheMode" to "bucket",
                            // 통합 GPU/저전력 어댑터 대신 고성능 어댑터를 요청한다.
                            "ep.webgpuexecutionprovider.powerPreference" to "high-performance",
                        ),
                    )
                }.fold(
                    { providerNote = "WebGPU+튜닝 적용됨" },
                    { providerNote = "WebGPU+튜닝 실패 → CPU: ${it.message}" },
                )
            }
        },
    )
    private val inputName: String = session.inputNames.first()

    /** 전처리 출력 버퍼. 매 추론마다 재할당하지 않는다(4 MB). */
    private val inputBuffer = FloatArray(3 * RfDetrSpec.INPUT_SIZE * RfDetrSpec.INPUT_SIZE)

    /**
     * @param bitmap EXIF 회전이 이미 적용되고 최장변이 [RfDetrSpec.MAX_DIMENSION] 이하인 비트맵
     * @param decodeImageMs 호출자가 잰 이미지 디코딩 시간(측정 표시용)
     */
    fun detect(bitmap: Bitmap, decodeImageMs: Long): DetectionResult {
        val w = bitmap.width
        val h = bitmap.height

        val t0 = System.nanoTime()
        val rgb = bitmap.toRgb888()
        RfDetrPreprocess.toInputTensor(rgb, w, h, inputBuffer)
        val t1 = System.nanoTime()

        val shape = longArrayOf(
            1, 3, RfDetrSpec.INPUT_SIZE.toLong(), RfDetrSpec.INPUT_SIZE.toLong(),
        )
        var logits: FloatArray? = null
        var boxes: FloatArray? = null
        var t2: Long
        OnnxTensor.createTensor(env, FloatBuffer.wrap(inputBuffer), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                t2 = System.nanoTime()
                // ⚠️ pred_masks(100×144×144 = 207만 float)는 여기서 꺼내지 않는다.
                //    BBOX만 필요한데 마스크까지 JVM으로 복사하면 그 마샬링이 추론 시간을 넘어선다.
                //    (실제로 벤치마크에서 이걸 포함시켰다가 1.2초를 추론 시간으로 오인했다.)
                for (i in 0 until result.size()) {
                    val v = result[i].value
                    val flat = flattenSmall(v) ?: continue
                    when (flat.size) {
                        RfDetrSpec.QUERY_COUNT * 2 -> logits = flat
                        RfDetrSpec.QUERY_COUNT * 4 -> boxes = flat
                    }
                }
            }
        }
        val t3 = System.nanoTime()

        val detections = RfDetrPostprocess.decode(
            requireNotNull(logits) { "pred_logits 출력을 찾지 못했다" },
            requireNotNull(boxes) { "pred_boxes 출력을 찾지 못했다" },
        )
        val t4 = System.nanoTime()

        return DetectionResult(
            detections = detections,
            timings = Timings(
                decodeImageMs = decodeImageMs,
                preprocessMs = (t1 - t0) / 1_000_000,
                inferenceMs = (t2 - t1) / 1_000_000,
                postprocessMs = (t4 - t3 + (t3 - t2)) / 1_000_000,
            ),
            imageWidth = w,
            imageHeight = h,
        )
    }

    /** 작은 출력만 평탄화한다. 큰 텐서(마스크)는 건드리지 않고 null을 돌려준다. */
    private fun flattenSmall(value: Any?): FloatArray? {
        val rows = ((value as? Array<*>)?.firstOrNull() as? Array<*>) ?: return null
        if (rows.size != RfDetrSpec.QUERY_COUNT) return null
        val first = rows.firstOrNull() as? FloatArray ?: return null
        if (first.size > 4) return null   // 마스크(144)는 제외
        val out = FloatArray(rows.size * first.size)
        rows.forEachIndexed { i, row ->
            (row as FloatArray).copyInto(out, i * first.size)
        }
        return out
    }

    override fun close() {
        session.close()
    }

    companion object {
        /**
         * 4스레드가 최적이다. 실측에서 1스레드 6968 ms · 2스레드 3615 ms · 4스레드 2083 ms 였고,
         * 6·8스레드는 오히려 느려졌다(big.LITTLE에서 리틀코어로 넘치는 비용).
         */
        const val DEFAULT_THREADS = 4

        const val MODEL_FP32 = "rfdetr_seg_small.onnx"

        /**
         * dynamic INT8 (가중치만 8bit, activation은 float).
         * 125 MB → 34.8 MB. 캘리브레이션 불필요.
         * static INT8은 RF-DETR이 명시적으로 거부한다 — transformer activation이 8bit PTQ를 못 견딘다.
         *
         * ⚠️ macOS 데스크톱에서는 **오히려 느렸다**(233 → 361 ms). MLAS fp32 커널이 이미 빠르고
         *    역양자화 비용이 붙기 때문이다. 모바일은 메모리 대역폭이 더 빡빡해 결과가 다를 수 있어
         *    기기에서 직접 재본다. 정확도는 box IoU 0.90 / mask IoU 0.94 로 감수할 만하다.
         */
        const val MODEL_INT8 = "rfdetr_seg_small_int8.onnx"

        /**
         * fp16 (반정밀도). 125 → 62.7 MB. 입출력은 fp32로 두고 내부 연산만 fp16이다.
         *
         * INT8과 달리 정확도 손실이 훨씬 작다(부동소수점을 유지하므로).
         * ORT 1.29에 **ARM KleidiAI fp16 커널**이 들어가서, ARMv8.2 FEAT_FP16을 가진 CPU에서는
         * 네이티브 fp16 연산이 돌 여지가 있다. TFLite fp16이 이득 없었던 것과는 다른 경로다
         * (TFLite는 fp16을 fp32로 되돌려 계산했다).
         */
        const val MODEL_FP16 = "rfdetr_seg_small_fp16.onnx"
    }
}

/** ARGB_8888 비트맵 → RGB888 연속 바이트. 전처리가 요구하는 형식. */
private fun Bitmap.toRgb888(): ByteArray {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val out = ByteArray(width * height * 3)
    var j = 0
    for (p in pixels) {
        out[j++] = (p shr 16).toByte()   // R
        out[j++] = (p shr 8).toByte()    // G
        out[j++] = p.toByte()            // B
    }
    return out
}
