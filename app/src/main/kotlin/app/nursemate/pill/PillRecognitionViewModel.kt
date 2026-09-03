package app.nursemate.pill

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.data.pill.DailyLimitReached
import app.nursemate.core.data.pill.PillRepository
import app.nursemate.core.data.pill.UsageHolder
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.vision.DetectionResult
import app.nursemate.core.vision.ImageLoader
import app.nursemate.core.vision.PillDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
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
class PillRecognitionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelFile: PillModelFile,
    private val attributeCache: PillAttributeCache,
    private val pillRepository: PillRepository,
    private val usageHolder: UsageHolder
) : ViewModel() {

    private val _state = MutableStateFlow(PillUiState())
    val state = _state.asStateFlow()

    /** 앱 전체가 공유하는 값. 미리보기의 "오늘 남은 횟수"가 이걸 본다. */
    val usage = usageHolder.usage

    /** ONNX 세션은 로드에 수백 ms가 걸린다. 한 번 만들고 플로우 내내 재사용한다. */
    private var detector: PillDetector? = null

    /** 학습데이터 축적용 **원본** 파일. 화면이 쓰는 비트맵은 축소·크롭된 것이라 원본이 아니다. */
    private var sourceUri: Uri? = null

    /**
     * 촬영·선택한 사진을 읽는다.
     *
     * 최장변 2048 축소, 중앙 정사각 크롭, EXIF 회전이 여기서 끝난다([ImageLoader.load]).
     * 4000×3000 원본 기준 500 ms 안팎이 걸리므로 미리보기에 로딩 표시가 필요하다.
     */
    fun selectPhoto(uri: Uri) {
        sourceUri = uri
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
        sourceUri = null
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
                    if (result.pills.isEmpty()) {
                        _state.update { it.copy(detection = DetectionPhase.Empty) }
                        return@onSuccess
                    }
                    _state.update { it.copy(detection = DetectionPhase.Success(result)) }
                    extractAttributes(result)
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

    /**
     * 분석 실패 후 '다시 시도'.
     *
     * **검출이 이미 끝났으면 다시 돌리지 않는다.** 온디바이스 추론은 같은 사진에 같은 결과를
     * 주고 2초가 걸린다 — 서버 호출만 실패한 경우에 그 값을 다시 치를 이유가 없다.
     */
    fun retryDetection() {
        val done = _state.value.detection as? DetectionPhase.Success
        if (done != null) {
            _state.update { it.copy(attributes = AttributePhase.Idle) }
            viewModelScope.launch { extractAttributes(done.result) }
            return
        }
        _state.update { it.copy(detection = DetectionPhase.Idle, attributes = AttributePhase.Idle) }
        startDetection()
    }

    /**
     * 크롭을 서버로 보내 색·모양·제형을 받는다.
     *
     * ## 1회 식별 = 이 요청 1회다
     * 사진에 알약이 몇 개든 상관없다. 검출 0개면 여기까지 오지 않는다 —
     * 오면 아무 소득 없이 한 번 차감된다(spec §카운트 규칙).
     *
     * ## 원본 업로드는 기다리지 않는다
     * 학습데이터 축적(NM-348)은 식별과 분리된 베스트 에포트다. 결과를 기다리면
     * 사용자가 S3 왕복만큼 더 기다리게 된다.
     */
    private suspend fun extractAttributes(result: DetectionResult) {
        _state.update { it.copy(attributes = AttributePhase.Running) }

        // 원본 업로드는 **기다리지 않는다.** 식별과 분리된 베스트 에포트라 결과도 보지 않는다.
        //
        // JPEG 일 때만 보낸다 — presigned 서명에 image/jpeg 가 박혀 있어 갤러리에서 고른
        // HEIC·PNG 를 그 타입으로 올리면 깨진 파일이 쌓인다. 촬영 결과는 항상 JPEG 다.
        // **화면이 쓰는 비트맵이 아니라 원본 URI 에서 읽는다** — 그 비트맵은 축소·크롭됐다.
        sourceUri?.let { uri ->
            viewModelScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        if (context.contentResolver.getType(uri) != JPEG_MIME) return@withContext null
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                }.getOrNull()?.let { bytes -> pillRepository.uploadOriginal(bytes) }
            }
        }

        val pillIds = result.pills.indices.map(::pillId)

        // 디버그 빌드에서 직전 결과가 남아 있으면 그걸 쓴다 — 화면을 고칠 때마다 Gemini 를
        // 부르면 5~8초씩 기다리고 하루 15회 한도가 오후에 바닥난다. 릴리스에서는 늘 null 이다.
        attributeCache.load(pillIds)?.let { cached ->
            _state.update { it.copy(attributes = AttributePhase.Done(cached.associateBy(PillAttribute::pillId))) }
            return
        }

        val crops = result.pills.mapIndexed { index, pill ->
            pillId(index) to pill.crop.toPngBytes()
        }.toMap()

        pillRepository.attributes(crops)
            .onSuccess { extracted ->
                usageHolder.update(extracted.usage)
                attributeCache.save(extracted.items)
                _state.update {
                    it.copy(attributes = AttributePhase.Done(extracted.items.associateBy(PillAttribute::pillId)))
                }
            }
            .onFailure { throwable ->
                when (throwable) {
                    is DailyLimitReached -> {
                        throwable.usage?.let(usageHolder::update)
                        Log.i(TAG, "일일 식별 한도 도달")
                        _state.update { it.copy(attributes = AttributePhase.LimitReached) }
                    }

                    else -> {
                        Log.w(TAG, "속성 추출 실패", throwable)
                        _state.update {
                            it.copy(
                                attributes = AttributePhase.Failed(
                                    throwable.message ?: "알약 정보를 가져오지 못했어요"
                                )
                            )
                        }
                    }
                }
            }
    }

    /**
     * 사용자 보정 — 수정·선택·삭제·수동 추가.
     *
     * 촬영·검출과 다른 일이라 갈라 뒀다. 앞쪽은 사진 한 장을 결과로 바꾸는 파이프라인이고,
     * 이쪽은 그 결과를 사람이 고치는 것이다 — 서로 부르지 않는다.
     */
    val corrections = PillCorrections(_state)

    private suspend fun loadDetector(): PillDetector = detector ?: withContext(Dispatchers.IO) {
        PillDetector(modelFile.prepare()).also { detector = it }
    }

    override fun onCleared() {
        detector?.close()
        detector = null
    }

    private companion object {
        const val TAG = "NM394"
        const val JPEG_MIME = "image/jpeg"
    }
}

data class PillUiState(
    /** 최장변 2048 이하, 중앙 정사각, EXIF 회전이 적용된 원본. 검출과 크롭이 모두 이걸 쓴다. */
    val photo: Bitmap? = null,
    val isLoadingPhoto: Boolean = false,
    val errorMessage: String? = null,
    val detection: DetectionPhase = DetectionPhase.Idle,
    val attributes: AttributePhase = AttributePhase.Idle,
    /**
     * 사용자가 목록에서 뺀 알약의 pillId.
     *
     * 검출 결과 자체를 고치지 않고 가려서 보여준다 — 원본을 지우면 되돌릴 수 없고,
     * 서버에 이미 보낸 속성과 짝이 어긋난다. 번호는 남은 것들로 다시 매긴다.
     */
    val removedPillIds: Set<String> = emptySet(),
    /**
     * 사용자가 고친 속성·각인. 서버가 뽑은 값 위에 덮어쓴다.
     *
     * 서버 응답을 직접 수정하지 않고 따로 둔다 — 어디까지가 자동값이고 어디부터 사람이
     * 고친 것인지 구분되어야, 나중에 추출 품질을 되짚을 수 있다.
     * 손대기 전에는 항목이 없고, 그때는 추출값에서 만들어 쓴다([editOf]).
     */
    val edits: Map<String, PillEdit> = emptyMap(),
    /**
     * 사용자가 직접 넣은 알약. 검출 결과 **뒤에** 붙는다.
     *
     * 사진에 대응 영역이 없어 오버레이도 크롭 썸네일도 없다(spec NM-187).
     */
    val manualPillIds: List<String> = emptyList(),
    /** 확정한 후보. 카드 제목이 '알약을 선택해주세요'에서 품목명으로 바뀐다. */
    val selections: Map<String, PillCandidate> = emptyMap()
)

/**
 * 서버 속성 추출 단계.
 *
 * 검출(온디바이스)과 나눠 둔다 — 실패 원인이 다르고(모델 vs 네트워크·한도),
 * '다시 시도'가 되돌아갈 지점도 다르다.
 */
sealed interface AttributePhase {
    data object Idle : AttributePhase
    data object Running : AttributePhase

    /** @param byPillId 요청에 실은 pillId 로 찾는다. 실패한 알약은 `error` 가 채워져 온다. */
    data class Done(val byPillId: Map<String, PillAttribute>) : AttributePhase

    /** 일일 한도 도달. **차감되지 않았다** — 안내만 하고 되돌린다. */
    data object LimitReached : AttributePhase

    data class Failed(val message: String) : AttributePhase
}

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

/**
 * 크롭을 PNG 바이트로.
 *
 * ⚠️ **JPEG 로 바꾸지 말 것.** 크롭은 마스크를 알파에 담고 있어서, 알파를 버리면
 * 배경이 검게 살아나고 서버가 그 배경까지 알약으로 읽는다.
 */
private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().use { out ->
    compress(Bitmap.CompressFormat.PNG, 100, out)
    out.toByteArray()
}

/**
 * 검출 순서(0부터)로 정하는 세션 로컬 키.
 *
 * 서버 요청에 실어 보낸 값이라 **삭제로 번호가 바뀌어도 이 키는 그대로다** —
 * 화면의 표시 번호와 헷갈리지 않게 둘을 나눠 둔다.
 */
internal fun pillId(index: Int): String = (index + 1).toString()

/**
 * 수동 추가 알약의 키.
 *
 * 검출 키가 "1"·"2"… 라 숫자만으로는 겹친다. 접두사로 갈라 두면 키만 보고도 사진에
 * 대응 영역이 있는 알약인지 알 수 있다.
 */
internal fun manualPillId(index: Int): String = "m${index + 1}"

internal val String.isManualPill: Boolean get() = startsWith("m")

/**
 * 수정 화면이 들고 고칠 값.
 *
 * 아직 안 건드린 알약은 서버 추출값에서 만들어 준다 — 추출까지 실패했으면 빈 값에서 시작한다
 * (spec §개별 추출 실패 — 그때도 사용자가 직접 채워 후보를 찾을 수 있어야 한다).
 */
fun PillUiState.editOf(pillId: String): PillEdit {
    edits[pillId]?.let { return it }
    val attribute = extracted(pillId) ?: PillAttribute(pillId = pillId)
    return PillEdit(attribute = attribute, faces = FaceInputs.from(attribute))
}

private fun PillUiState.extracted(pillId: String): PillAttribute? =
    (attributes as? AttributePhase.Done)?.byPillId?.get(pillId)

/**
 * 인식 결과에 대한 사용자 보정.
 *
 * 상태를 [PillRecognitionViewModel] 과 나눠 갖는 게 아니라 **같은 흐름을 함께 쓴다** —
 * 화면 하나가 두 상태를 합쳐 보는 일이 없도록.
 */
class PillCorrections internal constructor(private val state: MutableStateFlow<PillUiState>) {

    /** 사용자가 고친 속성·각인을 갈무리한다. 서버가 준 원본은 건드리지 않는다. */
    fun updateEdit(pillId: String, edit: PillEdit) {
        state.update { it.copy(edits = it.edits + (pillId to edit)) }
    }

    /** 후보를 확정한다. 취소하면 부르지 않으므로 여기 오면 사용자가 확인을 누른 것이다. */
    fun selectCandidate(pillId: String, candidate: PillCandidate) {
        state.update { it.copy(selections = it.selections + (pillId to candidate)) }
    }

    /**
     * 목록에서 알약 하나를 뺀다 (spec NM-134).
     *
     * 오탐이거나 인식 대상이 아닌 알약을 지우는 용도다. 사진 위 영역 표시도 함께 사라지고
     * 번호가 다시 매겨진다 — 화면이 알아서 하도록 여기서는 id 만 기록한다.
     */
    fun removePill(pillId: String) {
        // 수동 추가 알약은 사진에 대응 bbox 가 없어 목록에서만 빼면 된다(spec NM-134).
        state.update {
            if (pillId.isManualPill) {
                it.copy(manualPillIds = it.manualPillIds - pillId)
            } else {
                it.copy(removedPillIds = it.removedPillIds + pillId)
            }
        }
    }

    /**
     * 수동 추가 알약을 목록에 넣는다.
     *
     * ⚠️ **확인을 누른 뒤에만 부른다.** 진입할 때 넣으면 취소하고 나온 자리에 빈 카드가
     * 남는다(spec NM-187 — "선택·확인 시에만 새 카드로 추가, 취소 시 미추가").
     */
    fun addManualPill(pillId: String) {
        state.update {
            if (pillId in it.manualPillIds) it else it.copy(manualPillIds = it.manualPillIds + pillId)
        }
    }

    /** 다음 수동 추가 알약이 쓸 키. 지웠다 다시 추가해도 겹치지 않게 항상 뒤로만 간다. */
    fun nextManualPillId(): String = manualPillId(manualSequence++)

    /** 수동 추가 키 카운터. 목록에서 지운 키를 다시 쓰면 옛 수정값이 딸려 온다. */
    private var manualSequence = 0
}
