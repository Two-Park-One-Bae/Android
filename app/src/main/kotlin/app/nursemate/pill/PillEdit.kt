package app.nursemate.pill

import app.nursemate.core.model.PillAttribute

/**
 * 알약 하나에 대해 사용자가 고친 값.
 *
 * 속성(색·모양·제형)과 각인을 **한 덩이로** 들고 다닌다. 둘을 따로 두면 화면마다 두 곳에서
 * 꺼내 합쳐야 하는데, 한 군데서 빠뜨리면 고친 값이 안 보이는 화면이 생긴다.
 *
 * @param attribute 색·모양·제형. 앞뒤 각인은 [faces] 가 정본이라 여기 실린 값은 쓰지 않는다.
 * @param faces 각인 입력. '해당 없음'을 담아야 해서 [PillAttribute] 와 따로 둔다([FaceInput]).
 */
data class PillEdit(val attribute: PillAttribute, val faces: FaceInputs)

/**
 * 직전 값과 견줘 **무엇이 바뀌었는지** 이름으로 돌려준다 (`pill_attr_edit.attribute`).
 *
 * 화면은 속성 넷을 한 덩이([PillAttribute])로 넘겨 주므로 "무엇을 고쳤나"는 여기서 가려야 한다.
 * 이름은 iOS 와 같은 값을 쓴다 — 한 GA4 속성에 함께 쌓여 갈리면 축이 둘로 쪼개진다.
 *
 * 한 번에 둘 이상 바뀌는 입력은 없지만, 그렇더라도 **바뀐 것을 전부** 돌려준다 —
 * 조용히 하나만 세면 수정 횟수가 실제보다 적게 나온다.
 */
internal fun PillAttribute.changesFrom(previous: PillAttribute): List<String> = buildList {
    if (colors != previous.colors) add("color")
    if (isTransparent != previous.isTransparent) add("transparent")
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
    if (attribute.shape != null) add("shape")
    if (!attribute.colors.isNullOrEmpty()) add("color")
    if (attribute.isTransparent) add("transparent")
    if (attribute.formulation != null) add("formulation")
    if (faces.front.toFace() != null || faces.back.toFace() != null) add("imprint")
}.ifEmpty { listOf("none") }.joinToString(",")
