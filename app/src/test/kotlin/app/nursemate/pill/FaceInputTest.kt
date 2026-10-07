package app.nursemate.pill

import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.ImprintSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 면 조건 3단 → 요청 변환 — NM-516 · NM-517.
 *
 * 「전체」와 「없음」이 정반대라(전체 = 조건 제외 / 없음 = 그게 없는 알약만) 눈으로 읽어서는
 * 틀린 것을 못 잡는다. 둘이 갈리는 자리만 골라 못 박아 둔다.
 */
class FaceInputTest {

    @Test
    fun `손대지 않은 면은 조건에서 빠진다`() {
        assertNull(FaceInput().toRequest())
        assertEquals(false, FaceInput().hasCondition)
    }

    /** 「없음」은 null 이 아니라 값이다. null 로 보내면 조건이 통째로 사라진다. */
    @Test
    fun `없음은 조건에서 빼지 않고 명시한다`() {
        val request = FaceInput(
            imprint = "",
            imprintSource = ImprintSource.USER,
            dividingLine = DividingLine.NONE,
            hasMark = false
        ).toRequest()

        assertEquals("", request?.imprint)
        assertEquals(DividingLine.NONE, request?.dividingLine)
        assertEquals(false, request?.hasMark)
    }

    /** 「전체」는 그 항만 빠진다 — 옆 칸을 끌고 가지 않는다. */
    @Test
    fun `전체인 항만 빠진다`() {
        val request = FaceInput(dividingLine = DividingLine.MINUS).toRequest()

        assertNull("각인은 전체다", request?.imprint)
        assertNull("마크도 전체다", request?.hasMark)
        assertEquals(DividingLine.MINUS, request?.dividingLine)
    }

    /**
     * 각인이 있으면 출처가 **필수**다 — 빠지면 서버가 400 `INVALID_REQUEST` 를 준다.
     * 서버가 `MODEL` 과 `USER` 를 다르게 매칭해 틀리면 조용히 다른 후보가 나온다.
     */
    @Test
    fun `각인에는 출처가 따라붙는다`() {
        val model = FaceInput(imprint = "AX", imprintSource = ImprintSource.MODEL).toRequest()
        assertEquals(ImprintSource.MODEL, model?.imprintSource)

        val user = FaceInput().typed("ALX3").toRequest()
        assertEquals(ImprintSource.USER, user?.imprintSource)
        assertEquals("ALX3", user?.imprint)
    }

    /** 각인이 「전체」면 출처도 안 보낸다 — 값 없는 출처는 뜻이 없다. */
    @Test
    fun `각인이 전체면 출처도 안 보낸다`() {
        val request = FaceInput(imprintSource = ImprintSource.MODEL, hasMark = true).toRequest()
        assertNull(request?.imprint)
        assertNull(request?.imprintSource)
    }

    /** 값이 같아도 사용자가 입력했으면 `USER` 다 — 계약이 「초기 상태와 명시적 원복만 모델값」이다. */
    @Test
    fun `같은 값을 다시 쳐도 사용자값이다`() {
        val model = FaceInput(imprint = "AX", imprintSource = ImprintSource.MODEL)
        assertEquals(ImprintSource.USER, model.typed("AX").imprintSource)
    }

    /**
     * 임베딩은 **조건이 아니다** — 사진에서 나온 정렬 재료라, 사용자가 아무 조건도 안 걸어도
     * 면에 실려 나간다. 빼면 서버가 마크 유사도로 줄 세울 재료를 잃는다.
     */
    @Test
    fun `조건이 없어도 임베딩은 나간다`() {
        val input = FaceInput(markEmbedding = "ZXhhbXBsZQ==")
        assertEquals(false, input.hasCondition)
        assertEquals("ZXhhbXBsZQ==", input.toRequest()?.markEmbedding)
    }

    /** 읽은 값은 앞면에만 들어간다 — 뒷면은 사진이 없어 「전체」로 남는다. */
    @Test
    fun `읽은 값은 앞면에만 들어간다`() {
        val faces = FaceReading(
            imprint = "AX",
            species = 27,
            hasMark = true,
            markEmbedding = "ZXhhbXBsZQ=="
        ).toInputs()

        assertEquals("AX", faces.front.imprint)
        assertEquals(ImprintSource.MODEL, faces.front.imprintSource)
        assertEquals(true, faces.front.hasMark)
        assertEquals(27, faces.front.species)
        assertEquals(FaceInput(), faces.back)
    }

    /**
     * 모델이 못 읽은 면은 **전부 전체**다.
     *
     * 빈 문자열이나 `false` 로 떨어지면 「각인 없는 알약만」·「마크 없는 알약만」이 하드 조건으로
     * 나가 정답 약이 통째로 빠진다.
     */
    @Test
    fun `못 읽은 면은 아무 조건도 안 건다`() {
        val faces = FaceReading(markEmbedding = "ZXhhbXBsZQ==").toInputs()

        assertNull(faces.front.imprint)
        assertNull(faces.front.hasMark)
        assertEquals(false, faces.front.hasCondition)
        assertTrue("임베딩만 실려 나간다", faces.front.toRequest()?.markEmbedding != null)
    }
}
