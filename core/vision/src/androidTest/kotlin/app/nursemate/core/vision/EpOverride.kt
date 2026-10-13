package app.nursemate.core.vision

import androidx.test.platform.app.InstrumentationRegistry

/**
 * 계측 인자로 실행 공급자를 고른다 — 같은 기기에서 **CPU 와 WebGPU 의 답을 맞대 보려고** 둔다.
 *
 * ```
 * ./gradlew :core:vision:connectedDebugAndroidTest           # 기본 — 기기가 되면 WebGPU
 * ./gradlew :core:vision:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.nm_webgpu=false   # CPU 강제
 * ```
 *
 * Firebase Test Lab 은 `--environment-variables nm_webgpu=false` 가 같은 자리로 들어간다.
 *
 * ## 왜 필요했나
 * WebGPU 를 쓰기로 한 근거(NM-396 · NM-528)가 전부 **Galaxy S24 한 대**에서 나왔다. 그 기기의
 * GPU 는 Xclipse 940(AMD RDNA 계열)이라 Adreno · Mali 와 드라이버가 통째로 다르다. 2026-10-13
 * 에 Adreno 740(SM-S916N)에서 `vulkan.adreno.so` 세그폴트가 올라오면서, 안 죽는 기기에서도
 * **답이 같은지**를 확인한 적이 없다는 것이 드러났다.
 *
 * 한 기기에서 두 번 돌려 답이 갈리면 그 GPU 의 WebGPU 는 믿을 수 없다. 세그폴트와 달리 이쪽은
 * 조용해서, 보려고 하지 않으면 보이지 않는다.
 */
internal object EpOverride {

    /** 계측 인자 이름. Test Lab 의 `--environment-variables` 키와 같아야 한다. */
    private const val ARG = "nm_webgpu"

    /**
     * 인자를 읽어 [OrtBackend] 에 반영한다. 테스트의 `@Before` 에서 **세션을 만들기 전에** 부른다.
     *
     * 인자가 없으면 운영과 같은 기본값(켜짐)이다.
     *
     * @return 로그·실패 메시지에 붙일 이름
     */
    fun apply(): String {
        val requested = InstrumentationRegistry.getArguments().getString(ARG)?.toBooleanStrictOrNull() ?: true
        OrtBackend.webGpuEnabled = requested
        return if (requested) "WebGPU(가능하면)" else "CPU 강제"
    }
}
