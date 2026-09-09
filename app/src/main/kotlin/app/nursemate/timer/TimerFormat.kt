package app.nursemate.timer

import androidx.compose.ui.graphics.Color
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.model.TimerCategory

/**
 * 남은 시간 표기 — 정본 `타이머 / C1 리스트`.
 *
 * 한 시간 미만은 `12:34`(분:초), 넘으면 `1:12:40`(시:분:초)다. 항상 시:분:초로 쓰면
 * 15분 타이머가 `00:12:34` 로 보여 자리만 차지한다.
 */
fun formatRemaining(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val secs = s % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}

/**
 * 전체 시간 표기 — 정본은 `15분` · `30분` · `2시간` 처럼 사람이 읽는 단위로 쓴다.
 *
 * ⚠️ **0인 단위는 빼되, 전부 0이면 초로 쓴다.** 분 단위로만 계산하면 15초짜리가 `0분` 이 돼
 * 무엇을 맞춰 놨는지 알 수 없다(프리셋은 초 단위까지 고를 수 있다).
 */
fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val parts = buildList {
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val secs = s % 60
        if (hours > 0) add("${hours}시간")
        if (minutes > 0) add("${minutes}분")
        if (secs > 0) add("${secs}초")
    }
    return parts.joinToString(" ").ifEmpty { "0초" }
}

/**
 * 분류 태그 색 — 정본이 분류마다 다른 배경을 쓴다.
 *
 * 검사 `info-50` · 투약 `primary-50` · 처치 `secondary-50`.
 * 글자색은 같은 계열 700 이다(만료 카드에서 배경만 `neutral-0` 으로 바뀐다).
 */
fun tagBackground(category: TimerCategory): Color = when (category) {
    TimerCategory.TEST -> NmColor.Info.C50
    TimerCategory.MEDICATION -> NmColor.Primary.C50
    TimerCategory.TREATMENT -> NmColor.Secondary.C50
}

fun tagForeground(category: TimerCategory): Color = when (category) {
    TimerCategory.TEST -> NmColor.Info.C700
    TimerCategory.MEDICATION -> NmColor.Primary.C700
    TimerCategory.TREATMENT -> NmColor.Secondary.C700
}

/** 헤더 부제 — 정본 문구는 `진행 중 2 · 일시정지 1 · 종료 1` 이고, 0인 항목은 빼고 잇는다. */
fun timerCountLabel(running: Int, paused: Int, ringing: Int): String {
    val parts = buildList {
        if (running > 0) add("진행 중 $running")
        if (paused > 0) add("일시정지 $paused")
        if (ringing > 0) add("종료 $ringing")
    }
    return parts.joinToString(" · ")
}
