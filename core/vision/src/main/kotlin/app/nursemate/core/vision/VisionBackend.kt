package app.nursemate.core.vision

import java.io.File

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
     * GPU 를 **쓰기 시작한다** — 열기(`create` · 셰이더 준비) · 실행 · 닫기 직전에 불린다([whileOnGpu]). 꽂은 쪽이 디스크에
     * 흔적을 남겨, 이 프로세스가 그 사이에 네이티브로 죽으면 다음 실행에서 알아채게 한다.
     *
     * 흔적을 **모델이 열려 있는 동안이 아니라 GPU 를 쓰는 동안만** 둔다. 모델은 플로우 내내(촬영 화면부터 · 탭을 옮겨도) 열려
     * 있어, 그 동안 GPU 와 무관한 네이티브 크래시가 나면 GPU 탓으로 잘못 읽고 그 빌드 내내 전 모델이 CPU 로 돌기 때문이다
     * (2026-10-19 리뷰 — 미리 열기로 그 구간이 넓어졌다).
     *
     * ⚠️ **여기서 오래 걸리면 안 된다.** 판독 한 번에 두 번 불린다.
     */
    @Volatile
    var onGpuUse: (modelFileName: String) -> Unit = {}

    /** [onGpuUse] 로 알린 GPU 작업이 끝났다. 도는 GPU 작업이 없으면 흔적을 지울 수 있다. */
    @Volatile
    var onGpuReleased: (modelFileName: String) -> Unit = {}

    /** [gpu] 일 때 [block] 동안만 GPU 흔적을 둔다. 던져도 끝을 알린다. */
    internal inline fun <T> whileOnGpu(name: String, gpu: Boolean, block: () -> T): T {
        if (!gpu) return block()
        onGpuUse(name)
        try {
            return block()
        } finally {
            onGpuReleased(name)
        }
    }

    /**
     * GPU 로 열린 모델이 **실행에서** 실패해 CPU 로 대체했다. 꽂은 쪽이 이 기기 · 이 빌드에서 그 모델을 CPU 에 고정해
     * 다음에 열 때부터 GPU 를 시도하지 않게 한다.
     */
    @Volatile
    var onGpuRunFailed: (modelFileName: String) -> Unit = {}

    /**
     * 모델이 **어느 가속기로** 열렸는지 — 열 때마다 불린다. 운영에서 기기별로 GPU 가 실제로 붙는지 보려는
     * 분석 이벤트가 쓴다(NM-628). [onGpuUse] 와 달리 크래시 판정과 무관하다. GPU 실행 실패로 CPU 로 바뀌었을 때도 한 번 더
     * 불린다([OpenOutcome.GPU_RUN_FAILED]).
     */
    @Volatile
    var onModelOpened: (modelFileName: String, outcome: OpenOutcome) -> Unit = { _, _ -> }

    /**
     * GPU 로 연 모델의 번역 결과(이 기기 GPU 용 실행 코드)를 디스크에 남길 자리 — NM-636. null 이면 남기지 않는다
     * (계측 테스트 · `core:vision` 단독).
     *
     * 모델을 GPU 로 열 때마다 LiteRT 가 셰이더를 번역한다(S24 세그 약 3초). 번역 결과를 남겨 두면 다음 실행부터 건너뛴다 —
     * S24 세그 2.8초 → 0.6초. 앱 프로세스가 죽으면 메모리의 번역은 사라지므로 디스크에 둔다(Google LiteRT GPU 문서의
     * serialization 권장).
     */
    @Volatile
    var gpuCache: GpuCache? = null
}

/**
 * @param dir 앱 전용 폴더(`codeCacheDir` 아래 — 앱을 올리면 시스템이 비운다)
 * @param salt 키에 덧붙일 값 — 앱 버전 · 기기 빌드. 바뀌면 다른 키라 새로 번역한다(드라이버가 바뀌었을 수 있다)
 */
data class GpuCache(val dir: File, val salt: String)

/** 모델이 실제로 어디서 열렸고, CPU 라면 왜인가. 이름(`label`)은 분석 이벤트 값이다 — 바꾸지 않는다. */
enum class OpenOutcome(val label: String, val onGpu: Boolean) {
    /** 계획대로 GPU. */
    GPU("ok", onGpu = true),

    /** 실행 정밀도 계약이 CPU 다(각인 p2 · p5). */
    PLANNED_CPU("planned_cpu", onGpu = false),

    /** GPU 로 열다 실패해 CPU 로 대체했다 — 드라이버가 모델을 거절했다. */
    GPU_OPEN_FAILED("gpu_open_failed", onGpu = false),

    /** [VisionBackend.allowGpu] 가 막았다 — 전에 네이티브로 죽은 기기 · GPU 실행이 실패해 CPU 에 고정된 모델. */
    GPU_BLOCKED("gpu_blocked", onGpu = false),

    /** GPU 로 열렸는데 실행에서 실패해 CPU 로 다시 열었다. */
    GPU_RUN_FAILED("gpu_run_failed", onGpu = false)
}
