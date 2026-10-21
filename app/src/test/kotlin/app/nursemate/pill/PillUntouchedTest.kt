package app.nursemate.pill

import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.model.PillConditions
import app.nursemate.core.model.PillShape
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 스토어 리뷰 묶음 1 의 「수정 없이 골랐다」— 후보를 **고른 순간**의 속성 칸으로 본다(NM-561). */
class PillUntouchedTest {

    private val candidate = PillCandidate("c", LicenseStatus.NORMAL, "약", "업체", "", "")
    private val model = PillEdit(PillAttribute(pillId = "0"), PillConditions(), FaceInputs())

    @Test
    fun `손대지 않은 알약은 수정 없음이다`() {
        assertTrue(PillUiState().isUntouched("0"))
    }

    @Test
    fun `고친 칸이 있으면 수정이다 · 같은 값으로 되돌렸으면 수정 없음이다`() {
        val changed = model.copy(conditions = PillConditions(shape = PillShape.ROUND))
        assertFalse(PillUiState(edits = mapOf("0" to changed)).isUntouched("0"))
        assertTrue(PillUiState(edits = mapOf("0" to model)).isUntouched("0"))
    }

    @Test
    fun `수동 추가 알약은 늘 수정이다`() {
        assertFalse(PillUiState().isUntouched(manualPillId(0)))
    }

    @Test
    fun `고른 순간에 판정한다 — 고른 뒤 칸을 바꿔도 뒤집히지 않는다`() {
        val state = MutableStateFlow(PillUiState())
        PillCorrections(state).selectCandidate("0", candidate)

        // 고른 뒤 수정 화면에서 칸을 바꾸고 확인 · 취소 없이 탭으로 빠져나갔다
        state.value =
            state.value.copy(edits = mapOf("0" to model.copy(conditions = PillConditions(shape = PillShape.ROUND))))

        assertEquals(true, state.value.pickedUntouched["0"])
    }
}
