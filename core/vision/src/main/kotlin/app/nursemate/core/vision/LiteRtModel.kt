package app.nursemate.core.vision

import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.LiteRtException
import com.google.ai.edge.litert.TensorBuffer
import java.io.Closeable
import java.io.File

/** 모델(또는 각인 조각)을 어디서 돌리나. */
internal enum class Placement {
    CPU,
    GPU,

    /** GPU 가 못 받는 연산은 CPU 로 넘긴다. 각인 p4 는 GPU 만으로 열면 Exynos 에서 컴파일이 실패한다. */
    GPU_CPU
}

/** GPU 에서 돌 때의 정밀도. CPU 는 늘 fp32 다. */
internal enum class GpuPrecision { FP16, FP32 }

/**
 * 실행 계획 하나 — **ML 이 정한 실행 정밀도 계약**을 그대로 옮긴다(`_handoff/litert-ondevice-migration.md`).
 * 바꾸면 정확도 보장이 없다. 예: 각인 p3 · 마크 · 세그를 FP16 으로 두면 8 Elite(Adreno 830)에서
 * 실행마다 다르게 틀리고, 세그는 Exynos 2500 에서 개수 일치가 93.3% 로 떨어진다.
 */
internal data class RunPlan(val placement: Placement, val precision: GpuPrecision = GpuPrecision.FP32)

/**
 * LiteRT `CompiledModel` 하나 — 입출력 버퍼를 한 번 만들어 재사용한다.
 *
 * ## GPU 가 안 되면 CPU 로 — 열 때도, 돌릴 때도
 * 계획이 GPU 라도 [VisionBackend.allowGpu] 가 막거나 GPU 로 열다(버퍼 할당까지) 실패하면 **같은 파일을 CPU 로** 연다.
 * 열린 뒤 GPU 실행이 예외를 던지면 그 자리에서 CPU 로 다시 열어 한 번 더 돌리고, [VisionBackend.onGpuRunFailed] 로
 * 알려 이 기기에서 그 모델을 CPU 에 고정한다 — 컴파일은 받고 실행에서 거절하는 드라이버가 있어도 식별이 이어진다.
 * 느려도 도는 쪽이 판독을 통째로 포기하는 것보다 낫다. CPU 로 돌아도 판정은 fp32 와 같다(ML 실측).
 *
 * ## GPU 흔적은 GPU 를 쓰는 동안만 — 열기 **전부터**
 * 드라이버는 셰이더를 준비하는 `create` 안에서도 프로세스째 죽을 수 있다. 연 뒤에 남기면 그 죽음이 흔적 없이 지나가
 * 다음 실행이 못 막는다. 그래서 GPU 로 열기 · 닫기를 [VisionBackend.whileOnGpu] 로 감싸고, 실행은 리더가 판독 한 번을
 * 감싼다(꽂은 쪽이 도는 작업 수를 세어 0 이면 흔적을 지운다). 열어 두고 쉬는 동안에는 흔적이 없다.
 *
 * ⚠️ **한 인스턴스를 두 스레드가 동시에 [run] 하면 안 된다.** 버퍼를 공유한다. 리더가 잠그거나
 * (세그 · 마크), 조각마다 전용 스레드를 둔다(각인).
 */
internal class LiteRtModel private constructor(
    private val file: File,
    private val threads: Int,
    private var engine: Engine
) : Closeable {

    val name: String get() = file.name

    /** 지금 GPU 로 돌고 있는가. 계획이 GPU 여도 대체됐으면 false 다. */
    val onGpu: Boolean get() = engine.onGpu

    /** 입력을 index 순서대로 받아 돌리고, 출력을 index 순서대로 돌려준다. */
    fun run(vararg xs: FloatArray): List<FloatArray> = try {
        engine.run(xs)
    } catch (e: LiteRtException) {
        if (!engine.onGpu) throw e
        Log.w(TAG, "$name GPU 실행 실패 — CPU 로 다시 연다: ${e.message}")
        runCatching { VisionBackend.onGpuRunFailed(name) }
        // CPU 를 **먼저** 연다. 그게 실패하면(메모리 등) GPU 엔진을 그대로 둔 채 던진다 — 닫은 GPU 엔진이 남아
        // [close] 가 두 번 닫는 일이 없게.
        val cpu = Engine.cpu(file, threads)
        engine.close() // 리더가 판독을 감싸 흔적이 있는 구간이다
        engine = cpu
        report(name, OpenOutcome.GPU_RUN_FAILED)
        engine.run(xs)
    }

    override fun close() {
        VisionBackend.whileOnGpu(name, engine.onGpu) { engine.close() }
    }

    /** 컴파일된 모델과 그 입출력 버퍼. 만들다 실패하면 이미 연 것을 닫고 던진다 — 반쯤 열린 채 새지 않게. */
    private class Engine private constructor(private val compiled: CompiledModel, val onGpu: Boolean) : Closeable {
        private val inputs: List<TensorBuffer>
        private val outputs: List<TensorBuffer>

        init {
            var done = false
            try {
                inputs = compiled.createInputBuffers()
                outputs = compiled.createOutputBuffers()
                done = true
            } finally {
                if (!done) compiled.close()
            }
        }

        fun run(xs: Array<out FloatArray>): List<FloatArray> {
            require(xs.size == inputs.size) { "입력은 ${inputs.size}개인데 ${xs.size}개를 받았다" }
            xs.forEachIndexed { i, x -> inputs[i].writeFloat(x) }
            compiled.run(inputs, outputs)
            return outputs.map { it.readFloat() }
        }

        override fun close() {
            inputs.forEach { it.close() }
            outputs.forEach { it.close() }
            compiled.close()
        }

        companion object {
            fun cpu(file: File, threads: Int) =
                Engine(CompiledModel.create(file.absolutePath, cpuOptions(threads)), onGpu = false)

            fun gpu(file: File, plan: RunPlan, threads: Int) =
                Engine(CompiledModel.create(file.absolutePath, gpuOptions(plan, threads)), onGpu = true)
        }
    }

    companion object {
        /**
         * [file] 을 [plan] 대로 연다. GPU 가 막혔거나 실패하면 CPU 로 연다.
         *
         * 열기는 0.8~3.3초 걸린다(ML 실측, 셰이더 준비 포함) — 플로우 동안 한 번만 열고 재사용한다.
         */
        fun open(file: File, plan: RunPlan, threads: Int = CPU_THREADS): LiteRtModel {
            val name = file.name
            val outcome = when {
                plan.placement == Placement.CPU -> OpenOutcome.PLANNED_CPU

                !VisionBackend.allowGpu(name) -> OpenOutcome.GPU_BLOCKED

                else -> openOnGpu(file, plan, threads)?.let {
                    report(name, OpenOutcome.GPU)
                    return LiteRtModel(file, threads, it)
                } ?: OpenOutcome.GPU_OPEN_FAILED
            }
            if (outcome == OpenOutcome.GPU_BLOCKED) Log.i(TAG, "$name GPU 막힘 — CPU 로 연다")
            val cpu = Engine.cpu(file, threads)
            Log.i(TAG, "$name CPU($threads 스레드)")
            report(name, outcome)
            return LiteRtModel(file, threads, cpu)
        }

        /** GPU 로 열어 본다(버퍼 할당까지). 실패하면 null — 호출부가 CPU 로 연다. */
        private fun openOnGpu(file: File, plan: RunPlan, threads: Int): Engine? {
            val name = file.name
            // ⚠️ 열기 **전에** 알린다 — 셰이더 준비 중에 드라이버가 죽어도 다음 실행이 알게(클래스 KDoc).
            return VisionBackend.whileOnGpu(name, gpu = true) {
                runCatching { Engine.gpu(file, plan, threads) }
                    .onSuccess { Log.i(TAG, "$name GPU(${plan.placement} · ${plan.precision})") }
                    .onFailure { Log.w(TAG, "$name GPU 로 못 열었다 — CPU 로 연다: ${it.message}") }
                    .getOrNull()
            }
        }

        private fun report(name: String, outcome: OpenOutcome) {
            // 보고가 실패해도 모델 열기는 멈추지 않는다 — 관측일 뿐이다.
            runCatching { VisionBackend.onModelOpened(name, outcome) }
                .onFailure { Log.w(TAG, "$name 열림 보고 실패", it) }
        }

        private fun cpuOptions(threads: Int) = CompiledModel.Options(Accelerator.CPU).apply {
            cpuOptions = CompiledModel.CpuOptions(numThreads = threads)
        }

        private fun gpuOptions(plan: RunPlan, threads: Int): CompiledModel.Options {
            val accelerators = when (plan.placement) {
                Placement.GPU_CPU -> setOf(Accelerator.GPU, Accelerator.CPU)
                else -> setOf(Accelerator.GPU)
            }
            return CompiledModel.Options(accelerators).apply {
                cpuOptions = CompiledModel.CpuOptions(numThreads = threads)
                gpuOptions = CompiledModel.GpuOptions(
                    // ML 이 잰 경로가 OpenCL 이다. 다른 백엔드는 계약 밖이다.
                    backend = CompiledModel.GpuOptions.Backend.OPENCL,
                    precision = when (plan.precision) {
                        GpuPrecision.FP16 -> CompiledModel.GpuOptions.Precision.FP16
                        GpuPrecision.FP32 -> CompiledModel.GpuOptions.Precision.FP32
                    }
                )
            }
        }

        /**
         * 실측에서 4스레드가 최적이었다(ORT 시절 1 · 2 · 4 · 6 · 8 을 쟀다 — 6 · 8 은 리틀코어로 넘쳐 느려졌다).
         * ML 벤치도 CPU 4스레드다.
         */
        const val CPU_THREADS = 4

        private const val TAG = "LiteRt"
    }
}

/** NCHW → NHWC. [plane] 은 H·W. 전처리는 NCHW 로 쓰고(ONNX 시절 그대로 · 테스트가 받치는 형식) 넣기 직전에 돌린다. */
internal fun nchwToNhwc(src: FloatArray, dst: FloatArray, n: Int, c: Int, plane: Int): FloatArray {
    for (b in 0 until n) {
        val base = b * c * plane
        for (ch in 0 until c) {
            val from = base + ch * plane
            for (p in 0 until plane) dst[base + p * c + ch] = src[from + p]
        }
    }
    return dst
}
