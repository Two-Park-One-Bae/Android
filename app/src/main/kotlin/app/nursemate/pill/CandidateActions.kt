package app.nursemate.pill

import app.nursemate.core.model.PillCandidate

/**
 * 후보 목록에서 갈라지는 동작.
 *
 * 한 행에 과녁이 셋이라(카드=선택 · 썸네일=이미지 비교 · ⓘ=세부정보) 화면에서 목록까지
 * 콜백을 낱개로 넘기면 인자만 길어진다. 함께 다니는 것들이라 묶어 둔다.
 *
 * @param onRetry 조회 실패에서 **지금 조건으로 다시 조회**한다
 * @param onRetryLoadMore 이어서 조회 실패에서 같은 구간을 다시 부른다. 둘은 다른 일이다 —
 *   앞은 목록을 새로 받고, 뒤는 보이는 후보에 이어 붙인다(NM-529)
 */
data class CandidateActions(
    val onSelect: (PillCandidate) -> Unit,
    val onDetail: (PillCandidate) -> Unit,
    val onThumbnail: (PillCandidate) -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryLoadMore: () -> Unit
)
