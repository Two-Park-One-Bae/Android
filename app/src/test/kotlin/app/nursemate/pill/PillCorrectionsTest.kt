package app.nursemate.pill

import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.model.PillConditions
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 수정 화면의 취소 · 고른 후보 — 뒤로가 취소다(spec §선택). 탭 바로 다녀와도 남는다. */
class PillCorrectionsTest {

    private fun edit(mark: Boolean?) =
        PillEdit(PillAttribute(pillId = "1"), PillConditions(), FaceInputs(front = FaceInput(hasMark = mark)))

    @Test
    fun `손대기 전에 들어왔으면 취소는 고친 값을 지운다`() {
        val state = MutableStateFlow(PillUiState())
        val corrections = PillCorrections(state)
        corrections.originalOf("1")
        corrections.updateEdit("1", edit(false))

        corrections.revert("1")

        // 지워야 모델값 · 판독값에서 다시 만든다 — 들어온 뒤 도착한 마크 판독이 살아난다.
        assertFalse("1" in state.value.edits)
    }

    @Test
    fun `고친 값이 있던 알약은 그 값으로 되돌린다`() {
        val state = MutableStateFlow(PillUiState(edits = mapOf("1" to edit(true))))
        val corrections = PillCorrections(state)
        corrections.originalOf("1")
        corrections.updateEdit("1", edit(false))

        corrections.revert("1")

        assertEquals(true, state.value.edits.getValue("1").faces.front.hasMark)
    }

    @Test
    fun `원래 값은 처음 잡은 것을 지키고 끝내면 놓는다`() {
        val state = MutableStateFlow(PillUiState())
        val corrections = PillCorrections(state)
        assertNull(corrections.originalOf("1"))
        corrections.updateEdit("1", edit(false))
        assertNull("두 번째로 물어도 처음 값이다", corrections.originalOf("1"))

        val candidate = PillCandidate("c", LicenseStatus.NORMAL, "약", "업체", "", "")
        corrections.choose("1", candidate)
        assertEquals(candidate, corrections.pendingOf("1"))

        corrections.endEdit("1")
        assertNull(corrections.pendingOf("1"))
        assertEquals(false, corrections.originalOf("1")?.faces?.front?.hasMark)
    }
}
