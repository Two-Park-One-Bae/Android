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

    @Test
    fun `Play 설치만 센다`() {
        assertTrue(QnnDeliveryReporter.isFromPlay("com.android.vending"))
        // adb·Android Studio 설치는 feature module 을 전부 깔아 기기그룹을 안 탔다.
        assertFalse(QnnDeliveryReporter.isFromPlay(null))
        assertFalse(QnnDeliveryReporter.isFromPlay("com.android.shell"))
        assertFalse(QnnDeliveryReporter.isFromPlay("com.sec.android.app.samsungapps"))
    }
}
