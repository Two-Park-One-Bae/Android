package app.nursemate.pill

import app.nursemate.core.vision.OpenOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class OnDeviceBackendReporterTest {

    @Test
    fun `GPU 로 열리면 gpu · ok`() {
        val e = OnDeviceBackendReporter.event("p3_trunk.tflite", OpenOutcome.GPU, "s5e9945")
        assertEquals(
            mapOf("model" to "p3_trunk.tflite", "backend" to "gpu", "cause" to "ok", "soc_model" to "s5e9945"),
            e.params
        )
    }

    @Test
    fun `CPU 로 대체되면 cpu 와 그 까닭`() {
        assertEquals("gpu_open_failed", OnDeviceBackendReporter.event("m", OpenOutcome.GPU_OPEN_FAILED, "x").cause)
        assertEquals("cpu", OnDeviceBackendReporter.event("m", OpenOutcome.GPU_BLOCKED, "x").backend)
        assertEquals("planned_cpu", OnDeviceBackendReporter.event("m", OpenOutcome.PLANNED_CPU, "x").cause)
    }
}
