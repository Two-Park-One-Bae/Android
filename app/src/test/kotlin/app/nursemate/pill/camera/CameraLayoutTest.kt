package app.nursemate.pill.camera

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nursemate.pill.zoomLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLayoutTest {

    private fun near(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 0.5f)

    @Test
    fun `S24 는 기본 카메라 실측과 같다`() {
        // 1080×2340px · 2.625 → 411.4×891.4dp, 내비 바 126px = 48dp
        val l = CameraLayout.of(width = 411.4.dp, height = 891.4.dp, navBottom = 48.dp, cutoutTop = 0.dp)
        assertTrue(l.roomy)
        near(31.dp, l.iconsTop)
        near(136.dp, l.previewTop)
        near(0.dp, l.previewLeft)
        near(411.4.dp, l.previewSide)
    }

    @Test
    fun `16 대 9 폰은 미리보기에 얹고 정사각이 아이콘 줄과 셔터 줄 사이에 든다`() {
        val l = CameraLayout.of(width = 360.dp, height = 640.dp, navBottom = 48.dp, cutoutTop = 24.dp)
        assertFalse(l.roomy)
        val shutterTop = 640.dp - 48.dp - CameraLayout.SHUTTER_CENTER - CameraLayout.SHUTTER_HEIGHT / 2
        assertTrue(l.previewTop >= l.iconsTop + CameraLayout.ICONS_HEIGHT)
        assertTrue(l.previewBottom <= shutterTop)
        assertTrue(l.previewSide > 300.dp)
    }

    @Test
    fun `태블릿 가로는 정사각이 높이에 맞춰 줄고 가운데 온다`() {
        val l = CameraLayout.of(width = 1280.dp, height = 800.dp, navBottom = 48.dp, cutoutTop = 0.dp)
        assertFalse(l.roomy)
        assertTrue(l.previewSide < 800.dp)
        near((1280.dp - l.previewSide) / 2, l.previewLeft)
    }

    @Test
    fun `카메라 구멍이 아이콘 줄보다 길면 아이콘 줄이 그 아래로 내려간다`() {
        val l = CameraLayout.of(width = 411.dp, height = 891.dp, navBottom = 48.dp, cutoutTop = 44.dp)
        near(44.dp, l.iconsTop)
    }

    @Test
    fun `배율 표기는 기본 카메라와 같다`() {
        assertEquals(".6", zoomLabel(0.6f))
        assertEquals("1", zoomLabel(1f))
        assertEquals("1.4", zoomLabel(1.4f))
        assertEquals("3", zoomLabel(3f))
    }
}
