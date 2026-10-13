package app.nursemate.core.vision

/**
 * **어느 모델을 WebGPU 로 열지**를 바깥에서 정하게 하는 구멍 — NM-574.
 *
 * ## 왜 `core:vision` 이 직접 정하지 않나
 * 판단에 필요한 것이 이 모듈 밖에 있다. 기기가 전에 죽은 적이 있는지(DataStore), 어떤 SoC
 * 인지, 원격으로 내려온 차단 목록이 있는지 — 전부 `app` 쪽 사정이다. 비전 모듈은 **묻기만**
 * 하고 답은 [allowWebGpu] 를 꽂은 쪽이 한다.
 *
 * 꽂지 않으면 늘 허용이다. 계측 테스트와 `core:vision` 단독 사용이 그대로 돈다.
 *
 * ## 왜 이것이 필요한가 — 2026-10-13
 * Adreno 드라이버가 각인 모델을 받다 죽는다. Firebase Test Lab 실기기에서 재현했다.
 *
 * | 기기 | 결과 |
 * |---|---|
 * | SM-S916N · Adreno 740 | `vulkan.adreno.so` `vkCmdPipelineBarrier` 에서 **SIGSEGV** |
 * | SM-S928N · Adreno 750 | Conv 노드에서 `OrtException`, 이어서 **WebGPU 디바이스 사망** |
 *
 * 둘 다 [OrtSession.run][ai.onnxruntime.OrtSession.run] 안에서 일어난다. 세션 생성이 아니다 —
 * [OrtBackend] 의 `runCatching` 폴백이 **원리적으로 못 잡는 자리**이고, 세그폴트는 예외가
 * 아니라 OS 가 프로세스를 끝내는 것이라 어떤 `catch` 로도 못 막는다.
 *
 * 그래서 **들어가기 전에 막는 수밖에 없다.**
 */
object VisionBackend {

    /**
     * 이 모델을 WebGPU 로 열어도 되는가. 인자는 모델 **파일 이름**이다
     * (`reader_ep60_s1.onnx` 등 — 각 모델의 `MODEL_FILE_NAME`).
     *
     * 세션을 **열 때** 묻는다. 세션 하나가 플로우 내내 재사용되므로 추론마다 묻지 않는다.
     */
    @Volatile
    var allowWebGpu: (modelFileName: String) -> Boolean = { true }

    /**
     * WebGPU 세션이 **실제로 열렸을 때** 불린다. 꽂은 쪽이 디스크에 흔적을 남겨, 이 프로세스가
     * 네이티브로 죽으면 다음 실행에서 알아채게 한다.
     *
     * ⚠️ **여기서 오래 걸리면 안 된다.** 세션 생성 경로 한가운데다.
     */
    @Volatile
    var onWebGpuSessionOpened: (modelFileName: String) -> Unit = {}
}
