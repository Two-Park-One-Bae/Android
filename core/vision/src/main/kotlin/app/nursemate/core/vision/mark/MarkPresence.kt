package app.nursemate.core.vision.mark

/**
 * 마크 유무 점수를 계약의 `hasMark` 로 접는다 — 정본은 계약(NM-502), 우리 쪽은 NM-515.
 *
 * ## 임계가 두 갈래다
 * 같은 면의 **각인을 읽었는지**에 따라 다르다. 2026-09-30 팀 결정이다.
 *
 * | 같은 면 각인 | 임계 | 왜 |
 * |---|---|---|
 * | 읽었다 | **0.80** | 각인이 이미 후보를 중앙값 2개로 좁혀 놨다. 남은 건 마크 오탐 비용뿐이라 엄격하게 |
 * | 못 읽었다 | **0.60** | 후보가 25,246개다. 마크가 **유일하게 센 좁히는 신호**라 느슨하게 |
 *
 * `hasMark` 는 **면 단위 필드**라 앞면이 각인을 읽고 뒷면이 못 읽었으면 앞면은 0.80, 뒷면은
 * 0.60 을 쓴다. 면마다 따로 판단한다.
 *
 * ## `false` 를 보내지 않는다
 * 임계에 못 미치면 **null** 이다 — 「마크 없음」이 아니라 「모르겠다」다.
 *
 * 서버는 각인·구분선·마크 유무를 한 면에서 **동시에** 검사한다. 모델이 마크를 놓쳐 `false` 를
 * 보내면 앞면은 마크 때문에, 뒷면은 각인이 달라 탈락해 **정답 약이 통째로 빠진다.**
 * `false` 는 사용자가 「마크 없음」을 직접 골랐을 때만 나간다.
 *
 * ## 임계를 모델 메타데이터에서 읽지 않는다
 * species 모델이 적어 둔 권장값은 `presence_hint` 0.5 · `presence_sure` 0.8 이고 **0.60 은
 * 거기 없다.** 계약이 정한 값이라 앱 상수로 둔다.
 */
object MarkPresence {

    /** 같은 면의 각인을 읽었을 때. 모델 메타데이터의 `presence_sure` 와 우연히 같다. */
    const val WITH_IMPRINT = 0.80f

    /** 같은 면의 각인을 못 읽었을 때. **모델 메타데이터에 없는 값이다.** */
    const val WITHOUT_IMPRINT = 0.60f

    /**
     * @param score [MarkReader.Result.presence] — `1 − P(없음)`
     * @param imprint 같은 면의 각인 모델값. **못 읽었으면 null 이다**(빈 문자열이 아니다 —
     *                각인 모델은 빈 문자열을 내지 않는다)
     * @return `true` 또는 **null**. `false` 는 나오지 않는다
     */
    fun hasMark(score: Float, imprint: String?): Boolean? {
        val threshold = if (imprint != null) WITH_IMPRINT else WITHOUT_IMPRINT
        return if (score >= threshold) true else null
    }
}
