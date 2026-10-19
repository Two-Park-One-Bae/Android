package app.nursemate.pill

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 메뉴 여닫기 — 바깥 탭 닫기는 화면이 [EditMenus.onDown] 으로 하고, 칩 위의 탭은 칩이 토글한다.
 * 실제 순서를 흉내 낸다: 터치 시작 → `onDown(자리)` · 손을 떼면 → 그 자리 칩의 클릭.
 */
class EditMenusTest {

    private val menus = EditMenus()
    private val shapeChip = Rect(0f, 0f, 100f, 40f)
    private val colorChip = Rect(120f, 0f, 220f, 40f)
    private val inside = Offset(50f, 20f)

    private fun tapChip(chip: Rect, click: () -> Unit) {
        menus.onDown(chip.center)
        click()
    }

    private fun openShape() {
        tapChip(shapeChip) { menus.toggle(AttributePanel.Shape) }
        menus.anchorAt(shapeChip) // 열린 칩이 그려지며 자리를 알린다
    }

    @Test
    fun `열린 칩을 다시 누르면 닫힌다 — 오래 눌러도`() {
        openShape()
        // 터치 시작은 칩 안이라 닫지 않고, 뗄 때 클릭이 토글한다. 누른 시간과 무관하다.
        tapChip(shapeChip) { menus.toggle(AttributePanel.Shape) }

        assertNull(menus.open)
    }

    @Test
    fun `열린 채로 다른 칩을 누르면 그 칩이 한 번에 열린다`() {
        openShape()
        tapChip(colorChip) { menus.toggle(AttributePanel.Color) }

        assertEquals(AttributePanel.Color, menus.open)
    }

    @Test
    fun `칩 밖을 누르면 닫히고 곧바로 같은 칩을 누르면 다시 열린다`() {
        openShape()
        menus.onDown(Offset(500f, 500f)) // 후보 목록 빈 곳
        assertNull(menus.open)

        tapChip(shapeChip) { menus.toggle(AttributePanel.Shape) }
        assertEquals(AttributePanel.Shape, menus.open)
    }

    @Test
    fun `면 메뉴에서 고른 직후 같은 칩을 누르면 다시 열린다`() {
        val mark = FaceMenuKey(FaceSide.Front, FaceMenu.Mark)
        tapChip(shapeChip) { menus.face(mark) }
        menus.anchorAt(shapeChip)

        menus.face(null) // 항목을 골라 닫힘
        tapChip(shapeChip) { menus.face(mark) }

        assertEquals(mark, menus.faceMenu)
    }

    @Test
    fun `열린 면 메뉴 칩을 다시 누르면 닫힌다`() {
        val mark = FaceMenuKey(FaceSide.Front, FaceMenu.Mark)
        tapChip(shapeChip) { menus.face(mark) }
        menus.anchorAt(shapeChip)

        tapChip(shapeChip) { menus.face(mark) }

        assertNull(menus.faceMenu)
    }

    @Test
    fun `열린 메뉴가 없으면 터치가 아무것도 바꾸지 않는다`() {
        menus.startImprint(FaceSide.Front)
        menus.onDown(inside)

        assertEquals(FaceSide.Front, menus.editingSide)
    }
}
