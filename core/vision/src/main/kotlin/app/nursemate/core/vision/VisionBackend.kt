package app.nursemate.core.vision

/**
 * **어느 모델을 GPU 로 열지**를 바깥에서 정하게 하는 구멍 — NM-574 에서 시작해 NM-628(LiteRT)로 넘어왔다.
 *
 * ## 왜 `core:vision` 이 직접 정하지 않나
 * 판단에 필요한 것이 이 모듈 밖에 있다. 기기가 전에 죽은 적이 있는지, 자가 점검에서 틀린 적이
 * 있는지, 어떤 SoC · 드라이버인지 — 전부 `app` 쪽 사정이다. 비전 모듈은 **묻기만** 하고 답은
 * [allowGpu] 를 꽂은 쪽이 한다.
 *
 * 꽂지 않으면 늘 허용이다. 계측 테스트와 `core:vision` 단독 사용이 그대로 돈다.
 *
 * ## 왜 들어가기 전에 막나
 * GPU 드라이버가 모델을 받다 **프로세스째 죽는** 기기가 있다(ORT WebGPU 시절 Adreno 740 SIGSEGV —
 * 2026-10-13 Test Lab). 세그폴트는 예외가 아니라 OS 가 프로세스를 끝내는 것이라 어떤 `catch` 로도
 * 못 막는다. 그래서 전에 죽은 기기는 다음부터 열기 전에 막는다.
 */
object VisionBackend {

    /**
     * 이 모델을 GPU 로 열어도 되는가. 인자는 모델 **파일 이름**이다(각인은 조각 파일 이름 — `p3_trunk.tflite` 등).
     *
     * 모델을 **열 때** 묻는다. 한 번 연 모델이 플로우 내내 재사용되므로 추론마다 묻지 않는다.
     */
    @Volatile
    var allowGpu: (modelFileName: String) -> Boolean = { true }

    /**
     * GPU 로 **실제로 열렸을 때** 불린다. 꽂은 쪽이 디스크에 흔적을 남겨, 이 프로세스가 네이티브로
     * 죽으면 다음 실행에서 알아채게 한다.
     *
     * ⚠️ **여기서 오래 걸리면 안 된다.** 모델 여는 경로 한가운데다.
     */
    @Volatile
    var onGpuModelOpened: (modelFileName: String) -> Unit = {}
}
