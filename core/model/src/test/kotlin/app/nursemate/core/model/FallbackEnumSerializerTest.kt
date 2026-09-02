package app.nursemate.core.model

import app.nursemate.core.model.serialization.FallbackEnumSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable(with = TestProviderSerializer::class)
private enum class TestProvider { GOOGLE, APPLE, KAKAO, UNKNOWN }

private object TestProviderSerializer : FallbackEnumSerializer<TestProvider>(
    "TestProvider",
    TestProvider.entries.toTypedArray(),
    TestProvider.UNKNOWN
)

@Serializable
private data class Holder(val provider: TestProvider, val list: List<TestProvider> = emptyList())

class FallbackEnumSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `아는 값은 그대로 매핑된다`() {
        assertEquals(
            TestProvider.KAKAO,
            json.decodeFromString<Holder>("""{"provider":"KAKAO"}""").provider
        )
    }

    /**
     * 이게 이 직렬화기의 존재 이유다. 기본 enum 처리였다면 여기서 던지고,
     * **응답 전체**가 파싱 실패로 날아간다.
     */
    @Test
    fun `모르는 값은 던지지 않고 UNKNOWN 이 된다`() {
        assertEquals(
            TestProvider.UNKNOWN,
            json.decodeFromString<Holder>("""{"provider":"NAVER"}""").provider
        )
    }

    @Test
    fun `리스트 안의 모르는 값도 나머지를 살린다`() {
        val holder = json.decodeFromString<Holder>(
            """{"provider":"GOOGLE","list":["APPLE","NAVER","KAKAO"]}"""
        )

        assertEquals(
            listOf(TestProvider.APPLE, TestProvider.UNKNOWN, TestProvider.KAKAO),
            holder.list
        )
    }

    @Test
    fun `직렬화는 enum 이름을 그대로 쓴다`() {
        // 기본값(list)은 kotlinx.serialization 이 생략하므로 provider 만 확인한다.
        assertEquals(
            """{"provider":"APPLE"}""",
            json.encodeToString(Holder(TestProvider.APPLE))
        )
    }
}
