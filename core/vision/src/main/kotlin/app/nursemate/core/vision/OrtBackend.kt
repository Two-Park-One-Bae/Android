package app.nursemate.core.vision

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.File

/**
 * 세션을 **WebGPU 로 열고, 안 되면 CPU 로 되돌아간다**.
 *
 * ## 어느 EP 를 쓰는가 — **기기가 정한다**
 * 2026-10-13 실기기 전수 측정(NM-577). 사진 3장(알약 3·7·4정) · 예열 후 · 답 대조 포함.
 *
 * | | 검출 | 각인 | 마크(1장) |
 * |---|---|---|---|
 * | **Xclipse 940** (Exynos 2400) | 1575 → **810 ms** | 4376 → **2157 ms** | 650 → **206 ms** |
 * | Adreno 740 (SM8550) | 1700 → 1110 ms | **프로세스 사망** | 704 → 337 ms |
 * | Adreno 750 (SM8650) | **프로세스 사망** | 예외 | 560 → 262 ms |
 * | Mali (Tensor G4) | 예외 | 예외 | 예외 |
 *
 * WebGPU 가 되는 곳에서는 **답이 CPU 와 같다**(검출 박스·점수 최대차 1e-7). 다만 **같은
 * Adreno 안에서도 세대마다 깨지는 모델이 다르다** — 무엇이 깨질지 미리 알 수 없으므로
 * **되는 것만 켠다**(`WebGpuPolicy` 허용목록). 모르면 CPU 다.
 *
 * ## 다른 EP 는 재 보고 접었다
 * 옛 주석은 「Galaxy S24 에서 전부 재 봤다」였는데 그 한 대의 결과였다. 네 기기로 다시 쟀다.
 *
 * | 경로 | 결과 |
 * |---|---|
 * | NNAPI | **검출이 전 기기에서 `ANEURALNETWORKS_OP_FAILED`**. 각인·마크는 돌지만 CPU 보다 느리다. Android 15 에서 deprecated |
 * | XNNPACK | 전부 CPU 보다 1.5~2.5배 느리고, **마크는 네 기기 전부 오답**(종 7 → 65) |
 * | Exynos NNC | 컴파일 전체 실패 (Xclipse) |
 * | int8 | 세그는 트랜스포머 활성 이상치에 무너진다 |
 * | QNN | 프리빌트 `onnxruntime-android-qnn` 에는 **Dawn 이 없다** — 쓰려면 WebGPU 를 버려야 한다 |
 *
 * 즉 실질 선택지는 **CPU 와 WebGPU 둘뿐**이다.
 *
 * ## 프리빌트에 들어 있다 — 소스 빌드가 아니다
 * 2026-09 에는 「프리빌트 Maven 배포본에 EP 가 없어 소스 빌드가 필요하다」가 맞았고, 그래서
 * 11MB 커스텀 AAR 과 minSdk 28 을 받아들여야 했다. **공식 `onnxruntime-android` 1.30.0 부터는
 * 들어 있다**(ABI 4개 전부 · minSdk 24). 저장소가 1.22.0 에 머물러 있었을 뿐이다.
 *
 * ## 되돌아갈 길이 필요한 이유
 * WebGPU 는 Dawn 을 통해 Vulkan 으로 내려간다. 드라이버가 못 받는 기기가 있어서, 세션 생성이
 * 실패하면 잡아서 CPU 로 다시 연다. 답은 같다(감사셋 대조 완료, NM-528).
 *
 * ⚠️ **아래 `runCatching` 은 세션 생성 실패만 잡는다.** 예전 주석은 「드라이버가 못 받으면
 * 세션 생성 실패로 나온다」를 전제했는데 **그 전제가 틀렸다**(NM-574, 2026-10-13 Test Lab 실측).
 * 실제로 터지는 자리는 [OrtSession.run][ai.onnxruntime.OrtSession.run] 안이고, 양상이 둘이다:
 *
 * | 기기 | 무엇이 일어나나 | 잡히나 |
 * |---|---|---|
 * | SM-S916N · Adreno 740 | `vulkan.adreno.so` `vkCmdPipelineBarrier` 에서 **SIGSEGV** | **못 잡는다** — OS 가 프로세스를 끝낸다 |
 * | SM-S928N · Adreno 750 | Conv 노드 `OrtException`, 이어서 WebGPU 디바이스 사망 | 잡히지만 그 프로세스의 WebGPU 는 죽은 뒤다 |
 *
 * 그래서 **들어가기 전에 막는 길**을 [VisionBackend] 로 따로 냈다. 이 폴백은 그대로 두되,
 * 이것만으로 충분하다고 읽히면 안 된다.
 */
internal object OrtBackend {

    /**
     * WebGPU 를 쓸지 — **계측 테스트만 끈다**(`-e nm_webgpu false`).
     *
     * 같은 기기에서 CPU 와 WebGPU 의 답을 맞대 보려면 한쪽을 강제로 끌 수 있어야 한다.
     * 운영 코드에는 이 값을 바꾸는 자리가 없고, 기본값은 늘 켜짐이다.
     *
     * 왜 필요했나: 2026-10-13 Adreno(SM-S916N)에서 `vulkan.adreno.so` 세그폴트가 올라왔는데,
     * WebGPU 성능·정확도 근거가 전부 Xclipse(S24 · Exynos 2400) 한 대에서 나온 것이었다.
     * 다른 GPU 에서 **답이 같은지**를 재려면 같은 기기에서 두 경로를 다 돌려야 한다.
     */
    internal var webGpuEnabled: Boolean = true

    /**
     * 실행 공급자를 하나로 **못박는다** — 계측 테스트 전용(`-e nm_ep nnapi`).
     *
     * ## 왜 필요한가
     * 지금의 EP 비교표([OrtBackend] KDoc)가 **Galaxy S24(Exynos 2400 · Xclipse 940) 한 대**
     * 에서 나왔다. 「NNAPI 실패」는 엑시노스 드라이버 얘기지 퀄컴 NNAPI(Hexagon NPU 로
     * 내려간다)에 대한 것이 아니고, 「XNNPACK 더 느림」도 그 CPU 에서의 값이다.
     * 2026-10-13 Adreno 에서 각인이 깨지는 것이 드러나면서 **그 표를 다시 깔아야** 한다.
     *
     * `null` 이면 운영과 같다 — WebGPU 가 되면 WebGPU, 안 되면 CPU.
     */
    internal var forcedEp: Ep? = null

    /** 이 기기가 WebGPU EP 를 갖고 있는가. 런타임 한 번만 묻는다. */
    private val hasWebGpu: Boolean by lazy {
        OrtEnvironment.getAvailableProviders().contains(OrtProvider.WEBGPU).also {
            Log.i(TAG, "사용 가능 EP: ${OrtEnvironment.getAvailableProviders()} · WebGPU=$it")
        }
    }

    /**
     * @param cpuNodeNames WebGPU 로 열 때 **CPU 에 남겨 둘 노드**의 이름 조각.
     *                     각인처럼 일부 연산이 GPU 에서 오히려 느린 모델이 쓴다 — [ImprintReader] KDoc 참조.
     *                     CPU 로 되돌아갈 때는 의미가 없으므로 무시된다.
     * @param webGpuOptions WebGPU EP 제공자 옵션(`preferredLayout` 등). 기본은 비어 있다 —
     *                       **무엇이 Adreno 를 살리는지 재는 데 쓴다**(NM-577).
     * @param configure CPU·WebGPU 양쪽에 공통으로 줄 설정
     */
    fun openSession(
        env: OrtEnvironment,
        modelFile: File,
        cpuNodeNames: String? = null,
        webGpuOptions: Map<String, String> = emptyMap(),
        configure: OrtSession.SessionOptions.() -> Unit = {}
    ): OrtSession {
        forcedEp?.let { ep ->
            Log.i(TAG, "${modelFile.name} EP 고정 → $ep")
            return env.createSession(
                modelFile.absolutePath,
                OrtSession.SessionOptions().apply {
                    configure()
                    applyEp(ep, cpuNodeNames, webGpuOptions)
                }
            )
        }

        // 바깥이 막는 모델은 아예 시도하지 않는다 — 이 기기에서 터지는 것이 이미 알려진 조합이거나,
        // 전에 네이티브로 죽은 적이 있는 기기다(NM-574).
        val allowed = VisionBackend.allowWebGpu(modelFile.name)
        if (!allowed) Log.i(TAG, "${modelFile.name} WebGPU 차단됨 — CPU 로 연다")

        val onWebGpu = if (allowed && webGpuEnabled && hasWebGpu) {
            runCatching {
                env.createSession(
                    modelFile.absolutePath,
                    OrtSession.SessionOptions().apply {
                        configure()
                        addWebGPU(webGpuOptions)
                        cpuNodeNames?.let { addConfigEntry(LAYER_ASSIGNMENT, "cpu($it)") }
                    }
                )
            }.onSuccess {
                // 열렸다는 것만 알린다. **추론은 아직 안 돌았다** — 터지는 것은 그다음이라,
                // 기록하는 쪽이 「이 프로세스는 WebGPU 를 썼다」를 미리 남겨 둬야 죽고 나서 안다.
                VisionBackend.onWebGpuSessionOpened(modelFile.name)
            }.onFailure {
                // 기기에 따라 드라이버가 못 받을 수 있다. 판독을 통째로 포기하는 것보다 느려도 도는 쪽이 낫다.
                Log.w(TAG, "${modelFile.name} WebGPU 세션 실패 — CPU 로 연다: ${it.message}")
            }.getOrNull()
        } else {
            null
        }

        return onWebGpu ?: env.createSession(
            modelFile.absolutePath,
            OrtSession.SessionOptions().apply(configure)
        )
    }

    /** [forcedEp] 가 정한 EP 하나만 건다 — 계측 테스트 경로다. 폴백이 없어야 그 EP 를 잰 값이 된다. */
    private fun OrtSession.SessionOptions.applyEp(ep: Ep, cpuNodeNames: String?, webGpuOptions: Map<String, String>) =
        when (ep) {
            Ep.CPU -> Unit

            Ep.WEBGPU -> {
                addWebGPU(webGpuOptions)
                cpuNodeNames?.let { addConfigEntry(LAYER_ASSIGNMENT, "cpu($it)") }
            }

            // 기본 플래그로 연다. 세부 플래그는 되는 것이 확인된 다음에 만진다.
            Ep.NNAPI -> addNnapi()

            Ep.XNNPACK -> addXnnpack(emptyMap())
        }

    /**
     * 노드 이름 조각으로 배정을 강제하는 **공식 설정 키**.
     *
     * WebGPU EP 전용 옵션 `ep.webgpuexecutionprovider.forceCpuNodeNames` 도 있는데 **안 먹는다**
     * (각인에서 11715 ms — 고정이 아예 안 됐다). 그쪽은 정확한 노드 이름 목록을 받는 것으로 보이고,
     * 이 키가 부분 문자열로 맞춘다.
     */
    private const val LAYER_ASSIGNMENT = "session.name_based_layer_assignment"

    private const val TAG = "NM534"
}

/** 계측 테스트가 못박을 수 있는 실행 공급자. 프리빌트 `onnxruntime-android` 에 들어 있는 넷이다. */
internal enum class Ep { CPU, WEBGPU, NNAPI, XNNPACK }
