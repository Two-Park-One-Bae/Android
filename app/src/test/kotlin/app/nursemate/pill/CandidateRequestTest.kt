package app.nursemate.pill

import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.ImprintSource
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillConditions
import app.nursemate.core.model.PillShape
import app.nursemate.core.vision.mark.MarkReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 조건 → 후보 조회 요청, 그리고 **「입력 전」을 가르는 기준** — NM-516 · NM-517 · NM-529.
 *
 * 틀려도 예외가 아니라 **엉뚱한 목록**으로만 드러나는 자리라 규칙을 박아 둔다.
 */
class CandidateRequestTest {

    private val embedding = "ZXhhbXBsZQ=="

    private fun photographed(front: FaceInput) = FaceInputs(front = front)

    /**
     * 가장 중요한 한 줄 — **임베딩은 입력이 아니다.**
     *
     * 추출 실패 알약도 사진은 있어 임베딩이 딸려 온다. 입력으로 치면 사용자가 아무것도
     * 넣지 않았는데 후보 200개가 쏟아진다(NM-529 「입력 전」).
     */
    @Test
    fun `마크 임베딩만 있으면 조회하지 않는다`() {
        val conditions = PillConditions()
        val faces = photographed(FaceInput(markEmbedding = embedding, species = 7))

        assertFalse(conditions.toRequest(faces).hasCondition)
    }

    @Test
    fun `각인_구분선_마크 중 하나라도 있으면 조회한다`() {
        val conditions = PillConditions()
        val cases = listOf(
            FaceInput(imprint = "AX", imprintSource = ImprintSource.MODEL, markEmbedding = embedding),
            FaceInput(dividingLine = DividingLine.PLUS, markEmbedding = embedding),
            FaceInput(hasMark = true, markEmbedding = embedding),
            // 「없음」도 하드 조건이다 — 각인 없는 알약만 남긴다.
            FaceInput(imprint = "", imprintSource = ImprintSource.USER)
        )
        for (face in cases) {
            assertTrue("$face 는 조회해야 한다", conditions.toRequest(photographed(face)).hasCondition)
        }
    }

    /** 토큰만 있어도 부를 값어치가 있다 — 조건 없이도 모델값 정렬로 후보가 나온다. */
    @Test
    fun `속성 토큰만 있어도 조회한다`() {
        assertTrue(PillConditions(attributeToken = "tok").toRequest(FaceInputs()).hasCondition)
    }

    /** 수동 추가 직후 — 사진도 토큰도 조건도 없다. */
    @Test
    fun `아무것도 없으면 조회하지 않는다`() {
        assertFalse(PillConditions().toRequest(FaceInputs()).hasCondition)
    }

    @Test
    fun `사용자가 고른 색_모양은 조회를 부른다`() {
        assertTrue(PillConditions(colors = listOf(PillColor.WHITE)).toRequest(FaceInputs()).hasCondition)
        assertTrue(PillConditions(shape = PillShape.ROUND).toRequest(FaceInputs()).hasCondition)
    }

    /**
     * `markEmbeddingModel` 은 **요청 최상위**이고 임베딩이 있을 때만 실린다 —
     * 빠지면 400 `INVALID_REQUEST` 다(NM-533).
     */
    @Test
    fun `임베딩이 있으면 모델 버전을 함께 보낸다`() {
        val withEmbedding = PillConditions(attributeToken = "tok")
            .toRequest(photographed(FaceInput(markEmbedding = embedding)))
        assertEquals(MarkReader.MODEL_VERSION, withEmbedding.markEmbeddingModel)

        val without = PillConditions(attributeToken = "tok").toRequest(FaceInputs())
        assertNull("임베딩이 없으면 버전도 안 보낸다", without.markEmbeddingModel)
    }

    /** 뒷면에만 임베딩이 있어도 보낸다 — 「어느 면이든」이 계약 문구다. */
    @Test
    fun `뒷면 임베딩만 있어도 모델 버전을 보낸다`() {
        val faces = FaceInputs(back = FaceInput(markEmbedding = embedding))
        assertEquals(MarkReader.MODEL_VERSION, PillConditions().toRequest(faces).markEmbeddingModel)
    }

    /**
     * ⚠️ **모델이 추정한 모양·제형은 요청에 실리지 않는다.**
     *
     * [PillConditions] 에는 사용자가 고른 값만 담기므로 여기서 샐 길이 없어야 한다 —
     * v0 은 둘을 한 객체에 담아 모델 추정값이 하드 필터로 나갔다(NM-516).
     */
    @Test
    fun `조건이 비어 있으면 모양_제형도 비어 나간다`() {
        val request = PillConditions(attributeToken = "tok").toRequest(FaceInputs())

        assertNull(request.shape)
        assertNull(request.formulation)
        assertTrue(request.colors.isEmpty())
    }
}
