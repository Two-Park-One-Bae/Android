package app.nursemate.core.model.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 모르는 enum 값을 예외 대신 [fallback]으로 받는 직렬화기.
 *
 * ## 왜 필요한가
 * kotlinx.serialization 의 기본 enum 처리는 목록에 없는 문자열을 만나면 던진다. 서버가 enum 에
 * 값을 하나 추가하는 순간 **구버전 앱이 그 응답 전체를 파싱하지 못하고 죽는다** — 알약 목록에
 * 새 제형이 하나 섞였다고 화면 전체가 실패하는 식이다.
 *
 * 서버 enum 은 늘 UNKNOWN 자리를 두고 이 직렬화기를 쓴다(`spec/api/domains/enums.md`).
 *
 * ## 사용법
 * ```kotlin
 * @Serializable(with = AuthProviderSerializer::class)
 * enum class AuthProvider { GOOGLE, APPLE, KAKAO, UNKNOWN }
 *
 * object AuthProviderSerializer : FallbackEnumSerializer<AuthProvider>(
 *     "AuthProvider", AuthProvider.entries.toTypedArray(), AuthProvider.UNKNOWN
 * )
 * ```
 *
 * ⚠️ 직렬화(요청 전송)에서 [fallback]을 쓰면 "UNKNOWN" 이라는 문자열이 서버로 나간다.
 * 이 직렬화기는 **응답을 읽는 용도**다 — fallback 값을 요청 본문에 담지 않는다.
 */
abstract class FallbackEnumSerializer<T : Enum<T>>(serialName: String, values: Array<T>, private val fallback: T) :
    KSerializer<T> {

    private val byName: Map<String, T> = values.associateBy { it.name }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): T = byName[decoder.decodeString()] ?: fallback
}
