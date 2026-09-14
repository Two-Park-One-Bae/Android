package app.nursemate.core.model

// 타이머 시간 표기.
//
// ⚠️ 폰·워치·위젯이 **같은 함수**를 쓴다. 표기가 표면마다 다르면 같은 타이머가 다르게 보인다.
// 색·아이콘처럼 플랫폼마다 달라도 되는 것과 달리, 남은 시간은 어디서 보든 같아야 한다.
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
 * 만료 후 지난 시간 — `-03:12`.
 *
 * ## 정본은 `00:00` 고정이다
 * 그런데 spec 이 "[완료] 를 누를 때까지 지속"이라 만료 카드가 오래 남는 것을 전제한다.
 * 병동에서 30초 전과 10분 전은 대응이 다르고, 만료가 여러 개 쌓이면 어느 것이 급한지도
 * 알 수 없다. 자리는 정본 그대로 두고 값만 경과 시간으로 바꿨다(2026-09-09 결정,
 * spec·디자인 개정 요청 대상 — iOS 도 `00:00` 하드코딩이다).
 */
fun formatOverdue(elapsedSeconds: Int): String = "-" + formatRemaining(elapsedSeconds)

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
