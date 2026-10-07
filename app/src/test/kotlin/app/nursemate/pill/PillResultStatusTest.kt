package app.nursemate.pill

import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.model.PillConditions
import org.junit.Assert.assertEquals
import org.junit.Test

/** 한 줄 카드가 어느 상태로 그려지나 — 정본 ①·⑭ (NM-516). */
class PillResultStatusTest {

    private fun edit(error: String? = null) = PillEdit(
        attribute = PillAttribute(pillId = "p1", error = error),
        conditions = PillConditions(),
        faces = FaceInputs()
    )

    private val candidate = PillCandidate(
        pillCode = "199000001",
        licenseStatus = LicenseStatus.NORMAL,
        pillName = "타이레놀정500밀리그람",
        companyName = "한국얀센(주)",
        pillThumbnailUrl = "",
        pillImageUrl = ""
    )

    @Test
    fun `아직 안 골랐으면 선택 전이다`() {
        assertEquals(PillResultStatus.PENDING, PillResultStatus.of(edit(), selected = null))
    }

    @Test
    fun `추출에 실패한 알약은 인식 실패다`() {
        assertEquals(
            PillResultStatus.FAILED,
            PillResultStatus.of(edit(error = "EXTRACTION_FAILED"), selected = null)
        )
    }

    @Test
    fun `후보를 고르면 식별 완료다`() {
        assertEquals(PillResultStatus.IDENTIFIED, PillResultStatus.of(edit(), selected = candidate))
    }

    /**
     * 확정이 가장 세다. 추출에 실패해도 사용자가 직접 채워 고르면 **초록 카드**다 —
     * 실패를 그대로 두면 다 고른 뒤에도 빨간 카드가 남아 「아직 할 일이 있다」로 읽힌다.
     */
    @Test
    fun `추출에 실패했어도 고르면 식별 완료다`() {
        assertEquals(
            PillResultStatus.IDENTIFIED,
            PillResultStatus.of(edit(error = "EXTRACTION_FAILED"), selected = candidate)
        )
    }
}
