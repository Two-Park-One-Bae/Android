package app.nursemate.pill

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.vision.DetectionResult
import app.nursemate.core.vision.ImageLoader
import app.nursemate.core.vision.PillDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 알약 식별 플로우 전체가 공유하는 상태.
 *
 * 촬영·미리보기·결과 화면이 **같은 비트맵 하나**를 본다. 화면마다 URI에서 다시 디코딩하면
 * 미리보기로 확인한 사진과 검출에 들어간 사진이 달라질 수 있는데, iOS가 정확히 그 버그를 냈다
 * (커밋 `c4f4b90` — BBOX는 맞는데 크롭만 엉뚱한 영역). 한 번 읽어서 돌려 쓴다.
 *
 * 그래서 이 ViewModel은 화면이 아니라 **`pill` 네비게이션 그래프**에 스코프된다.
 * 알약 탭을 벗어나면 함께 정리된다.
 */
@HiltViewModel
class PillRecognitionViewModel @Inject constructor(@param:ApplicationContext private val context: Context) :
    ViewModel() {

    private val _state = MutableStateFlow(PillUiState())
    val state = _state.asStateFlow()

    /** ONNX 세션은 로드에 수백 ms가 걸린다. 한 번 만들고 플로우 내내 재사용한다. */
    private var detector: PillDetector? = null

    /**
     * 촬영·선택한 사진을 읽는다.
     *
     * 최장변 2048 축소, 중앙 정사각 크롭, EXIF 회전이 여기서 끝난다([ImageLoader.load]).
     * 4000×3000 원본 기준 500 ms 안팎이 걸리므로 미리보기에 로딩 표시가 필요하다.
     */
    fun selectPhoto(uri: Uri) {
        _state.update { PillUiState(isLoadingPhoto = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ImageLoader.load(context, uri) }
            }.onSuccess { bitmap ->
                _state.update { it.copy(photo = bitmap, isLoadingPhoto = false) }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        photo = null,
                        isLoadingPhoto = false,
                        errorMessage = throwable.message ?: "사진을 불러오지 못했어요"
                    )
                }
            }
        }
    }

    /** 재촬영 — 고른 사진과 검출 결과를 버린다. */
    fun discardPhoto() {
        // 비트맵을 recycle()하지 않는다. 화면 전환 애니메이션이 아직 그리고 있을 수 있어
        // 그 순간 recycle하면 "Canvas: trying to use a recycled bitmap"으로 죽는다. GC에 맡긴다.
        _state.update { PillUiState() }
    }

    /**
     * 온디바이스 검출을 시작한다. 로딩 화면 진입 시 한 번 부른다.
     *
     * 이미 돌고 있거나 끝난 상태면 아무것도 하지 않는다 — 화면 회전이나 재구성으로 다시 불려도
     * 추론이 겹치지 않게 한다.
     */
    fun startDetection() {
        if (_state.value.detection != DetectionPhase.Idle) return
        val bitmap = _state.value.photo ?: return

        _state.update { it.copy(detection = DetectionPhase.Running) }
        viewModelScope.launch {
            runCatching { loadDetector().detect(bitmap) }
                .onSuccess { result ->
                    Log.i(
                        TAG,
                        "검출 ${result.pills.size}개 | 전처리 ${result.timings.preprocessMs}ms " +
                            "추론 ${result.timings.inferenceMs}ms 후처리 ${result.timings.postprocessMs}ms"
                    )
                    _state.update {
                        it.copy(
                            detection = if (result.pills.isEmpty()) {
                                DetectionPhase.Empty
                            } else {
                                DetectionPhase.Success(result)
                            }
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.e(TAG, "검출 실패", throwable)
                    _state.update {
                        it.copy(
                            detection = DetectionPhase.Failed(
                                throwable.message ?: "알약을 분석하지 못했어요"
                            )
                        )
                    }
                }
        }
    }

    /** 분석 실패 후 '다시 시도' — 같은 사진으로 검출을 처음부터 돌린다. */
    fun retryDetection() {
        _state.update { it.copy(detection = DetectionPhase.Idle) }
        startDetection()
    }

    /**
     * 모델을 읽어 세션을 만든다.
     *
     * ⚠️ 모델(119 MB)은 APK에 넣지 않는다. 개발 중에는 앱 전용 외부 저장소에 adb push 해서 쓴다:
     * ```
     * adb push rfdetr_seg_small.onnx /sdcard/Android/data/app.nursemate.debug/files/
     * ```
     * 릴리스 배포 방식(Play Asset Delivery vs 다운로드)은 아직 정하지 않았다.
     */
    private suspend fun loadDetector(): PillDetector = detector ?: withContext(Dispatchers.IO) {
        val file = File(context.getExternalFilesDir(null), PillDetector.MODEL_FILE_NAME)
        require(file.isFile) { "모델 파일이 없습니다:\n${file.absolutePath}" }
        PillDetector(file).also { detector = it }
    }

    override fun onCleared() {
        detector?.close()
        detector = null
    }

    private companion object {
        const val TAG = "NM394"
    }
}

data class PillUiState(
    /** 최장변 2048 이하, 중앙 정사각, EXIF 회전이 적용된 원본. 검출과 크롭이 모두 이걸 쓴다. */
    val photo: Bitmap? = null,
    val isLoadingPhoto: Boolean = false,
    val errorMessage: String? = null,
    val detection: DetectionPhase = DetectionPhase.Idle
)

/**
 * 검출 단계.
 *
 * `Empty`는 실패가 아니다 — 사진에 알약이 없었던 것이고 디자인상 `⑥ 결과 없음`으로 간다.
 * `Failed`는 모델 로드·추론이 깨진 경우로 `⑦ 분석 실패`다.
 */
sealed interface DetectionPhase {
    data object Idle : DetectionPhase
    data object Running : DetectionPhase
    data class Success(val result: DetectionResult) : DetectionPhase
    data object Empty : DetectionPhase
    data class Failed(val message: String) : DetectionPhase
}
