package app.nursemate.wear.ui

import androidx.compose.ui.graphics.Color

/**
 * 워치 화면 색 — 정본 `타이머 워치 / W1·W2·W3` 프레임의 값.
 *
 * ## 왜 `core:designsystem` 을 안 쓰나
 * 그 모듈은 **폰용 Material3** 를 끌고 온다. 워치 앱 클래스패스에 그게 올라오면
 * `MaterialTheme`·`Text` 가 두 벌이 되어, 잘못 import 해도 컴파일은 되고 화면만 이상해진다.
 * 워치에서 실제로 쓰는 색이 몇 개뿐이라 여기 옮겨 적었다 — 값이 갈리지 않게 정본 토큰
 * 이름을 그대로 주석에 남긴다. (토큰만 담은 공용 모듈로 빼는 게 장기 해법이다.)
 */
internal object WearTimerColors {

    /** 워치는 항상 검은 배경이다 — 정본이자 Wear 품질요건(WO-V13). OLED 전력에도 유리하다. */
    val Background = Color(0xFF000000)

    /** 카드 바탕. 정본 `#1C1C1E`. */
    val Card = Color(0xFF1C1C1E)

    /** 링 트랙. 정본 `#3A3A3C`. */
    val Track = Color(0xFF3A3A3C)

    /** 보조 텍스트·비활성. 정본 `#8E8E93`. */
    val Muted = Color(0xFF8E8E93)

    /** 본문 텍스트. 정본 `#F8FAFC` = `$neutral-50`. */
    val OnBackground = Color(0xFFF8FAFC)

    /** `$primary-500` — 진행 링·시작 아이콘. */
    val Primary = Color(0xFF0EA5E9)

    /** `$primary-300` — 남은 시간. */
    val PrimarySoft = Color(0xFF7DD3FC)

    /** 만료 카드 바탕. 정본 `#422006`. */
    val ExpiredSurface = Color(0xFF422006)

    /** `$warning-500` — 만료 아이콘·시간. */
    val Warning = Color(0xFFF59E0B)

    /** `$warning-600` — [완료] 버튼·만료 테두리. */
    val WarningStrong = Color(0xFFD97706)

    /** `$info-300` — 분류 「검사」. */
    val CategoryTest = Color(0xFF93C5FD)

    /** `$secondary-300` — 분류 「처치」. */
    val CategoryTreatment = Color(0xFF5EEAD4)

    /** `$primary-300` — 분류 「투약」. 정본에 프레임이 없어 같은 계열로 맞췄다. */
    val CategoryMedication = Color(0xFF7DD3FC)
}
