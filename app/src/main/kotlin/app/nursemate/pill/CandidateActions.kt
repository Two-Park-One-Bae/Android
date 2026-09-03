package app.nursemate.pill

import app.nursemate.core.model.PillCandidate

/**
 * 후보 목록에서 갈라지는 동작 넷.
 *
 * 한 행에 과녁이 셋이라(카드=선택 · 썸네일=이미지 비교 · ⓘ=세부정보) 화면에서 목록까지
 * 콜백을 낱개로 넘기면 인자만 길어진다. 함께 다니는 것들이라 묶어 둔다.
 */
data class CandidateActions(
    val onSelect: (PillCandidate) -> Unit,
    val onDetail: (PillCandidate) -> Unit,
    val onThumbnail: (PillCandidate) -> Unit,
    val onLoadMore: () -> Unit
)
