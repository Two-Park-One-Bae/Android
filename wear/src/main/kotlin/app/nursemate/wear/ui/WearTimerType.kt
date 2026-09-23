package app.nursemate.wear.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * 워치 글자 — 정본 `타이머 워치 / W1` 값을 그대로 옮긴 것.
 *
 * 정본 프레임은 애플워치 **2배 픽셀**이라 절반이 실제 크기다(396px 폭 ≈ 198pt, 이 워치는
 * 203dp 로 거의 같다). Wear Material3 기본 타이포를 쓰면 2~4sp 씩 커져 203dp 화면에서
 * 줄이 넘치고 카드가 부푼다.
 *
 * ⚠️ **하한을 12sp 로 올렸다 — 정본보다 큰 값이 둘 있다.**
 * Wear 품질요건 WO-V14 가 핵심 텍스트에 최소 12sp 를 요구하는데 정본의 남은 시간(11.5)과
 * 빈 상태 제목(11)이 그 아래다. 위계는 정본 그대로 두고 그 둘만 올렸다 — 정본 개정 요청 대상.
 */
internal object WearTimerType {

    /**
     * 여러 줄로 감길 수 있는 한글에 쓴다.
     *
     * ⚠️ **기본 줄바꿈은 한글을 단어 중간에서 끊는다.** 「타일에 추 / 가해보세요」,
     * 「진행 중인 타이머가 없어 / 요」처럼 나왔다(실기기 확인).
     *
     * 어절 단위로 끊으려면 [LineBreak.WordBreak.Phrase] 가 필요한데, 그것만으로는 안 된다 —
     * **글자의 로케일이 한국어여야** 플랫폼이 한국어 어절 규칙을 쓴다. 기기 로케일이 영어면
     * 조용히 무시된다(에뮬레이터에서 그랬다). 우리 문구는 전부 한국어 고정이라
     * 기기 설정과 무관하게 여기서 못박는다.
     */
    fun TextStyle.wrapKorean(): TextStyle = copy(
        lineBreak = LineBreak(
            strategy = LineBreak.Strategy.Balanced,
            strictness = LineBreak.Strictness.Normal,
            wordBreak = LineBreak.WordBreak.Phrase
        ),
        localeList = KoreanLocale
    )

    private val KoreanLocale = LocaleList("ko-KR")

    /** 페이지 제목 「타이머」·「프리셋」 — 정본 30px/700. */
    val Header = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)

    // ⚠️ 처치 키워드의 굵기는 자리마다 다르다 — 합치지 않는다.
    // 만료가 가장 굵어 목록에서 먼저 읽히는 것이 정본의 의도다.
    //
    // ⚠️ **크기는 정본보다 한 단계 크다.** 정본 행은 31.9dp 인데 Wear `Button` 의 최소가
    // 52dp 라 낮출 수가 없다(`requiredHeight` 로도 안 된다 — 컴포넌트가 자기 최소를 바깥에서
    // 다시 건다). 행만 커지고 글자는 그대로면 안이 비어 보여, 행에 맞춰 함께 키웠다.
    // 정본 비율(행 대비 47%)을 그대로 적용하면 20sp 가 돼 과하므로 절충한 값이다.

    /**
     * 목록 행 제목 — 정본 프리셋 24px/500 · 진행 중 25px/600. 행 높이에 맞춰 14sp.
     *
     * 프리셋과 활성이 같은 값을 쓴다. 정본은 둘의 굵기를 반 단계 다르게 두었는데, 같은
     * 목록의 두 페이지라 넘길 때 글자 굵기가 바뀌는 것이 오히려 눈에 걸렸다.
     */
    val RowTitle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

    /** 목록 행 둘째 줄 — 「남은시간 · 분류」. **WO-V14 하한 12sp.** */
    val RowSubtitle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)

    /** 만료 카드 — 정본 24px/700(=12), 행 높이에 맞춰 14sp. */
    val ExpiredLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)

    /** 만료 경과 시간 — 정본 22px/700. */
    val Overdue = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)

    /** 분류 태그 — 정본 19px/normal. 보조 정보라 10sp 하한을 따른다. */
    val Category = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)

    /** 「누르면 바로 시작됩니다」 — 정본 19px/normal. */
    val Hint = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal)

    /** 「프리셋에서 시작하세요」 — 정본 20px/normal. */
    val EmptyHint = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal)

    /** 빈 상태 제목 — 정본 22px/600(=11). **WO-V14 때문에 12sp 로 올림.** */
    val EmptyTitle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

    /** W2 헤더의 처치 키워드 — 정본 27px/600. */
    val DetailLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

    /** W2 가운데 큰 남은 시간 — 정본 48px/700. */
    val DetailRemaining = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)

    /**
     * W2 제목 아래 분류 캡션 — 정본 19px/normal(=9.5). **WO-V14 하한인 10sp 로 올림.**
     *
     * 헤더 칸의 4/10(192dp 에서 12.4dp)에 들어가야 해서 [Category](11sp)보다 작다.
     * 보조 텍스트라 10sp 까지는 허용되지만 **더 줄일 수는 없다** — 이 값이 바닥이다.
     */
    val DetailCaption = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)

    /** W2 링 아래 전체 시간 — 정본 21px/normal. */
    val DetailTotal = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)

    /** 「완료」 — 정본 22px/600. */
    val Action = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

    /** 정본 「W3 만료」의 제목 — 34px ÷2. 화면을 덮는 알람이라 목록 헤더보다 크다. */
    val AlarmTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
}
