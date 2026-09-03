package app.nursemate.detail

import app.nursemate.core.model.Cell
import app.nursemate.core.model.Row
import app.nursemate.core.model.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 병합 셀 격자 복원.
 *
 * 계약이 HTML 과 같아 **병합이 먹은 자리는 `cells` 에 아예 없다.** 그대로 그리면 아래 행이
 * 왼쪽으로 당겨져 열이 통째로 어긋난다 — 용량 표에서 열이 밀리면 다른 약의 용량을 읽게 된다.
 * 눈으로는 잡기 어려운 자리라 여기서 못 박아 둔다.
 */
class LicenseDocTableTest {

    private fun cell(text: String, colspan: Int = 1, rowspan: Int = 1) =
        Cell(content = listOf(Span(text)), colspan = colspan, rowspan = rowspan)

    /**
     * 열 번호로 값을 읽는다.
     *
     * ⚠️ 슬롯 인덱스가 곧 열 번호는 아니다 — colspan 2 짜리 칸은 슬롯 **하나**로 두 열을
     * 덮는다. colspan 을 더해 가며 찾아야 실제 화면에서 보이는 자리와 같아진다.
     */
    private fun List<List<GridSlot>>.textAt(row: Int, column: Int): String? {
        var at = 0
        this[row].forEach { slot ->
            if (column < at + slot.colspan) return slot.cell?.content?.firstOrNull()?.text
            at += slot.colspan
        }
        return null
    }

    @Test
    fun `병합이 없으면 준 그대로다`() {
        val grid = listOf(Row(listOf(cell("A"), cell("B")))).gridRows()

        assertEquals(2, grid[0].size)
        assertEquals("A", grid.textAt(0, 0))
        assertEquals("B", grid.textAt(0, 1))
    }

    @Test
    fun `colspan 은 칸을 넓힌다`() {
        val grid = listOf(Row(listOf(cell("머리", colspan = 3)))).gridRows()

        assertEquals(1, grid[0].size)
        assertEquals(3, grid[0][0].colspan)
    }

    @Test
    fun `rowspan 이 먹은 자리는 다음 행에서 빈 칸으로 남는다`() {
        val grid = listOf(
            Row(listOf(cell("성인", rowspan = 2), cell("250mg"))),
            // 두 번째 행에는 첫 칸이 없다 — 위에서 세로로 병합했기 때문이다.
            Row(listOf(cell("500mg")))
        ).gridRows()

        assertEquals("성인", grid.textAt(0, 0))
        // 빈 칸을 채워 넣지 않으면 500mg 이 '구분' 열로 올라간다.
        assertNull(grid.textAt(1, 0))
        assertEquals("500mg", grid.textAt(1, 1))
    }

    @Test
    fun `rowspan 은 지정한 행 수만큼만 먹는다`() {
        val grid = listOf(
            Row(listOf(cell("성인", rowspan = 2), cell("a"))),
            Row(listOf(cell("b"))),
            Row(listOf(cell("소아"), cell("c")))
        ).gridRows()

        assertEquals("소아", grid.textAt(2, 0))
        assertEquals("c", grid.textAt(2, 1))
    }

    @Test
    fun `가로 세로 병합이 겹쳐도 자리가 맞는다`() {
        val grid = listOf(
            Row(listOf(cell("묶음", colspan = 2, rowspan = 2), cell("비고"))),
            Row(listOf(cell("메모"))),
            Row(listOf(cell("x"), cell("y"), cell("z")))
        ).gridRows()

        assertEquals("비고", grid.textAt(0, 2))
        // 첫 두 칸이 위 병합에 먹혔으므로 '메모'는 세 번째 열이다.
        assertNull(grid.textAt(1, 0))
        assertNull(grid.textAt(1, 1))
        assertEquals("메모", grid.textAt(1, 2))
        assertEquals("x", grid.textAt(2, 0))
    }
}
