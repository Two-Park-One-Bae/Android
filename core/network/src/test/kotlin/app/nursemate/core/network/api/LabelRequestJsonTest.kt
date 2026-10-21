package app.nursemate.core.network.api

import app.nursemate.core.model.Image
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 학습 라벨 요청이 계약(spec `openapi.yaml` 0.31 · `api/domains/label.md`) 모양으로 나가는지 — NM-571.
 * 운영 `Json` 설정(`NetworkModule.provideJson`)과 같게 둔다.
 */
class LabelRequestJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun encode(value: PillAttributesRequest): JsonObject =
        json.encodeToJsonElement(PillAttributesRequest.serializer(), value).jsonObject

    @Test
    fun `속성 추출에 imageId · 원본 크기 · segModel 과 알약마다 box · score 가 실린다`() {
        val body = encode(
            PillAttributesRequest(
                items = listOf(
                    PillAttributeItem("0", Image("image/png", "AA"), box = listOf(388, 212, 204, 196), score = 0.9f)
                ),
                imageId = "0192a6f0-0000-7000-8000-000000000000",
                imageWidth = 2048,
                imageHeight = 2048,
                segModel = "20260610-rfdetr-seg-small"
            )
        )

        assertEquals("0192a6f0-0000-7000-8000-000000000000", body["imageId"].toString().trim('"'))
        assertEquals("2048", body["imageWidth"].toString())
        val item = body["items"]!!.toString()
        assertEquals(true, item.contains("\"box\":[388,212,204,196]"))
        assertEquals(true, item.contains("\"score\":0.9"))
        assertEquals(true, item.contains("\"pillId\":\"0\""))
    }

    @Test
    fun `라벨 필드가 없으면 키 자체가 빠진다 — 구버전과 같은 본문`() {
        val body = encode(PillAttributesRequest(items = listOf(PillAttributeItem("0", Image("image/png", "AA")))))

        assertEquals(setOf("items"), body.keys)
        assertEquals(
            setOf("pillId", "croppedImage"),
            body["items"]!!.toString().let { json.parseToJsonElement(it) }
                .let { (it as kotlinx.serialization.json.JsonArray)[0].jsonObject.keys }
        )
    }

    @Test
    fun `모델 알약 라벨은 manual 을 빼고 model 과 userInput 을 싣는다`() {
        val query = PillCandidatesRequest(attributeToken = "t")
        val body = json.encodeToJsonElement(
            PillLabelRequest.serializer(),
            PillLabelRequest("P1", userInput = query, model = query)
        ).jsonObject

        assertFalse("manual" in body)
        assertEquals(setOf("pillCode", "userInput", "model"), body.keys)
        // 후보 조회 요청 그대로 — 색 조건 없음도 빈 목록으로 담긴다(PillCandidatesRequest.colors).
        assertEquals(true, body["userInput"].toString().contains("\"colors\":[]"))
    }

    @Test
    fun `수동 추가 알약 라벨은 manual true 이고 model 이 없다`() {
        val body = json.encodeToJsonElement(
            PillLabelRequest.serializer(),
            PillLabelRequest("P1", userInput = PillCandidatesRequest(), manual = true)
        ).jsonObject

        assertEquals(setOf("pillCode", "userInput", "manual"), body.keys)
        assertEquals("true", body["manual"].toString())
    }

    @Test
    fun `지운 번호가 없으면 빈 목록을 보낸다`() {
        val body = json.encodeToJsonElement(
            PillSegmentationSaveRequest.serializer(),
            PillSegmentationSaveRequest(emptyList())
        ).jsonObject

        assertEquals("[]", body["deletedIndexes"].toString())
    }
}
