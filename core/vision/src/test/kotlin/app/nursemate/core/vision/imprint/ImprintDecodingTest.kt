package app.nursemate.core.vision.imprint

import app.nursemate.core.vision.imprint.ImprintDecoding.Char
import kotlin.math.ln
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/**
 * 각인 디코딩 — `infer.py` 의 `decode_conf` · `best_pair` · `sure_chars` 와 같은 규칙인지.
 *
 * 전처리(OpenCV 영역)와 떼어 놓은 층이라 기기 없이 고정할 수 있다. 실제 크롭 대조는 따로 한다 —
 * 이 테스트가 보는 것은 **규칙**이지 모델이 아니다.
 */
class ImprintDecodingTest {

    /** 확률 p 를 argmax 로 만드는 로짓 한 줄. 나머지 클래스는 0 으로 균등하게 남는다. */
    private fun step(index: Int, probability: Float): FloatArray {
        val classes = ImprintDecoding.CHARSET.length + 1
        // softmax = e^x / (e^x + (n-1)) 이 p 가 되도록 x 를 잡는다.
        val x = ln(probability * (classes - 1) / (1 - probability))
        return FloatArray(classes).also { it[index] = x.toFloat() }
    }

    private fun indexOf(c: kotlin.Char) = ImprintDecoding.CHARSET.indexOf(c) + 1

    @Test
    fun `blank 과 반복은 CTC 규칙대로 접힌다`() {
        // A A blank A → "AA". 붙어 있는 같은 글자는 하나로 접히고, blank 를 사이에 두면 갈린다.
        val logits = arrayOf(
            step(indexOf('A'), 0.99f),
            step(indexOf('A'), 0.99f),
            step(0, 0.99f),
            step(indexOf('A'), 0.99f)
        )
        assertEquals("AA", ImprintDecoding.decodeConfident(logits).joinToString("") { it.text.toString() })
    }

    @Test
    fun `글자마다 확률이 함께 나온다`() {
        val chars = ImprintDecoding.decodeConfident(
            arrayOf(step(indexOf('X'), 0.80f), step(0, 0.99f), step(indexOf('7'), 0.96f))
        )
        assertEquals(listOf('X', '7'), chars.map { it.text })
        assertEquals(0.80f, chars[0].probability, 0.01f)
        assertEquals(0.96f, chars[1].probability, 0.01f)
    }

    @Test
    fun `best_pair 는 인접 두 글자 중 낮은 쪽이 가장 높은 창을 고른다`() {
        // A(0.99) L(0.40) X(0.97) 3(0.96) → 창별 낮은 쪽 = 0.40 · 0.40 · 0.96 → "X3"
        val chars = listOf(Char('A', 0.99f), Char('L', 0.40f), Char('X', 0.97f), Char('3', 0.96f))
        val (pair, score) = ImprintDecoding.bestPair(chars)
        assertEquals("X3", pair)
        assertEquals(0.96f, score, 1e-6f)
    }

    @Test
    fun `글자가 둘 미만이면 best_pair 가 없다`() {
        assertEquals("" to 0f, ImprintDecoding.bestPair(listOf(Char('A', 0.99f))))
        assertEquals("" to 0f, ImprintDecoding.bestPair(emptyList()))
    }

    @Test
    fun `확신 글자는 사이가 빠진 채로 순서만 지켜 나온다`() {
        // ALX3 에서 L 만 확신 못 하면 답은 AX3 다 — 서버가 부분 수열로 매칭하는 이유다.
        val chars = listOf(Char('A', 0.99f), Char('L', 0.40f), Char('X', 0.97f), Char('3', 0.96f))
        assertEquals("AX3", ImprintDecoding.sureChars(chars))
    }

    @Test
    fun `확신 글자가 하나도 없으면 빈 문자열이 아니라 null 이다`() {
        // ⚠️ 빈 문자열은 「각인 없는 알약만」이라는 하드 조건이다. 못 읽은 것을 그렇게 보내면
        //    각인이 있는 정답이 전부 탈락한다.
        assertNull(ImprintDecoding.sureChars(listOf(Char('A', 0.90f), Char('B', 0.80f))))
        assertNull(ImprintDecoding.sureChars(emptyList()))
    }

    @Test
    fun `채택 임계에 못 미치면 확신 글자가 있어도 내지 않는다`() {
        // 흐린 사진에서 우연히 0.95 를 넘은 글자로 정답을 잘라내지 않기 위한 관문이다.
        val chars = listOf(Char('A', 0.99f), Char('B', 0.99f))
        assertEquals("AB", ImprintDecoding.adopt(chars, pairScore = 0.95f))
        assertNull(ImprintDecoding.adopt(chars, pairScore = 0.948f))
    }

    @Test
    fun `채택 임계 경계값은 통과다`() {
        val chars = listOf(Char('A', 0.99f), Char('B', 0.99f))
        assertEquals("AB", ImprintDecoding.adopt(chars, pairScore = ImprintDecoding.ADOPT_THRESHOLD))
    }
}
