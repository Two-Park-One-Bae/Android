package app.nursemate.pill

import app.nursemate.core.data.pill.AttributeCrop
import app.nursemate.core.data.pill.AttributeImage
import app.nursemate.core.data.pill.PillLabelSender
import app.nursemate.core.data.pill.PillRepository
import app.nursemate.core.network.api.PillCandidatesRequest
import app.nursemate.core.network.api.PillLabelRequest
import app.nursemate.core.vision.DetectionResult
import app.nursemate.core.vision.PillDetector
import java.security.SecureRandom
import java.util.Random
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 학습 라벨 — 「선택」한 품목과 「완료」 때 지운 번호를 보낸다(NM-571, spec `api/domains/label.md`).
 *
 * 크롭 · 위치는 속성 추출 요청에 실려 이미 갔다(`PillRecognitionViewModel.extractAttributes`). 여기는 사용자가 한 일만 보낸다.
 * 모두 베스트 에포트라 결과를 화면에 반영하지 않는다([PillLabelSender]).
 *
 * ## `model` 은 그 알약의 **첫** 조회 요청이다
 * 사용자가 무엇을 고쳤는지는 `model`(첫 조회)과 `userInput`(마지막 조회)을 대보면 나온다. 그래서 첫 조회를 식별이 끝날
 * 때까지 들고 있는다 — 수정 화면을 닫았다 다시 열어도 바뀌지 않는다. 고친 것이 없으면 둘이 같고, 어드민이 그걸로
 * 한 번에 확정할 행을 가른다.
 */
class PillLabels internal constructor(
    private val state: MutableStateFlow<PillUiState>,
    private val sender: PillLabelSender,
    private val scope: CoroutineScope
) {

    /** 수정 화면이 후보 조회를 **실제로 보냈다**(입력 대기 뒤 · 취소되지 않은 것). */
    fun recordQuery(pillId: String, request: PillCandidatesRequest) {
        state.update { s ->
            val first = s.labelQueries[pillId]?.first ?: request
            s.copy(labelQueries = s.labelQueries + (pillId to LabelQueries(first = first, last = request)))
        }
    }

    /** 후보를 「선택」하고 결과로 돌아왔다. 같은 알약을 다시 고르면 다시 보낸다 — 서버가 덮어쓴다. */
    fun selected(pillId: String) {
        val s = state.value
        val imageId = s.imageId
        val index = labelIndexOf(pillId)
        val request = s.labelRequestOf(pillId)
        if (imageId == null || index == null || request == null) return
        scope.launch { sender.label(imageId, index, request) }
    }

    /**
     * 「완료」(또는 종료 확인의 「종료」)를 눌렀다 — 지운 모델 알약 번호를 보낸다. 지운 게 없으면 빈 목록. 수동 추가 알약은
     * 넣지 않는다(계약). 저장하지 않고 나가면 부르지 않는다.
     */
    fun completed() {
        val s = state.value
        val imageId = s.imageId ?: return
        val deleted = s.removedPillIds.filterNot { it.isManualPill }.mapNotNull(::labelIndexOf).sorted()
        scope.launch { sender.deleted(imageId, deleted) }
    }
}

/** 고른 품목과 첫 · 마지막 조회로 라벨 본문을 만든다. 고른 것이나 조회가 없으면 null — 보낼 값이 없다. */
private fun PillUiState.labelRequestOf(pillId: String): PillLabelRequest? {
    val pillCode = selections[pillId]?.pillCode
    // 조회 없이 고른 후보는 없다 — 후보가 조회에서 나온다.
    val queries = labelQueries[pillId]
    if (pillCode == null || queries == null) return null
    val manual = pillId.isManualPill
    return PillLabelRequest(
        pillCode = pillCode,
        userInput = queries.last,
        // 수동 추가 알약은 모델 값이 없다 — 보내면 400 이다.
        model = queries.first.takeUnless { manual },
        manual = true.takeIf { manual }
    )
}

/**
 * 속성 추출에 보낼 크롭 — 찾은 알약 **전부**다. 원본 자리(`box`) · 점수를 함께 싣는다(NM-571). 검출 입력 비트맵이 곧
 * 업로드한 가공본이라 좌표를 되돌릴 일이 없다.
 */
internal fun DetectionResult.attributeCrops(): List<AttributeCrop> = pills.mapIndexed { index, pill ->
    AttributeCrop(
        pillId = pillId(index),
        png = pill.crop.toPngBytes(),
        box = pill.box.toList(),
        score = pill.detection.score
    )
}

/** 학습 라벨용 사진 정보 — [imageId] 가 없으면(라벨을 쌓지 않는 경로) null. */
internal fun DetectionResult.attributeImage(imageId: String?): AttributeImage? =
    imageId?.let { AttributeImage(it, imageWidth, imageHeight, PillDetector.MODEL_VERSION) }

/**
 * 학습용 원본(가공본)을 올린다 — [PillUiState.imageId] 가 원본 키가 된다(NM-571). 기다리지 않고 결과도 보지 않는다(베스트 에포트).
 * 사진이나 키가 없으면 아무것도 하지 않는다.
 */
internal fun CoroutineScope.uploadTrainingImage(state: PillUiState, repository: PillRepository) {
    val imageId = state.imageId ?: return
    val processed = state.photo ?: return
    launch {
        runCatching { withContext(Dispatchers.IO) { processed.toJpegBytes() } }
            .getOrNull()?.let { bytes -> repository.uploadOriginal(bytes, imageId) }
    }
}

/** 한 알약의 첫 · 마지막 후보 조회 요청 — 라벨의 `model` · `userInput`. */
data class LabelQueries(val first: PillCandidatesRequest, val last: PillCandidatesRequest)

/**
 * 라벨 번호(`pillIndex`). 모델 알약은 속성 추출의 `pillId` 그대로(0~99), 수동 추가 알약은 100~199(`m1` → 100).
 * 범위를 벗어나면 null — 보내지 않는다.
 */
internal fun labelIndexOf(pillId: String): Int? = if (pillId.isManualPill) {
    pillId.drop(1).toIntOrNull()?.let { MANUAL_LABEL_BASE - 1 + it }?.takeIf {
        it in
            MANUAL_LABEL_BASE..MANUAL_LABEL_MAX
    }
} else {
    pillId.toIntOrNull()?.takeIf { it in 0 until MANUAL_LABEL_BASE }
}

private const val MANUAL_LABEL_BASE = 100
private const val MANUAL_LABEL_MAX = 199

/**
 * UUID v7 — 앞 48비트가 만든 시각(밀리초)이다(RFC 9562). 사진마다 앱이 만들고, 서버는 이 시각의 UTC 날짜로 원본 경로를
 * 다시 만든다(spec `label.md` §단위와 키). `java.util.UUID` 는 v7 을 만들지 못해 직접 짠다.
 */
internal object UuidV7 {
    private val secure = SecureRandom()

    fun next(nowMs: Long = System.currentTimeMillis(), random: Random = secure): String {
        val bytes = ByteArray(RANDOM_BYTES).also(random::nextBytes)
        // 상위 64비트 = 시각 48 · 버전 4(0111) · 난수 12
        val msb = (nowMs shl 16) or VERSION_7 or
            ((bytes[0].toLong() and 0x0F) shl 8) or (bytes[1].toLong() and 0xFF)
        // 하위 64비트 = 변형 2(10) · 난수 62
        var lsb = 0L
        for (i in 2 until RANDOM_BYTES) lsb = (lsb shl 8) or (bytes[i].toLong() and 0xFF)
        lsb = (lsb and VARIANT_MASK) or Long.MIN_VALUE
        return UUID(msb, lsb).toString()
    }

    private const val RANDOM_BYTES = 10
    private const val VERSION_7 = 0x7000L
    private const val VARIANT_MASK = 0x3FFFFFFFFFFFFFFFL
}
