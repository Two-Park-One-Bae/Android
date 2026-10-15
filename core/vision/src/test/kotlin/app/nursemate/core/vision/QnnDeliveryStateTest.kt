package app.nursemate.core.vision

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class QnnDeliveryStateTest {

    private fun libDir(vararg names: String): File =
        Files.createTempDirectory("lib").toFile().apply { names.forEach { File(this, it).writeBytes(ByteArray(0)) } }

    @Test
    fun `토대와 세대가 다 있으면 정상`() {
        val dir = libDir("libQnnHtp.so", "libQnnSystem.so", "libQnnHtpV75Skel.so", "libQnnHtpV75Stub.so")
        assertEquals(QnnDeliveryState.CORE_AND_SKEL, QnnDeliveryState.of(dir, pluginLoaded = true))
    }

    @Test
    fun `플러그인 클래스만 있고 토대 so 가 없으면 토대로 치지 않는다`() {
        val dir = libDir("libQnnHtpV75Skel.so")
        assertEquals(QnnDeliveryState.SKEL_ONLY, QnnDeliveryState.of(dir, pluginLoaded = true))
    }

    @Test
    fun `토대만 있으면 core_only`() {
        val dir = libDir("libQnnHtp.so", "libQnnSystem.so")
        assertEquals(QnnDeliveryState.CORE_ONLY, QnnDeliveryState.of(dir, pluginLoaded = true))
    }

    @Test
    fun `아무 것도 없으면 none — 그리고 비슷한 이름에 속지 않는다`() {
        val dir = libDir("libonnxruntime.so", "libQnnHtpPrepare.so", "libQnnHtpV75Stub.so")
        assertEquals(QnnDeliveryState.NONE, QnnDeliveryState.of(dir, pluginLoaded = false))
    }
}
