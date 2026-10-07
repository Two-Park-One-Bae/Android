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
 * WebGPU 는 Dawn 을 통해 Vulkan 으로 내려간다. 드라이버가 못 받는 기기가 있을 수 있고, 그때
 * 예외가 아니라 **조용히 이상한 결과**가 아니라 세션 생성 실패로 나온다 — 그러니 잡아서 CPU 로
 * 다시 연다. 답은 같다(감사셋 대조 완료, NM-528).
 */
internal object OrtBackend {

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
        if (hasWebGpu) {
            runCatching {
                return env.createSession(
                    modelFile.absolutePath,
                    OrtSession.SessionOptions().apply {
                        configure()
                        addWebGPU(emptyMap())
                        cpuNodeNames?.let { addConfigEntry(LAYER_ASSIGNMENT, "cpu($it)") }
                    }
                )
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
