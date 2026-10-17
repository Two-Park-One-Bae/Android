package app.nursemate.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateOfferPolicyTest {

    private val now = 1_800_000_000_000L
    private val interval = UpdateOfferPolicy.INTERVAL_MS

    @Test fun `새 버전이 없으면 권하지 않는다`() {
        assertFalse(UpdateOfferPolicy.shouldOffer(available = false, nowMs = now, lastOfferedAtMs = 0L))
    }

    @Test fun `처음이면 바로 권한다`() {
        assertTrue(UpdateOfferPolicy.shouldOffer(available = true, nowMs = now, lastOfferedAtMs = 0L))
    }

    @Test fun `간격이 안 찼으면 다시 묻지 않는다`() {
        assertFalse(UpdateOfferPolicy.shouldOffer(true, now, lastOfferedAtMs = now - interval + 1))
    }

    @Test fun `간격이 차면 다시 권한다`() {
        assertTrue(UpdateOfferPolicy.shouldOffer(true, now, lastOfferedAtMs = now - interval))
    }

    @Test fun `시계를 뒤로 돌려도 계속 묻지 않는다`() {
        assertFalse(UpdateOfferPolicy.shouldOffer(true, now, lastOfferedAtMs = now + interval))
    }
}
