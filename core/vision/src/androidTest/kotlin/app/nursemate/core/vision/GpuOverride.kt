package app.nursemate.core.vision

import androidx.test.platform.app.InstrumentationRegistry

/**
 * 계측 인자로 GPU 를 끈다 — 같은 기기에서 **CPU 와 GPU 의 답을 맞대 보려고** 둔다.
 *
 * ```
 * ./gradlew :core:vision:connectedDebugAndroidTest           # 기본 — 운영과 같이 GPU(안 되면 CPU)
 * ./gradlew :core:vision:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.nm_gpu=false   # CPU 강제
 * ```
 *
 * Firebase Test Lab 은 `--environment-variables nm_gpu=false` 가 같은 자리로 들어간다.
 *
 * ## 왜 필요한가
 * GPU 는 기기마다 드라이버가 통째로 다르다. 세그폴트와 달리 **조용히 다른 답**은 보려고 하지 않으면
 * 보이지 않는다 — LiteRT 에서도 8 Elite FP16 이 그랬다(ML 실측, 실행마다 다르게 틀림).
 */
internal object GpuOverride {

    /** 계측 인자 이름. Test Lab 의 `--environment-variables` 키와 같아야 한다. */
    private const val ARG = "nm_gpu"

    /**
     * 인자를 읽어 [VisionBackend] 에 반영한다. 테스트의 `@Before` 에서 **모델을 열기 전에** 부른다.
     *
     * @return 로그 · 실패 메시지에 붙일 이름
     */
    fun apply(): String {
        val gpu = InstrumentationRegistry.getArguments().getString(ARG)?.toBooleanStrictOrNull() ?: true
        VisionBackend.allowGpu = { gpu }
        return if (gpu) "GPU(가능하면)" else "CPU 강제"
    }
}
