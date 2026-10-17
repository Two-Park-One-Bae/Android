package app.nursemate.update

/**
 * 인앱 업데이트를 **지금 권할지** — NM-626.
 *
 * 매 실행마다 묻지 않는다. 한 번 권했으면(수락했든 닫았든) [INTERVAL_MS] 동안 다시 권하지 않는다.
 * 강제 업데이트(Remote Config 게이트)와 달리 이건 **권유**라, 귀찮게 하면 끄는 법을 찾게 된다.
 *
 * 기기 · Play 와 무관한 판단만 둔다 — 단위 테스트로 못박으려고 따로 뺐다.
 */
internal object UpdateOfferPolicy {

    /** 다시 권하기까지 3일. Play 자동 업데이트가 대부분을 먼저 올려 주므로 짧게 잡을 이유가 없다. */
    const val INTERVAL_MS: Long = 3L * 24 * 60 * 60 * 1000

    /**
     * @param available Play 가 새 버전이 있고 **유연 업데이트를 허용한다**고 답했는가
     * @param lastOfferedAtMs 마지막으로 권한 시각. 권한 적이 없으면 0
     */
    // 기기 시계를 뒤로 돌린 경우(now < last)는 간격이 안 찬 것으로 본다 — 계속 묻는 쪽보다 낫다.
    fun shouldOffer(available: Boolean, nowMs: Long, lastOfferedAtMs: Long): Boolean =
        available && (lastOfferedAtMs <= 0L || nowMs - lastOfferedAtMs >= INTERVAL_MS)
}
