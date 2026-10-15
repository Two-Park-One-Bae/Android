package app.nursemate.core.model

import kotlinx.serialization.Serializable

// 식별 기록 (NM-552) — 정본 `spec/api/domains/history.md` · openapi `/api/v0/pill-histories`.
//
// 한 건 = 촬영 한 번. 확정한 알약만 남기고 **사진은 남기지 않는다**(약포지에 환자 이름이 있을 수
// 있다). 화면의 알약 사진은 품목 낱알 이미지([PillHistoryPill.pillThumbnailUrl])다.

/** 목록 · 홈 최근 식별 카드용 요약. 알약 목록은 [PillHistory] 한 건 조회로 받는다. */
@Serializable
data class PillHistorySummary(
    val historyId: String,
    /** 처음 저장한 서버 시각(ISO-8601). */
    val identifiedAt: String,
    val firstPillName: String,
    val pillCount: Int
)

@Serializable
data class PillHistoryPage(
    val items: List<PillHistorySummary>,
    /** 다음 묶음이 없으면 null. */
    val nextCursor: String? = null
)

/** 기록 한 건. [pills] 는 화면 순서 그대로다. */
@Serializable
data class PillHistory(val historyId: String, val identifiedAt: String, val pills: List<PillHistoryPill>)

/** 기록 속 알약. 품목명 · 업체명 · 허가상태는 **저장할 때의 값**이다 — 카탈로그가 바뀌어도 그대로다. */
@Serializable
data class PillHistoryPill(
    val pillCode: String,
    val pillName: String,
    val companyName: String,
    val licenseStatus: LicenseStatus,
    val pillThumbnailUrl: String
)

/** 저장 요청 — 확정한 품목만, 화면 순서 그대로. 같은 품목이 여러 번 올 수 있다. */
@Serializable
data class PillHistorySaveRequest(val pillCodes: List<String>)
