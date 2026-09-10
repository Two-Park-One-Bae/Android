package app.nursemate.core.model

// 만료 알람의 진동 — 폰·워치가 **같은 패턴**을 쓴다.
//
// spec §만료·알람이 "[완료] 를 누를 때까지 지속되며, 중간에 저절로 사라지거나 임의로 끄는
// 상태는 없다"고 요구한다. 표면마다 길이가 다르면 워치만 먼저 조용해져 놓치게 된다.

/**
 * 끊어 치는 긴 진동.
 *
 * ⚠️ **짧게 만들면 안 된다.** 알림 채널의 진동은 **한 번만** 재생되고 반복 설정이 없다
 * (`FLAG_INSISTENT` 는 소리만 반복한다). 그래서 "지속"을 패턴 길이로 만들어야 한다.
 * 워치에서 3회짜리(2초)로 뒀더니 **한 번 울리고 마는 것처럼 느껴졌다.**
 *
 * 알림을 취소하면 남은 진동도 함께 멈춘다 — [완료] 를 누르면 바로 조용해진다.
 */
val TIMER_SUSTAINED_VIBRATION: LongArray = LongArray(SUSTAIN_CYCLES * 2) { index ->
    if (index % 2 == 0) VIBRATE_MS else PAUSE_MS
}

private const val VIBRATE_MS = 800L
private const val PAUSE_MS = 400L

/** 800 + 400 을 50번 = 약 60초. 병동에서 손이 바쁠 때 놓치지 않을 만큼. */
private const val SUSTAIN_CYCLES = 50
