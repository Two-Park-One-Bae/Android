package app.nursemate.core.model

import app.nursemate.core.model.serialization.FallbackEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 확정한 알약의 허가사항 — `GET /api/v0/pill-details/{pillCode}` 응답.
 *
 * 식약처 허가정보를 서버가 미리 적재해 둔 것이라 조회 시 외부 호출이 없다.
 * 원문은 HTML 표·첨자가 섞인 XML 인데 **서버가 구조화해 내려주고 앱은 그리기만 한다**
 * (spec `api/domains/pill-detail.md`).
 *
 * @param pillImageUrl 낱알 **원본**. 값은 항상 있지만 이미지가 없는 품목은 CDN 이 404 를
 *                     주므로 로드 실패 시 플레이스홀더로 받는다(NM-347).
 * @param documents type 당 최대 하나이고 없을 수도 있다. **순서에 기대지 말고 type 으로 찾는다.**
 */
@Serializable
data class PillDetail(
    val pillCode: String,
    val name: String,
    val companyName: String,
    val pillImageUrl: String,
    val classification: PillClassification,
    val appearance: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val storageMethod: String? = null,
    val validTerm: String? = null,
    val packUnit: String? = null,
    val documents: List<LicenseDoc> = emptyList()
) {
    fun document(type: DocType): LicenseDoc? = documents.firstOrNull { it.type == type }
}

/** 성분명 · 분량 · 단위. 분량은 소수·범위 등 표기가 다양해 문자열이다. */
@Serializable
data class Ingredient(val name: String, val amount: String, val unit: String)

/** 전문의약품 · 일반의약품. */
@Serializable(with = PillClassificationSerializer::class)
enum class PillClassification { ETC, OTC, UNKNOWN }

object PillClassificationSerializer : FallbackEnumSerializer<PillClassification>(
    "PillClassification",
    PillClassification.entries.toTypedArray(),
    PillClassification.UNKNOWN
)

/** 허가문서 종류. 화면의 문서 탭 셋과 1:1 이다. */
@Serializable(with = DocTypeSerializer::class)
enum class DocType { EFFECT, DOSAGE, CAUTION, UNKNOWN }

object DocTypeSerializer : FallbackEnumSerializer<DocType>(
    "DocType",
    DocType.entries.toTypedArray(),
    DocType.UNKNOWN
)

/** 허가문서 하나 — 블록을 **원문 순서 그대로** 담는다. */
@Serializable
data class LicenseDoc(val type: DocType, val blocks: List<Block> = emptyList())

/**
 * 문서를 이루는 블록.
 *
 * ⚠️ **모르는 `type` 은 [Block.Unsupported] 로 받아 화면에서 건너뛴다.** 계약이
 * "Block.type 값은 추가될 수 있다"고 못박고 있어, 모르는 값에 디코딩을 실패시키면
 * 서버가 블록 하나를 늘린 날 세부정보 화면 전체가 죽는다.
 */
@Serializable(with = BlockSerializer::class)
sealed interface Block {
    /** 항목 제목. 사용상 주의사항에서는 아코디언 한 칸의 머리가 된다. */
    @Serializable
    @SerialName("HEADING")
    data class Heading(val content: List<Span> = emptyList()) : Block

    @Serializable
    @SerialName("PARAGRAPH")
    data class Paragraph(val content: List<Span> = emptyList()) : Block

    /**
     * 표.
     *
     * 병합 시맨틱은 HTML 과 같다 — 병합된 셀은 왼쪽 위에 한 번만 나오고 [Cell.colspan] ·
     * [Cell.rowspan] 이 먹은 자리는 `cells` 에 아예 없다. 실데이터는 22열까지 온다.
     */
    @Serializable
    @SerialName("TABLE")
    data class Table(val caption: List<Span>? = null, val rows: List<Row> = emptyList()) : Block

    /** 원문에 박혀 있던 이미지(용량 계산식·도해). MVP 는 data URI 로 온다. */
    @Serializable
    @SerialName("IMAGE")
    data class Image(val src: String) : Block

    /** 앱이 모르는 블록. 그리지 않고 넘어간다. */
    @Serializable
    @SerialName("UNSUPPORTED")
    data object Unsupported : Block
}

object BlockSerializer : JsonContentPolymorphicSerializer<Block>(Block::class) {
    override fun selectDeserializer(element: JsonElement) =
        when (element.jsonObject["type"]?.jsonPrimitive?.contentOrNull) {
            "HEADING" -> Block.Heading.serializer()
            "PARAGRAPH" -> Block.Paragraph.serializer()
            "TABLE" -> Block.Table.serializer()
            "IMAGE" -> Block.Image.serializer()
            else -> Block.Unsupported.serializer()
        }
}

@Serializable
data class Row(val cells: List<Cell> = emptyList())

@Serializable
data class Cell(
    val content: List<Span> = emptyList(),
    val colspan: Int = 1,
    val rowspan: Int = 1,
    val header: Boolean = false
)

/**
 * 글자 조각.
 *
 * `text` 는 순수 문자열이다 — HTML 태그·엔티티가 남지 않는다. 다만 **개행은 들어올 수 있다**
 * (셀 안 여러 문단이 개행으로 합쳐진다).
 *
 * 첨자는 용량·화학식·각주에서 임상적 의미가 있어 보존한다. 모르는 스타일은 일반 글자로 그린다.
 */
@Serializable
data class Span(val text: String, val style: SpanStyle? = null)

@Serializable(with = SpanStyleSerializer::class)
enum class SpanStyle { SUP, SUB, UNKNOWN }

object SpanStyleSerializer : FallbackEnumSerializer<SpanStyle>(
    "SpanStyle",
    SpanStyle.entries.toTypedArray(),
    SpanStyle.UNKNOWN
)

/** 스팬을 이어 붙인 평문. 첨자 구분이 필요 없는 자리(표 셀 폭 계산 등)에서 쓴다. */
fun List<Span>.plainText(): String = joinToString("") { it.text }
