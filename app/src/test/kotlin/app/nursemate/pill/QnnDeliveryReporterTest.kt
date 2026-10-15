package app.nursemate.pill

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QnnDeliveryReporterTest {

    @Test
    fun `퀄컴만 보고한다`() {
        assertTrue(QnnDeliveryReporter.isQualcomm("QTI"))
        // 엑시노스·텐서·미디어텍은 QNN 이 없어 none 이 정상이다 — 분모를 흐리지 않게 뺀다.
        assertFalse(QnnDeliveryReporter.isQualcomm("Samsung"))
        assertFalse(QnnDeliveryReporter.isQualcomm("Google"))
        assertFalse(QnnDeliveryReporter.isQualcomm("Mediatek"))
        assertFalse(QnnDeliveryReporter.isQualcomm(""))
    }
}
