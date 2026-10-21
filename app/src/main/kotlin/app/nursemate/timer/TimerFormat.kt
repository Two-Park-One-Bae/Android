package app.nursemate.timer

import androidx.compose.ui.graphics.Color
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.v2.NmV2TagTone
import app.nursemate.core.model.TimerCategory

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

/**
 * V2.1.0 태그 색 — [tagBackground] · [tagForeground] 와 같은 짝을 V2 태그 컴포넌트에 맞춰 낸다.
 * 타이머 탭이 V2.1.0 으로 바뀌면(NM-614) 위 두 함수는 이것으로 대신한다.
 */
val TimerCategory.v2Tone: NmV2TagTone
    get() = when (this) {
        TimerCategory.TEST -> NmV2TagTone.Info
        TimerCategory.MEDICATION -> NmV2TagTone.Primary
        TimerCategory.TREATMENT -> NmV2TagTone.Secondary
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
