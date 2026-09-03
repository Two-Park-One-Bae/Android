package app.nursemate.pill

import androidx.compose.ui.graphics.Color
import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillFormulation
import app.nursemate.core.model.PillShape

/**
 * 서버 enum 을 화면 표기로 옮긴다.
 *
 * 한글 이름은 **정본(`spec/api/domains/enums.md`)의 표기를 그대로** 쓴다. 임의로 다듬으면
 * iOS·서버 로그와 말이 달라져 같은 알약을 두고 서로 다른 단어로 이야기하게 된다.
 */
internal val PillShape.label: String
    get() = when (this) {
        PillShape.ROUND -> "원형"

        PillShape.OVAL -> "타원형"

        PillShape.OBLONG -> "장방형"

        PillShape.SEMICIRCLE -> "반원형"

        PillShape.TRIANGLE -> "삼각형"

        PillShape.SQUARE -> "사각형"

        PillShape.DIAMOND -> "마름모형"

        PillShape.PENTAGON -> "오각형"

        PillShape.HEXAGON -> "육각형"

        PillShape.OCTAGON -> "팔각형"

        PillShape.OTHER -> "기타"

        // 서버가 값을 늘렸는데 앱이 모르는 경우. '기타'로 뭉뚱그리면 사용자가 그게 맞는 줄 안다.
        PillShape.UNKNOWN -> "미인식"
    }

internal val PillFormulation.label: String
    get() = when (this) {
        PillFormulation.TABLET -> "정제"
        PillFormulation.HARD_CAPSULE -> "경질캡슐"
        PillFormulation.SOFT_CAPSULE -> "연질캡슐"
        PillFormulation.OTHER -> "기타"
        PillFormulation.UNKNOWN -> "미인식"
    }

internal val DividingLine.label: String
    get() = when (this) {
        DividingLine.PLUS -> "+"

        DividingLine.MINUS -> "−"

        // 응답에서는 오지 않는 값이다(없으면 필드가 null). 후보 검색 필터 전용.
        DividingLine.NONE -> "없음"

        DividingLine.UNKNOWN -> "미인식"
    }

/**
 * 색 스와치.
 *
 * 정본은 색 **이름**을 적지 않고 점으로만 보여준다 — 다색 알약이 흔해서 이름을 늘어놓으면
 * 줄이 길어진다. 그래서 여기서 필요한 건 hex 뿐이다.
 *
 * 하양·무색처럼 배경과 구분되지 않는 색은 [needsOutline] 로 테두리를 준다.
 */
internal val PillColor.swatch: Color
    get() = when (this) {
        PillColor.WHITE -> Color(0xFFFFFFFF)
        PillColor.YELLOW -> Color(0xFFFACC15)
        PillColor.ORANGE -> Color(0xFFFB923C)
        PillColor.PINK -> Color(0xFFF9A8D4)
        PillColor.RED -> Color(0xFFEF4444)
        PillColor.BROWN -> Color(0xFFA16207)
        PillColor.LIGHT_GREEN -> Color(0xFFA3E635)
        PillColor.GREEN -> Color(0xFF22C55E)
        PillColor.TEAL -> Color(0xFF14B8A6)
        PillColor.BLUE -> Color(0xFF3B82F6)
        PillColor.NAVY -> Color(0xFF1E3A8A)
        PillColor.MAGENTA -> Color(0xFFDB2777)
        PillColor.PURPLE -> Color(0xFFA855F7)
        PillColor.GRAY -> Color(0xFF9CA3AF)
        PillColor.BLACK -> Color(0xFF111827)
        PillColor.COLORLESS -> Color(0xFFF8FAFC)
        PillColor.UNKNOWN -> Color(0xFFE2E8F0)
    }

/** 배경(흰 카드)과 구분되지 않아 테두리가 필요한 색. */
internal val PillColor.needsOutline: Boolean
    get() = this == PillColor.WHITE || this == PillColor.COLORLESS || this == PillColor.UNKNOWN
