package app.nursemate.pill

import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillConditions

/**
 * 알약 하나의 **모델값과 사용자값**.
 *
 * ## 둘을 섞지 않는다 (NM-516)
 * v0 은 `PillAttribute` 하나에 사용자가 고친 값을 덮어썼다. V1 에서는 둘이 하는 일이 다르다 —
 * 모델값은 **정렬**에만 쓰이고(`attributeToken`), 후보를 **자르는** 것은 사용자가 고른 값뿐이다.
 * 섞으면 모델이 추정한 모양이 하드 필터로 나가 정답 약을 떨어뜨린다.
 *
 * @param attribute 서버가 준 모델값 — **읽기 전용**이다. 표시와 되돌리기에 쓴다
 * @param conditions 사용자가 정한 조건. 처음엔 [PillConditions.attributeToken] 만 들어 있고
 *   색·모양·제형은 비어 있다 — 사용자가 고르기 전에는 아무것도 거르지 않는다
 * @param faces 각인 입력. 「없음」(빈 문자열)과 「조건 없음」(null)을 가려야 해서 따로 둔다([FaceInput])
 */
data class PillEdit(val attribute: PillAttribute, val conditions: PillConditions, val faces: FaceInputs)

/**
 * 직전 값과 견줘 **무엇이 바뀌었는지** 이름으로 돌려준다 (`pill_attr_edit.attribute`).
 *
 * 화면은 조건을 한 덩이([PillConditions])로 넘겨 주므로 "무엇을 고쳤나"는 여기서 가려야 한다.
 * `transparent` 축은 V1 에서 사라졌다(NM-487) — 지표에서도 뺀다.
 * 이름은 iOS 와 같은 값을 쓴다 — 한 GA4 속성에 함께 쌓여 갈리면 축이 둘로 쪼개진다.
 *
 * 한 번에 둘 이상 바뀌는 입력은 없지만, 그렇더라도 **바뀐 것을 전부** 돌려준다 —
 * 조용히 하나만 세면 수정 횟수가 실제보다 적게 나온다.
 */
internal fun PillConditions.changesFrom(previous: PillConditions): List<String> = buildList {
    if (colors != previous.colors) add("color")
    if (shape != previous.shape) add("shape")
    if (formulation != previous.formulation) add("formulation")
}

/**
 * 이탈 시점까지 **채워진** 속성 요약 (`pill_flow_exit.entered_values`).
 *
 * 빈 문자열 대신 `"none"` 을 보낸다 — GA4 에서 빈 값은 `(not set)` 이라
 * "아무것도 안 넣었다"와 "파라미터가 안 왔다"가 구분되지 않는다.
 * 순서는 iOS 와 같게 고정한다(문자열 그대로 비교되는 축이라 순서가 다르면 다른 값이 된다).
 */
internal fun PillEdit.enteredValuesSummary(): String = buildList {
    if (conditions.shape != null) add("shape")
    if (conditions.colors.isNotEmpty()) add("color")
    if (conditions.formulation != null) add("formulation")
    if (faces.front.toFace() != null || faces.back.toFace() != null) add("imprint")
}.ifEmpty { listOf("none") }.joinToString(",")
