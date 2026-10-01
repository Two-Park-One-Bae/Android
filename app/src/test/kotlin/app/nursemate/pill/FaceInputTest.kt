package app.nursemate.pill

import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.PillFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 각인 입력 → 도메인 변환.
 *
 * 요청과 응답에서 null 의 뜻이 **정반대**라(요청 = 조건 제외 / 응답 = 없음) 눈으로 읽어서는
 * 틀린 것을 못 잡는다. '해당 없음'과 '아직 안 정했다'가 갈리는 자리만 골라 못 박아 둔다.
 */
class FaceInputTest {

    @Test
    fun `손대지 않은 면은 조건에서 빠진다`() {
        assertNull(FaceInput().toRequest())
        assertNull(FaceInput().toFace())
    }

    @Test
    fun `해당 없음은 조건에서 빼지 않고 '없음'을 명시한다`() {
        val request = FaceInput(blank = true).toRequest()

        // null 로 보내면 "각인 없는 알약"을 찾으려던 조건이 통째로 사라진다.
        assertEquals("", request?.imprint)
        assertEquals(DividingLine.NONE, request?.dividingLine)
        assertEquals(false, request?.hasMark)
    }

    @Test
    fun `해당 없음은 입력이 남아 있어도 그 입력을 조건으로 쓰지 않는다`() {
        val input = FaceInput(blank = true, imprint = "MK", dividingLine = DividingLine.PLUS, hasMark = true)

        // 체크를 껐다 켜는 사이 글자가 남는데, 없다고 말해 놓고 그 글자로 찾으면 안 된다.
        assertEquals("", input.toRequest()?.imprint)
        assertEquals(DividingLine.NONE, input.toRequest()?.dividingLine)
        assertEquals(PillFace(), input.toFace())
    }

    @Test
    fun `마크 해제는 '없음'이 아니라 '조건 아님'이다`() {
        val request = FaceInput(imprint = "MK", hasMark = false).toRequest()

        // 라벨이 '마크 있음' 이라 체크가 존재의 주장이고 해제는 주장의 부재다.
        // false 로 보내면 마크 있는 알약이 후보에서 통째로 빠진다.
        assertNull(request?.hasMark)
        assertEquals("MK", request?.imprint)
    }

    @Test
    fun `각인만 비면 각인 조건만 빠진다`() {
        val request = FaceInput(dividingLine = DividingLine.MINUS).toRequest()

        assertNull(request?.imprint)
        assertEquals(DividingLine.MINUS, request?.dividingLine)
    }

    /**
     * 온디바이스가 읽은 값에서 시작해도 그대로 되돌아온다.
     *
     * ⚠️ V1 은 **서버가 각인·마크를 주지 않는다** — 앱이 읽은 값이 들어온다(NM-485 · NM-515).
     */
    @Test
    fun `읽은 값에서 시작하면 그대로 되돌아온다`() {
        val front = PillFace(imprint = "MK", dividingLine = DividingLine.PLUS, hasMark = true)

        val faces = FaceInputs.from(front = front, back = null)

        assertEquals(front, faces.front.toFace())
        assertNull(faces.back.toFace())
    }
}
