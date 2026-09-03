package app.nursemate.pill

/**
 * 수정 화면에서 지금 펼쳐 둔 선택판.
 *
 * 한 번에 하나만 연다 — 다 펼치면 카드가 화면을 다 먹어 정작 결과인 후보 목록이 밖으로 밀린다.
 * 열려 있는 칩(또는 각인 줄)을 다시 누르면 접힌다.
 */
enum class AttributePanel { Color, Shape, Formulation, Imprint }
