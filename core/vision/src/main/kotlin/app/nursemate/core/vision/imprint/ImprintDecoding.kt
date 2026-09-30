package app.nursemate.core.vision.imprint

import kotlin.math.exp

/**
 * CTC 출력 → 답. **ML 레포 `infer.py` 와 같은 답이 나와야 한다**(NM-485 DoD).
 *
 * 기준은 `models/imprint/20260907-crnn-ep60-s1/` 의 `infer.py` 와 `code/train.py` 다.
 * 여기 있는 것은 전부 순수 함수라 전처리(OpenCV 영역)와 분리해 단위 테스트로 고정할 수 있다.
 *
 * ## 답을 고르는 순서
 * 1. TTA 후보 144개(회전 24 × 배율 6)를 각각 CTC 그리디로 푼다 — [decodeConfident]
 * 2. 후보마다 [bestPair] 점수를 매긴다 — 인접 두 글자 확률 중 **낮은 쪽**
 * 3. 점수가 가장 높은 후보를 고른다
 * 4. 그 후보의 점수가 [ADOPT_THRESHOLD] 미만이면 **답을 내지 않는다**(null)
 * 5. 넘으면 그 후보의 글자 중 확률 [SURE_THRESHOLD] 이상인 것만 이어 낸다 — [sureChars]
 *
 * ⚠️ **고르는 기준은 `best_pair` 이고, 답은 확신 글자다.** 둘을 섞으면 안 된다.
 * 2026-09-26 개정 전에는 `best_pair` 두 글자 자체를 답으로 냈다. 지금은 고르기만 하고
 * 답은 따로 뽑는다 — 두 글자로 고정하면 셋을 확신해도 하나를 버리고, 하나만 확실할 때는
 * 확신 없는 글자를 억지로 끼워 넣는다.
 */
internal object ImprintDecoding {

    /** 인덱스 0 은 CTC blank 라 여기 없다. 카탈로그 각인 글자의 97.7% 를 덮는다. */
    const val CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /** 확신 글자 임계. 낮추면 틀린 글자가 섞여 정답이 후보에서 빠지고, 올리면 좁혀지지 않는다. */
    const val SURE_THRESHOLD = 0.95f

    /**
     * 채택 임계 — 감사셋 기준 **정밀도 90% 지점**.
     *
     * 고른 후보의 `best_pair` 확신도가 이 값 미만이면 확신 글자를 내지 않는다. 흐린 사진에서
     * 우연히 0.95 를 넘은 한두 글자로 정답을 잘라내지 않기 위해서다.
     */
    const val ADOPT_THRESHOLD = 0.949f

    /** 한 글자와 그 글자의 확률. */
    data class Char(val text: kotlin.Char, val probability: Float)

    /**
     * CTC 그리디 디코드 + 글자별 확률.
     *
     * @param logits 한 후보의 로짓 [T][C] — 시간축 먼저, C = blank 1 + [CHARSET] 36
     *
     * 파이썬 쪽은 `log_softmax` 를 거친 값을 받아 `exp` 로 되돌리는데, 여기서는 로짓에서
     * 바로 softmax 를 계산한다 — 같은 값이고 한 단계가 준다.
     */
    fun decodeConfident(logits: Array<FloatArray>): List<Char> {
        val out = ArrayList<Char>()
        var previous = 0
        for (step in logits) {
            var best = 0
            for (i in step.indices) if (step[i] > step[best]) best = i
            // blank 도 아니고 직전 글자와 같지도 않을 때만 글자가 확정된다 — CTC 규칙이다.
            if (best != 0 && best != previous) {
                out += Char(CHARSET[best - 1], softmaxAt(step, best))
            }
            previous = best
        }
        return out
    }

    /**
     * **확신하는 연속 두 글자**의 점수 — 인접 두 글자 확률 중 낮은 쪽의 최댓값.
     *
     * 「확실한 두 글자」를 모델에게 말로 묻는 대신(Gemini 에서 23.3% 빗나갔다) 인접 두 글자의
     * 확률 중 낮은 쪽이 가장 높은 창을 고른다. 그 값이 곧 그 후보의 신뢰도이고, 임계 하나로
     * 정밀도와 커버리지를 맞바꿀 수 있다.
     *
     * @return 두 글자와 점수. 글자가 둘 미만이면 `"" to 0f`
     */
    fun bestPair(chars: List<Char>): Pair<String, Float> {
        var best = ""
        var score = 0f
        for (i in 0 until chars.size - 1) {
            val s = minOf(chars[i].probability, chars[i + 1].probability)
            if (s > score) {
                best = "${chars[i].text}${chars[i + 1].text}"
                score = s
            }
        }
        return best to score
    }

    /**
     * 확률이 [threshold] 이상인 글자만 순서대로 이어 낸다.
     *
     * ⚠️ **없으면 null 이다. 빈 문자열을 돌려주지 않는다.** 계약에서 `imprint: ""` 는
     * 「각인이 없는 알약만」이라는 하드 조건이고 `null` 은 「조건 제외」다. 못 읽은 것을 빈
     * 문자열로 보내면 각인이 있는 알약이 전부 탈락해 정답을 잃는다.
     *
     * 사이 글자가 빠질 수 있다 — 서버가 부분 수열로 매칭하는 이유가 이것이다(AX 로 ALX3 를 찾는다).
     */
    fun sureChars(chars: List<Char>, threshold: Float = SURE_THRESHOLD): String? =
        chars.filter { it.probability >= threshold }
            .joinToString("") { it.text.toString() }
            .ifEmpty { null }

    /**
     * 후보 하나를 답으로 접는다 — 4·5단계.
     *
     * @return 확신 글자, 또는 채택 임계에 못 미치거나 확신 글자가 없으면 null
     */
    fun adopt(chars: List<Char>, pairScore: Float, threshold: Float = SURE_THRESHOLD): String? =
        if (pairScore >= ADOPT_THRESHOLD) sureChars(chars, threshold) else null

    /** 한 시간 단계의 softmax 값 하나. 최댓값을 빼 지수 폭주를 막는다. */
    private fun softmaxAt(logits: FloatArray, index: Int): Float {
        var max = logits[0]
        for (v in logits) if (v > max) max = v
        var sum = 0.0
        for (v in logits) sum += exp((v - max).toDouble())
        return (exp((logits[index] - max).toDouble()) / sum).toFloat()
    }
}
