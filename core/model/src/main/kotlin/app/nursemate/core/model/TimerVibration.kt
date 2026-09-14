package app.nursemate.core.model

// 만료 알람의 진동 — 폰·워치가 **같은 패턴**을 쓴다.
//
// spec §만료·알람이 "[완료] 를 누를 때까지 지속되며, 중간에 저절로 사라지거나 임의로 끄는
// 상태는 없다"고 요구한다. 표면마다 길이가 다르면 워치만 먼저 조용해져 놓치게 된다.

/**
 * 끊어 치는 진동 한 마디 — 0.4초 쉬고 0.8초 떤다.
 *
 * ⚠️ **배열의 첫 값은 「켜기 전 대기」다.** 진동부터 적는 줄 알고 `[800, 400]` 으로 두어
 * 실제로는 **0.8초 기다린 뒤 0.4초만 떨었다** — 의도의 반대다.
 *
 * ⚠️ **한 마디로 충분하다 — 길게 늘이지 않는다.** "지속"은 패턴 길이가 아니라 **무한 반복**이
 * 만든다(`createWaveform(pattern, repeat = 0)`). 폰·워치 모두 포그라운드 서비스가 이 파형을
 * 직접 몰고 [완료] 에서 [android.os.Vibrator.cancel] 로 끊는다. 예전에는 알림 채널에 맡겨
 * 한 번만 재생됐기 때문에 50마디(약 60초)를 배열에 박아 두었는데, 반복으로 도는 지금은
 * 앞의 한 마디만 계속 반복돼 나머지 49마디가 그대로 죽은 값이었다.
 */
val TIMER_SUSTAINED_VIBRATION: LongArray = longArrayOf(PAUSE_MS, VIBRATE_MS)

private const val VIBRATE_MS = 800L
private const val PAUSE_MS = 400L
