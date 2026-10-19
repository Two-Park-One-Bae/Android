package app.nursemate.pill.camera

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 초점 상태 — 매 프레임 `CONTROL_AF_STATE` 를 넷으로 줄였다. 링 색이 이 값을 따른다.
 *
 * - [Searching]: 렌즈가 움직이는 중(연속 AF 재탐색 · 탭 뒤 탐색)
 * - [Focused]: 맞음(연속 AF 가 맞췄거나 탭 초점이 잠김)
 * - [Failed]: 탭 초점이 끝났는데 못 맞춤
 */
enum class Af { Unknown, Searching, Focused, Failed }

data class PillCameraState(
    val bound: Boolean = false,
    val hasFlash: Boolean = false,
    /** 기기에 CameraX 야간 확장이 있는가. 없으면 달 아이콘을 두지 않는다. */
    val nightAvailable: Boolean = false,
    val night: Boolean = false,
    val zoom: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val exposureIndex: Int = 0,
    val exposureRange: IntRange = 0..0,
    val exposureSupported: Boolean = false,
    val af: Af = Af.Unknown,
    /** 길게 눌러(또는 자물쇠를 눌러) AE/AF 를 고정했는가. */
    val locked: Boolean = false,
    /** 어둡다 — ISO 1600 이상이거나 노출 1/20초 이상. 기본 카메라가 야간 권유 점을 띄우는 때다. */
    val lowLight: Boolean = false
)

/**
 * 촬영 화면의 카메라 — `ProcessCameraProvider` 에 Preview + ImageCapture 를 묶고 조작을 얹었다.
 * NM-630 이 PoC(`~/dev/test/camera-poc`)에서 옮겼다.
 *
 * | 기능 | 쓰는 API |
 * |---|---|
 * | 탭 초점 · 상태 | `FocusMeteringAction` + 매 프레임 `CONTROL_AF_STATE`(Camera2 interop) |
 * | AE/AF 고정 | 자동 해제 없는 `FocusMeteringAction` |
 * | 배율 | `setZoomRatio` — 1 아래면 논리 카메라가 초광각으로 넘긴다 |
 * | 노출 | `setExposureCompensationIndex` |
 * | 야간 | CameraX Extensions `NIGHT` — 기기에 있을 때만(S24 는 삼성 야간, 한 장 수 초) |
 *
 * ## 화면을 자주 다시 그리지 않는다 — 발열
 * 매 프레임(초당 30회) 결과를 그대로 상태에 올리면 그만큼 화면이 다시 그려진다. PoC 에서 이게 발열의
 * 한 원인이었다. 그래서 [state] 는 **값이 바뀔 때만** 올린다(초점 거리처럼 매 프레임 흔들리는 값은 아예 안 싣는다).
 *
 * ⚠️ 저장 사진은 자르지 않는다(`ViewPort` 를 쓰지 않는다). 중앙 정사각 크롭은 지금처럼
 * [app.nursemate.core.vision.ImageLoader] 가 맡는다 — 미리보기 영역과 같다는 근거는 `PillCameraScreen` KDoc.
 */
class PillCamera(private val context: Context) {

    private val _state = MutableStateFlow(PillCameraState())
    val state: StateFlow<PillCameraState> = _state.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var capture: ImageCapture? = null
    private var observedZoom: LiveData<ZoomState>? = null
    private var observedCamera: LiveData<CameraState>? = null

    /** 탭할 때마다 하나씩 는다. 늦게 끝난(취소된) 옛 탭의 결과가 새 탭 상태를 덮지 않게 가른다. */
    private var focusGeneration = 0

    /**
     * 탭한 뒤 **능동 AF 결과가 올 때까지** 연속 AF 값(PASSIVE_*)을 무시한다.
     *
     * 탭 요청이 실제 프레임에 실리기까지 몇 프레임이 걸린다. 그 사이 프레임은 탭 전의 「연속 AF 맞음」을
     * 그대로 실어 와, 링이 누르자마자 노랗게 됐다(PoC) — 맞추지도 않았는데 맞았다고 말하는 셈이다.
     */
    @Volatile
    private var awaitingActive = false

    /**
     * 손이 원하는 배율. 카메라가 돌려주는 `zoomState` 는 몇 프레임 늦게 오므로, 핀치를 그 값에 곱하면
     * 손가락보다 늦게 따라오며 덜컹거린다. 핀치 · 버튼 애니메이션은 이 값을 기준으로 움직인다.
     */
    var zoomTarget: Float = 1f
        private set

    /** 화면이 기억해 둘 배율 — 묶인 동안엔 [zoomTarget], 묶이기 전 · 풀린 뒤엔 [kept] 를 그대로 둔다. */
    fun zoomToKeep(kept: Float): Float = if (state.value.bound) zoomTarget else kept

    /**
     * 카메라를 묶는다. [night] 를 바꿀 때도 다시 부른다(확장 모드는 세션을 새로 열어야 한다).
     * 기기에 야간 확장이 없으면 [night] 는 무시하고 기본으로 묶는다.
     */
    suspend fun bind(
        owner: LifecycleOwner,
        view: PreviewView,
        night: Boolean = _state.value.night,
        zoom: Float = zoomTarget
    ) = withContext(Dispatchers.Main.immediate) {
        // CameraX 묶기 · `PreviewView.surfaceProvider` 는 메인 스레드에서만 된다. 부르는 쪽이 메인이어도 앞의 `await` 가
        // 끝난 뒤 다른 스레드에서 이어지는 환경이 있다(Compose UI 테스트의 인터셉터 — Test Lab A12 · S9 에서 카메라가 안
        // 붙었다, 2026-10-19). 여기서 메인에 못 박는다. 이미 메인이면 `immediate` 라 디스패치하지 않는다.
        val cameraProvider = provider ?: ProcessCameraProvider.awaitInstance(context).also { provider = it }
        val ext = extensions ?: runCatching {
            ExtensionsManager.getInstanceAsync(context, cameraProvider).await(context)
        }.getOrNull()?.also { extensions = it }
        val base = CameraSelector.DEFAULT_BACK_CAMERA
        val nightAvailable = ext?.isExtensionAvailable(base, ExtensionMode.NIGHT) == true
        val useNight = night && nightAvailable
        val selector = ext?.takeIf { useNight }?.getExtensionEnabledCameraSelector(base, ExtensionMode.NIGHT) ?: base

        val preview = previewBuilder().build().also { it.surfaceProvider = view.surfaceProvider }
        val shot = ImageCapture.Builder()
            // 알약 각인을 봐야 하므로 속도보다 화질을 택한다.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        cameraProvider.unbindAll()
        // 옛 카메라는 이미 풀렸다 — 새로 묶다 실패하면 셔터가 풀린 ImageCapture 를 쥔 채 열려 있지 않게 먼저 비운다.
        camera = null
        capture = null
        observedZoom?.removeObservers(owner)
        observedZoom = null
        observedCamera?.removeObservers(owner)
        observedCamera = null
        _state.update { it.copy(bound = false) }
        val cam = cameraProvider.bindToLifecycle(owner, selector, preview, shot)
        camera = cam
        capture = shot
        // 원하던 배율을 이어 간다(재촬영 · 야간 전환 · 백그라운드 복귀 — NM-632 체크리스트 6번). 범위는 새 카메라 것으로 자른다.
        val bounds = cam.cameraInfo.zoomState.value
        zoomTarget = bounds?.let { zoom.coerceIn(it.minZoomRatio, it.maxZoomRatio) } ?: 1f
        // 카메라가 **열릴 때마다** 원하던 배율을 다시 건다 — 백그라운드에서 돌아오면 CameraX 가 세션을 새로 열며 1× 로 돌린다.
        observedCamera = cam.cameraInfo.cameraState.also { state ->
            state.observe(owner) { if (it.type == CameraState.Type.OPEN) reapplyZoom() }
        }
        // 확장(야간) 카메라의 CameraInfo 는 기본 카메라와 다른 객체라, 지울 때는 **관찰했던 그 LiveData** 에서 지운다.
        observedZoom = cam.cameraInfo.zoomState.also { zoom ->
            zoom.observe(owner) { z ->
                _state.update { it.copy(zoom = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio) }
            }
        }
        val exposure = cam.cameraInfo.exposureState
        _state.update {
            it.copy(
                bound = true,
                hasFlash = cam.cameraInfo.hasFlashUnit(),
                nightAvailable = nightAvailable,
                night = useNight,
                exposureSupported = exposure.isExposureCompensationSupported,
                exposureRange = exposure.exposureCompensationRange.lower..exposure.exposureCompensationRange.upper,
                exposureIndex = exposure.exposureCompensationIndex,
                af = Af.Unknown,
                locked = false
            )
        }
    }

    /** 화면을 떠날 때 한 번 푼다. */
    fun unbind() {
        provider?.unbindAll()
        camera = null
        capture = null
        observedZoom = null
        observedCamera = null
    }

    /**
     * 매 프레임 결과에서 초점 상태 · 밝기를 읽는다.
     * ⚠️ Extensions 세션에는 이 콜백이 붙지 않는 기기가 있다 — 그때 링은 탭 결과(성공 · 실패)만 보인다.
     */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun previewBuilder(): Preview.Builder = Preview.Builder().also {
        Camera2Interop.Extender(it).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                val raw = result.get(CaptureResult.CONTROL_AF_STATE)
                if (raw != null && raw.isActiveAf()) awaitingActive = false
                val af = if (awaitingActive) null else raw?.toAf()
                val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
                val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                val dark = iso >= LOW_LIGHT_ISO || exposureNs >= LOW_LIGHT_EXPOSURE_NS
                val now = _state.value
                if ((af == null || af == now.af) && dark == now.lowLight) return
                _state.update { s -> s.copy(af = af ?: s.af, lowLight = dark) }
            }
        })
    }

    /** 지금 배율이 원하던 배율과 다르면 다시 건다. */
    private fun reapplyZoom() {
        val now = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: return
        if (abs(now - zoomTarget) > ZOOM_EPSILON) camera?.cameraControl?.setZoomRatio(zoomTarget)
    }

    fun setZoom(ratio: Float) {
        val s = _state.value
        zoomTarget = ratio.coerceIn(s.minZoom, s.maxZoom)
        camera?.cameraControl?.setZoomRatio(zoomTarget)
    }

    fun setExposure(index: Int) {
        val s = _state.value
        val i = index.coerceIn(s.exposureRange)
        if (i == s.exposureIndex) return
        camera?.cameraControl?.setExposureCompensationIndex(i)
        _state.update { it.copy(exposureIndex = i) }
    }

    /**
     * 누른 곳에 AF · AE 를 건다. [lock] 이면 풀지 않고(AE/AF 고정), 아니면 기본 카메라처럼 몇 초 뒤
     * 연속 AF 로 돌아간다. 새로 누를 때마다 노출 보정은 0 으로 되돌린다 — 기본 카메라와 같다.
     */
    fun focus(point: MeteringPoint, lock: Boolean) {
        val cam = camera ?: return
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .apply { if (lock) disableAutoCancel() else setAutoCancelDuration(AUTO_CANCEL_S, TimeUnit.SECONDS) }
            .build()
        setExposure(0)
        val generation = ++focusGeneration
        awaitingActive = true
        _state.update { it.copy(af = Af.Searching, locked = lock) }
        val future = cam.cameraControl.startFocusAndMetering(action)
        future.addListener({
            // 빠르게 다시 탭하면 CameraX 가 이 요청을 취소하고, 이 리스너는 새 탭 **뒤에** 돈다.
            // 그때 awaitingActive 를 풀면 새 요청이 실리기 전 프레임의 「연속 AF 맞음」이 올라온다 — 새 탭 것이 아니면 버린다.
            if (generation != focusGeneration) return@addListener
            val ok = runCatching { future.get().isFocusSuccessful }.getOrNull()
            // 결과 콜백이 없는 세션(Extensions)에서도 링이 결과를 보이도록 여기서도 적는다.
            awaitingActive = false
            if (ok != null) _state.update { it.copy(af = if (ok) Af.Focused else Af.Failed) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unlock() {
        camera?.cameraControl?.cancelFocusAndMetering()
        setExposure(0)
        _state.update { it.copy(locked = false) }
    }

    /**
     * 찍어 앱 캐시에 저장하고 그 위치를 돌려준다. 실패하면 null.
     *
     * 캐시에 두는 이유: 약포 사진은 환자 정보(**PII**)라 갤러리에 남기지 않는다.
     * 검출이 끝나면 지워도 되는 임시 파일이다.
     *
     * 플래시는 **누를 때 건다.** 토글 시점에 걸어 두면 CameraX 가 복귀하며 세션을 다시 열 때 값이 초기화돼
     * 화면 표시와 실제 동작이 어긋난다.
     */
    fun shoot(flashOn: Boolean, onResult: (Uri?) -> Unit) {
        val shot = capture ?: return onResult(null)
        shot.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        // 이전 캡처가 남아 있으면 지운다 — ViewModel 이 캐시 파일 URI 를 따로 추적하지 않아
        // 검출 완료 시점 정리는 더 큰 변경이 필요하다. 재촬영마다 쌓이는 걸 막는 최소 대응으로
        // 한 번에 최대 1개로 묶는다 — 약포 사진은 환자 정보(PII)라 오래 남을수록 위험이 크다.
        context.cacheDir.listFiles { file -> file.name.startsWith("capture_") }?.forEach { it.delete() }

        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        shot.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onResult(output.savedUri ?: Uri.fromFile(file))
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(null)
                }
            }
        )
    }

    private companion object {
        const val AUTO_CANCEL_S = 5L
        const val ZOOM_EPSILON = 0.01f
        const val LOW_LIGHT_ISO = 1600
        const val LOW_LIGHT_EXPOSURE_NS = 50_000_000L
    }
}

/** 탭(능동) AF 의 상태인가 — 연속 AF(PASSIVE_*)와 가른다. */
private fun Int.isActiveAf(): Boolean = this == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN ||
    this == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
    this == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED

private fun Int.toAf(): Af = when (this) {
    CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN, CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> Af.Searching
    CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED, CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> Af.Focused
    CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED, CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> Af.Failed
    else -> Af.Unknown
}

/**
 * `ListenableFuture` 를 기다린다. `get()` 으로 기다리면 메인 스레드가 멈추므로 콜백을 코루틴으로 감싼다.
 * guava 코루틴 어댑터를 들이지 않으려고 한 함수만 둔다.
 */
private suspend fun <T> ListenableFuture<T>.await(context: Context): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            runCatching { get() }
                .onSuccess(continuation::resume)
                .onFailure(continuation::resumeWithException)
        },
        ContextCompat.getMainExecutor(context)
    )
}
