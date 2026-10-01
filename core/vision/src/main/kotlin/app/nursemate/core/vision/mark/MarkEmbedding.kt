package app.nursemate.core.vision.mark

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * 마크 임베딩을 계약의 전송 형식으로 옮긴다 — 정본은 계약(`domains/candidate.md` 「마크 임베딩
 * 규격」), 우리 쪽은 NM-515.
 *
 * ## 넷을 전부 맞춰야 한다
 * | | |
 * |---|---|
 * | 인코딩 | base64 |
 * | dtype | **fp16** |
 * | 바이트 순서 | **little-endian** |
 * | 평탄화 | 8 × 768 row-major — 회전 8개가 바깥 |
 *
 * [MarkReader.Result.embedding] 이 이미 그 순서로 평탄화돼 있어 여기서는 담기만 한다.
 *
 * ## 왜 이걸 계약으로 박았나
 * 어긋나면 **에러가 아니라 후보 순서로만** 드러난다. 서버가 디코딩에 성공하고 코사인도 계산해
 * 내는데 값이 조금 틀렸을 뿐이라, 앱에서는 「왜 이 약이 3등이지」로만 보인다. iOS·Android·서버
 * 셋이 같은 줄을 따라야 한다.
 *
 * ## 다시 정규화하지 않는다
 * 모델이 L2 정규화한 값이다(`torch.nn.functional.normalize`). 여기서 또 걸면 값이 달라진다.
 *
 * ## `android.util.Half` 를 쓰지 않는다
 * minSdk 26 이라 쓸 수는 있지만 프레임워크 클래스라 JVM 단위 테스트에서 스텁이 던진다
 * (이 저장소에 Robolectric 이 없다). 인코딩 규칙은 **기기 없이 검증돼야 할 것**이라
 * 순수 JVM 으로 접는다 — base64 도 `java.util` 쪽을 쓴다.
 */
object MarkEmbedding {

    /** 8 방향 × 768. 계약이 못박은 크기라 다르면 보내지 않는다. */
    const val FLOATS = MarkPreprocess.ROTATIONS * MarkReader.EMBEDDING_DIM

    /** fp16 이라 원본 12,288 B → base64 16,384 자. */
    const val BYTES = FLOATS * 2

    /**
     * @param embedding [MarkReader.Result.embedding] 그대로
     * @return base64 문자열, 또는 크기가 계약과 다르면 null
     */
    fun encode(embedding: FloatArray): String? {
        if (embedding.size != FLOATS) return null
        val buffer = ByteBuffer.allocate(BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (value in embedding) buffer.putShort(toHalf(value))
        // 줄바꿈 없는 기본 인코더. 줄이 끊기면 서버 디코더가 12,288 B 를 못 맞춘다.
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    /**
     * fp32 → fp16(IEEE 754 binary16).
     *
     * **최근접 짝수 반올림**이다 — 기준 구현인 numpy `astype(float16)` 이 그렇다. 그냥 자르면
     * 값마다 한 ulp 씩 아래로 쏠려 코사인이 미세하게 틀어진다.
     *
     * 임베딩은 L2 정규화된 값이라 실제 범위는 [-1, 1] 안이고 비정규화·오버플로 경로는 밟히지
     * 않는다. 그래도 적어 둔다 — 잘못된 입력이 조용히 0 이 되는 쪽이 더 나쁘다.
     */
    @Suppress("MagicNumber")
    private fun toHalf(value: Float): Short {
        val bits = java.lang.Float.floatToRawIntBits(value)
        val sign = (bits ushr 31) shl 15
        val rawExponent = (bits ushr 23) and 0xFF
        val mantissa = bits and 0x7FFFFF

        if (rawExponent == 0xFF) {
            // NaN 은 가수를 살려 두고(0 이 되면 무한대가 된다), 무한대는 그대로.
            return (sign or 0x7C00 or if (mantissa != 0) 0x200 else 0).toShort()
        }

        val exponent = rawExponent - 127 + 15
        return when {
            exponent >= 0x1F -> (sign or 0x7C00).toShort()

            // fp16 의 최소 비정규화값보다 작다 — 부호만 남기고 0 으로 접는다.
            exponent < -10 -> sign.toShort()

            // 비정규화 영역. 숨은 1 을 되살려 오른쪽으로 민다.
            exponent <= 0 -> {
                val shifted = mantissa or 0x800000
                val shift = 14 - exponent
                (sign + roundShift(shifted, shift)).toShort()
            }

            // 가수를 23 → 10 비트로. 반올림이 지수로 넘치는 것은 **의도한 동작**이다 —
            // 지수가 1 커지고 가수가 0 이 되는 정확히 옳은 값이라, 더하기로 두면 저절로 된다.
            else -> (sign + (exponent shl 10) + roundShift(mantissa, 13)).toShort()
        }
    }

    /** [value] 를 [shift] 비트 내리면서 최근접 짝수로 반올림한다. */
    @Suppress("MagicNumber")
    private fun roundShift(value: Int, shift: Int): Int {
        val truncated = value ushr shift
        val dropped = value and ((1 shl shift) - 1)
        val half = 1 shl (shift - 1)
        // 딱 절반이면 짝수 쪽으로 — `truncated and 1` 을 더해 「홀수일 때만 올림」을 만든다.
        return if (dropped + (truncated and 1) > half) truncated + 1 else truncated
    }
}
