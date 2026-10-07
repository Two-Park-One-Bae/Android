package app.nursemate.core.model

import kotlinx.serialization.Serializable

/**
 * 사용자가 정한 **후보 조건** — `POST /api/v1/pill-candidates` 요청의 알맹이다 (NM-516 · NM-517).
 *
 * ## 왜 [PillAttribute] 와 따로 두는가
 * V1 에서 **모델값과 사용자값은 하는 일이 다르다.**
 *
 * | | 모델값 ([PillAttribute]) | 사용자값 (여기) |
 * |---|---|---|
 * | 색 | `colorHexes` — 보여 주고 되돌리기용 | [colors] — **점수**(자르지 않는다) |
 * | 모양·제형 | 대표값 — 표시·되돌리기용 | [shape]·[formulation] — **하드 필터** |
 * | 정렬 | `attributeToken` 으로 서버가 쓴다 | — |
 *
 * v0 은 둘을 한 객체에 담아 사용자가 고친 값을 모델값 위에 덮어썼다. V1 에서 그러면
 * **모델이 추정한 모양이 하드 필터로 나가 정답 약을 떨어뜨린다** — 계약이 못박는다.
 *
 * > 사용자가 직접 고른 모양만 — 하드 필터. 속성 추출 응답의 **대표 모양을 그대로 보내지 않는다.**
 *
 * ## 비어 있는 것이 기본이다
 * 촬영 직후에는 조건이 하나도 없다. 모델이 무엇을 읽었든 **후보를 자르지 않는다** —
 * 사용자가 고른 것만 조건이 된다. 화면은 그 차이를 칸 색으로 드러낸다
 * (회색 = 추정값·조건 없음 · 호박색 = 확실한 값).
 *
 * @param attributeToken 속성 추출 응답의 토큰을 **그대로** 되돌려준다. 사용자가 무엇을 고쳤든
 *   항상 보낸다 — 정렬에 쓰인다. 수동 추가·추출 실패 알약은 null
 * @param colors 사용자가 **직접 고른** 색만. 모델 색은 넣지 않는다(토큰이 대신한다).
 *   비어 있으면 색 항이 빠진다
 * @param shape 사용자가 **직접 고른** 모양만. null 이면 조건 없음
 * @param formulation 사용자가 **직접 고른** 제형만. null 이면 조건 없음
 */
@Serializable
data class PillConditions(
    val attributeToken: String? = null,
    val colors: List<PillColor> = emptyList(),
    val shape: PillShape? = null,
    val formulation: PillFormulation? = null,
    val front: PillFaceRequest? = null,
    val back: PillFaceRequest? = null
) {
    /** 후보를 실제로 **자르는** 조건이 하나라도 있는가. 색은 점수라 세지 않는다. */
    val hasHardFilter: Boolean
        get() = shape != null || formulation != null || front?.isEmpty() == false || back?.isEmpty() == false
}

/**
 * 각인 값의 출처.
 *
 * 서버가 둘을 **다르게 매칭한다** — 틀리면 예외가 아니라 조용히 다른 후보가 나온다.
 */
@Serializable
enum class ImprintSource {
    /** 온디바이스 OCR 이 읽은 확신 글자. 다른 글자가 더 있을 수 있다는 전제로 매칭된다. */
    MODEL,

    /** 사용자가 직접 입력했다. 각인 전체를 적지 않아도 찾아지도록 서버가 맞춰 준다. */
    USER
}
