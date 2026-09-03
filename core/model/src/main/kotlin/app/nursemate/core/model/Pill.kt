package app.nursemate.core.model

import app.nursemate.core.model.serialization.FallbackEnumSerializer
import kotlinx.serialization.Serializable

/**
 * 알약 색. **투명은 여기 없다** — 색조가 아니라 투명도 축이라
 * [PillAttribute.isTransparent] 로 분리한다(정본 `openapi.yaml` 주석).
 */
@Serializable(with = PillColorSerializer::class)
enum class PillColor {
    WHITE,
    YELLOW,
    ORANGE,
    PINK,
    RED,
    BROWN,
    LIGHT_GREEN,
    GREEN,
    TEAL,
    BLUE,
    NAVY,
    MAGENTA,
    PURPLE,
    GRAY,
    BLACK,
    COLORLESS,
    UNKNOWN
}

object PillColorSerializer : FallbackEnumSerializer<PillColor>(
    "PillColor",
    PillColor.entries.toTypedArray(),
    PillColor.UNKNOWN
)

@Serializable(with = PillShapeSerializer::class)
enum class PillShape {
    ROUND,
    OVAL,
    OBLONG,
    SEMICIRCLE,
    TRIANGLE,
    SQUARE,
    DIAMOND,
    PENTAGON,
    HEXAGON,
    OCTAGON,
    OTHER,
    UNKNOWN
}

object PillShapeSerializer : FallbackEnumSerializer<PillShape>(
    "PillShape",
    PillShape.entries.toTypedArray(),
    PillShape.UNKNOWN
)

@Serializable(with = PillFormulationSerializer::class)
enum class PillFormulation { TABLET, HARD_CAPSULE, SOFT_CAPSULE, OTHER, UNKNOWN }

object PillFormulationSerializer : FallbackEnumSerializer<PillFormulation>(
    "PillFormulation",
    PillFormulation.entries.toTypedArray(),
    PillFormulation.UNKNOWN
)

/**
 * 구분선.
 *
 * ⚠️ **응답에서는 [NONE] 이 오지 않는다** — 없으면 필드 자체가 null 이다.
 * [NONE] 은 후보 검색에서 "분할선 없음"을 조건으로 걸 때만 쓴다(정본 주석).
 */
@Serializable(with = DividingLineSerializer::class)
enum class DividingLine { NONE, PLUS, MINUS, UNKNOWN }

object DividingLineSerializer : FallbackEnumSerializer<DividingLine>(
    "DividingLine",
    DividingLine.entries.toTypedArray(),
    DividingLine.UNKNOWN
)

/** 한 면의 각인계열. MVP 에서는 서버가 추출하지 않아 **항상 null** 로 온다(수동 입력). */
@Serializable
data class PillFace(
    /** 각인. 없으면 null. 원천이 면당 단일 문자열이라 값도 하나다. */
    val imprint: String? = null,
    val dividingLine: DividingLine? = null,
    /** 마크(로고) 유무. MVP 는 종류를 가리지 않고 유무만 본다. */
    val hasMark: Boolean = false
)

/**
 * 서버가 크롭에서 뽑아낸 속성 — `POST /api/v0/pill-attributes` 응답 항목.
 *
 * **부분 실패가 정상 경로다.** 못 뽑은 속성은 null 로 오고 화면은 그 자리만 '미인식'으로
 * 두면 된다. 아예 실패한 알약은 [error] 가 채워져 오는데(`EXTRACTION_FAILED`),
 * 그때도 HTTP 는 200 이고 **나머지 알약은 정상**이다(spec §개별 추출 실패).
 */
@Serializable
data class PillAttribute(
    /** 요청에 실어 보낸 세션 로컬 식별자. 이 값으로 검출 결과와 짝을 맞춘다. */
    val pillId: String,
    /** 1~N색(다색 가능). 추출 실패면 null — 사용자가 고른다. */
    val colors: List<PillColor>? = null,
    val isTransparent: Boolean = false,
    val shape: PillShape? = null,
    val formulation: PillFormulation? = null,
    /** MVP 는 각인을 서버가 뽑지 않는다 — 항상 null 이다. */
    val front: PillFace? = null,
    val back: PillFace? = null,
    /** 이 알약만 실패했을 때의 코드. 성공이면 null. */
    val error: String? = null
) {
    /** 이 알약은 아예 못 읽었는가. 카드 전체를 '정보 인식 실패'로 표시하는 기준이다. */
    val failed: Boolean get() = error != null
}

/**
 * 일일 식별 사용량.
 *
 * ⚠️ **[limit] 을 앱에 하드코딩하지 않는다.** 서버 설정값이라 바뀔 수 있고,
 * 카운트·리셋·판정은 전부 서버 소유다(spec §식별 횟수 제한). 앱은 받은 값을 보여줄 뿐이다.
 */
@Serializable
data class Usage(
    val limit: Int,
    val remaining: Int,
    /** 다음 리셋 시각(KST 자정). 한도 소진 안내에 쓴다. */
    val resetAt: String
) {
    val exhausted: Boolean get() = remaining <= 0
}

/** 전송용 이미지. `data` 는 base64 다 — 크롭은 알파를 살려야 하므로 PNG 로 인코딩한다. */
@Serializable
data class Image(val mimeType: String, val data: String)
