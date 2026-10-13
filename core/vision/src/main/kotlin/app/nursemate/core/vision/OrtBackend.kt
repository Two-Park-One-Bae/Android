package app.nursemate.core.vision

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.File

/**
 * 세션을 **WebGPU 로 열고, 안 되면 CPU 로 되돌아간다**.
 *
 * ## 왜 WebGPU 인가
 * Galaxy S24(Exynos 2400)에서 가속 경로를 전부 재 보고 남은 하나다(NM-396 · NM-528).
 *
 * | 경로 | 결과 |
 * |---|---|
 * | XNNPACK | 세그 1550 → 3410 ms, **더 느리다** |
 * | NNAPI | 실패. Android 15 에서 deprecated 되기도 했다 |
 * | Exynos NNC | 컴파일 전체 실패 |
 * | int8 | 세그는 트랜스포머 활성 이상치에 무너진다 |
 * | **WebGPU** | 세그 1572 → **813 ms** · 각인 3472 → **1796 ms** · 마크 764 → **292 ms** |
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
     * @param configure CPU·WebGPU 양쪽에 공통으로 줄 설정
     */
    fun openSession(
        env: OrtEnvironment,
        modelFile: File,
        cpuNodeNames: String? = null,
        configure: OrtSession.SessionOptions.() -> Unit = {}
    ): OrtSession {
        // 바깥이 막는 모델은 아예 시도하지 않는다 — 이 기기에서 터지는 것이 이미 알려진 조합이거나,
        // 전에 네이티브로 죽은 적이 있는 기기다(NM-574).
        val allowed = VisionBackend.allowWebGpu(modelFile.name)
        if (!allowed) Log.i(TAG, "${modelFile.name} WebGPU 차단됨 — CPU 로 연다")

        if (allowed && webGpuEnabled && hasWebGpu) {
            runCatching {
                env.createSession(
                    modelFile.absolutePath,
                    OrtSession.SessionOptions().apply {
                        configure()
                        addWebGPU(emptyMap())
                        cpuNodeNames?.let { addConfigEntry(LAYER_ASSIGNMENT, "cpu($it)") }
                    }
                )
            }.onSuccess { session ->
                // 열렸다는 것만 알린다. **추론은 아직 안 돌았다** — 터지는 것은 그다음이라,
                // 기록하는 쪽이 「이 프로세스는 WebGPU 를 썼다」를 미리 남겨 둬야 죽고 나서 안다.
                VisionBackend.onWebGpuSessionOpened(modelFile.name)
                return session
            }.onFailure {
                // 기기에 따라 드라이버가 못 받을 수 있다. 판독을 통째로 포기하는 것보다 느려도 도는 쪽이 낫다.
                Log.w(TAG, "${modelFile.name} WebGPU 세션 실패 — CPU 로 연다: ${it.message}")
            }
        }
        return env.createSession(
            modelFile.absolutePath,
            OrtSession.SessionOptions().apply(configure)
        )
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
