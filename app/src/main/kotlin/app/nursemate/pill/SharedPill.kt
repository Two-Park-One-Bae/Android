package app.nursemate.pill

import app.nursemate.core.model.PillCandidate

/**
 * 공유물에 들어가는 알약 한 개 — 식별 결과의 확정 후보와 식별 기록의 알약이 같은 모양으로 나간다.
 * 앱 밖으로 나가는 문서라 어디서 공유했든 같아야 한다.
 */
data class SharedPill(val pillName: String, val companyName: String, val pillCode: String)

fun PillCandidate.toSharedPill() = SharedPill(pillName, companyName, pillCode)
