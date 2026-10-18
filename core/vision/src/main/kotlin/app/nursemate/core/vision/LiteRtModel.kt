package app.nursemate.core.vision

import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
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
 * ## GPU 가 안 되면 CPU 로
 * 계획이 GPU 라도 [VisionBackend.allowGpu] 가 막거나 GPU 로 열다 실패하면 **같은 파일을 CPU 로** 연다.
 * 느려도 도는 쪽이 판독을 통째로 포기하는 것보다 낫다. CPU 로 돌아도 판정은 fp32 와 같다(ML 실측).
 *
 * ⚠️ **한 인스턴스를 두 스레드가 동시에 [run] 하면 안 된다.** 버퍼를 공유한다. 리더가 잠그거나
 * (세그 · 마크), 조각마다 전용 스레드를 둔다(각인).
 */
internal class LiteRtModel private constructor(
    val name: String,
    private val compiled: CompiledModel,
    /** 실제로 GPU 로 열렸는가. 계획이 GPU 여도 대체됐으면 false 다. */
    val onGpu: Boolean
) : Closeable {

    private val inputs: List<TensorBuffer> = compiled.createInputBuffers()
    private val outputs: List<TensorBuffer> = compiled.createOutputBuffers()

    /** 입력을 index 순서대로 받아 돌리고, 출력을 index 순서대로 돌려준다. */
    fun run(vararg xs: FloatArray): List<FloatArray> {
        require(xs.size == inputs.size) { "$name 입력은 ${inputs.size}개인데 ${xs.size}개를 받았다" }
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
        /**
         * [file] 을 [plan] 대로 연다. GPU 가 막혔거나 실패하면 CPU 로 연다.
         *
         * 열기는 0.8~3.3초 걸린다(ML 실측, 셰이더 준비 포함) — 플로우 동안 한 번만 열고 재사용한다.
         */
        fun open(file: File, plan: RunPlan, threads: Int = CPU_THREADS): LiteRtModel {
            val name = file.name
            if (plan.placement != Placement.CPU) {
                if (VisionBackend.allowGpu(name)) {
                    runCatching { CompiledModel.create(file.absolutePath, gpuOptions(plan, threads)) }
                        .onSuccess {
                            // 열렸다는 것만 알린다. **추론은 아직 안 돌았다** — 네이티브로 죽는 것은 그다음이라,
                            // 꽂은 쪽이 「이 프로세스는 GPU 를 썼다」를 미리 남겨 둬야 죽고 나서 안다.
                            VisionBackend.onGpuModelOpened(name)
                            Log.i(TAG, "$name GPU(${plan.placement} · ${plan.precision})")
                            return LiteRtModel(name, it, onGpu = true)
                        }
                        .onFailure { Log.w(TAG, "$name GPU 로 못 열었다 — CPU 로 연다: ${it.message}") }
                } else {
                    Log.i(TAG, "$name GPU 막힘 — CPU 로 연다")
                }
            }
            val cpu = CompiledModel.create(file.absolutePath, cpuOptions(threads))
            Log.i(TAG, "$name CPU($threads 스레드)")
            return LiteRtModel(name, cpu, onGpu = false)
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
