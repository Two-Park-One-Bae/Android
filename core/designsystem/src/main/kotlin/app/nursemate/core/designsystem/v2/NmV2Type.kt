package app.nursemate.core.designsystem.v2

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.PretendardFamily

/**
 * V2.1.0 컴포넌트가 쓰는 글자 — 정본 컴포넌트에 적힌 크기 · 굵기 그대로다.
 *
 * 타입 스케일([app.nursemate.core.designsystem.NmTypography])에 없는 크기(16/600 · 13/500 등)를
 * 컴포넌트가 직접 쓰므로 여기에 모았다. 글꼴은 앱 정본인 Pretendard 다(정본 파일은 Noto Sans KR 로
 * 그려져 있지만 앱은 MVP 부터 Pretendard 를 쓴다 — 바꾸지 않는다, NM-580 「글꼴」).
 */
object NmV2Type {
    /**
     * 줄 높이는 글자 크기의 1.4배로 둔다. 정본 텍스트 노드에는 줄 높이가 없어 글꼴 기본값으로 그려지는데,
     * 그 값은 글꼴마다 다르다(Noto Sans KR · Pretendard). 기존 컴포넌트가 같은 비율을 쓴다
     * — `NmBanner` 13/18.
     */
    private fun style(size: Int, weight: FontWeight) = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = (size * LINE_HEIGHT_RATIO).sp
    )

    private const val LINE_HEIGHT_RATIO = 1.4f

    /** 버튼 · 카드 제목 · 모달 제목 — 16/600 */
    val button = style(16, FontWeight.SemiBold)

    /** 텍스트 버튼 · 목록 행 제목 · 프리셋 이름 — 15/600 */
    val textButton = style(15, FontWeight.SemiBold)
    val rowTitle = textButton

    /** 입력 칸 값 — 15/400 */
    val field = style(15, FontWeight.Normal)

    /** 내비게이션 바 제목 — 17/600 */
    val navTitle = style(17, FontWeight.SemiBold)

    /** 메뉴 항목 — 14/500 */
    val menu = style(14, FontWeight.Medium)

    /** 칩 · 배너 · 입력 칸 라벨 · 프리셋 시간 — 13/500 */
    val chip = style(13, FontWeight.Medium)

    /** 확정 드롭다운 칩 — 13/600 */
    val chipConfirmed = style(13, FontWeight.SemiBold)

    /** 카드 본문 — 13/400 */
    val cardBody = style(13, FontWeight.Normal)

    /** 목록 행 보조 설명 — 12/400 */
    val rowSubtitle = style(12, FontWeight.Normal)

    /** 태그 — 11/500 */
    val tag = style(11, FontWeight.Medium)

    /** 카드 구역 제목(진행 중인 타이머) — 16/700 */
    val sectionTitle = style(16, FontWeight.Bold)

    /** 기능 카드 제목 — 15/700 */
    val tileTitle = style(15, FontWeight.Bold)

    /** 기능 카드 숫자 — 34/700 */
    val metric = style(34, FontWeight.Bold)

    /** 기능 카드 숫자 단위(`/15` · `개`) — 14/600 */
    val metricUnit = style(14, FontWeight.SemiBold)

    /** 남은 시간 — 20/700 */
    val clock = style(20, FontWeight.Bold)

    /** 카드 안 작은 버튼 — 13/700 */
    val smallButton = style(13, FontWeight.Bold)

    /** 글자 링크(`전체 보기`) — 13/600 */
    val link = chipConfirmed

    /** 빈 상태 문구 — 13/400 */
    val empty = cardBody

    /** 보조 캡션(초기화 시각) — 11/400 */
    val caption = style(11, FontWeight.Normal)

    /** 탭 바 라벨 — 10 (선택 600 · 나머지 400) */
    val tabSelected = style(10, FontWeight.SemiBold)
    val tab = style(10, FontWeight.Normal)
}
