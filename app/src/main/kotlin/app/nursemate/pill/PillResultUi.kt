package app.nursemate.pill

import android.graphics.Bitmap
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.vision.DetectedPill

/**
 * 식별 결과 화면이 그리는 것 — [PillUiState] 에서 화면에 필요한 값만 뽑는다.
 *
 * 화면이 검출 결과 · 추출 결과 · 수정 내역을 직접 합치지 않게 여기서 한 번에 합친다. 캡처 테스트도
 * 이 모양으로 그린다.
 *
 * @param found 사진에서 찾은 알약 수 — `알약 N개를 찾았어요`. 직접 추가한 알약은 세지 않는다(정본 ③).
 */
data class PillResultUi(val photo: Bitmap?, val found: Int, val cards: List<PillResultCard>) {
    /** 확정한 알약 수 — 진행 표시의 분자. */
    val confirmed: Int get() = cards.count { it.selected != null }

    /** 확정하지 않은 알약 수 — `N개 남음` · 종료 확인 본문. */
    val unconfirmed: Int get() = cards.size - confirmed
}

/**
 * 한 줄 카드 하나.
 *
 * @param id 세션 로컬 키([pillId] · [manualPillId]) — 수정 · 삭제가 이 키로 간다
 * @param number 화면 번호(1부터). 지우면 다시 매긴다
 * @param detected 사진 위 영역과 잘라 낸 사진. 직접 추가한 알약은 없다
 */
data class PillResultCard(
    val id: String,
    val number: Int,
    val status: PillResultStatus,
    val detected: DetectedPill?,
    val selected: PillCandidate?
) {
    val manual: Boolean get() = id.isManualPill
}

/** 보이는 순서([pillIds])대로 카드를 만든다 — 번호 · 사진 표시 · 기록 저장이 모두 같은 차례를 따른다. */
fun PillUiState.toResultUi(): PillResultUi {
    val detectedById = (detection as? DetectionPhase.Success)?.result?.pills.orEmpty()
        .mapIndexed { index, pill -> pillId(index) to pill }
        .toMap()
    val cards = pillIds().mapIndexed { index, id ->
        PillResultCard(
            id = id,
            number = index + 1,
            status = PillResultStatus.of(editOf(id), selections[id]),
            detected = detectedById[id],
            selected = selections[id]
        )
    }
    return PillResultUi(photo = photo, found = cards.count { !it.manual }, cards = cards)
}
