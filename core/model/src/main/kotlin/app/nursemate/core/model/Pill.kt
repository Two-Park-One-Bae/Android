package app.nursemate.core.model

import app.nursemate.core.model.serialization.FallbackEnumSerializer
import kotlinx.serialization.Serializable

/**
 * 알약 색.
 *
 * **투명은 여기 없다.** v0 은 `isTransparent` 라는 별도 축으로 뒀는데 V1 에서 그 축을
 * 없앴다(NM-487) — 무색은 [COLORLESS] 하나로 다룬다.
 *
 * 이 열거형은 **사용자가 고르는 조건**이다([PillConditions.colors]). 모델이 뽑은 색은
 * 열거형이 아니라 표시값 hex 로 온다([PillAttribute.colorHexes]) — 검색에 쓰지 않으므로
 * 되돌려 매핑하지 않는다.
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
 * 서버가 크롭에서 뽑아낸 속성 — `POST /api/v1/pill-attributes` 응답 항목.
 *
 * ## v0 과 무엇이 다른가 (NM-516 · NM-521)
 * v0 은 Gemini 로 색·모양·제형을 뽑고 각인계열은 늘 null 이었다. v1 은 색을 openCV 로,
 * 모양·제형을 자체 분류 모델로 뽑고 **각인·마크는 앱이 온디바이스로** 읽는다.
 *
 * | | v0 | v1 |
 * |---|---|---|
 * | 색 | `colors` (열거형) | **[colorHexes]** (표시값) |
 * | 투명 | `isTransparent` | **없앴다** (NM-487) |
 * | 각인·마크 | `front`·`back` (늘 null) | **없앴다** — 앱이 읽는다 |
 * | 정렬 | 서버가 색 일치로 | **[attributeToken]** 을 되돌려주면 서버가 로지스틱으로 |
 *
 * ## ⚠️ **부분 실패가 없다**
 * v0 은 속성마다 null 이 올 수 있었는데 v1 은 아니다 — [error] 가 null 이면
 * `attributeToken`·`colorHexes`·`shape`·`formulation` 이 **모두** 채워지고, [error] 가 있으면
 * 넷 다 null 이다. 화면의 속성별 「미인식」 표시가 사라진 이유다.
 *
 * 알약 하나가 실패해도 HTTP 는 200 이고 **나머지 알약은 정상**이다.
 *
 * ## 모델값과 사용자값은 하는 일이 다르다
 * 여기 담긴 색·모양·제형은 **후보를 거르지 않는다** — 정렬에만 쓰인다(후보 조회에 조건으로
 * 보내지 않는다). 거르는 것은 사용자가 고른 값이다. 화면이 칸 색으로 그 차이를 드러낸다
 * (회색 = 추정값 · 호박색 = 확실한 값).
 */
@Serializable
data class PillAttribute(
    /** 요청에 실어 보낸 세션 로컬 식별자. 이 값으로 검출 결과와 짝을 맞춘다. */
    val pillId: String,
    /**
     * 속성 토큰 — 서버가 인코딩한 **불투명 문자열**이다.
     *
     * 색(Lab)과 모양·제형의 클래스별 확률을 담고 있고, 후보 조회에 그대로 되돌려주면 서버가
     * 정렬(로지스틱)에 쓴다. **앱은 해석하지 않는다.**
     *
     * ⚠️ **한 식별 흐름 안에서만 쓴다.** 토큰에는 버전이 있어서 오래되면 후보 조회가 400
     * `INVALID_ATTRIBUTE_TOKEN` 을 준다 — 앱 재실행 뒤까지 들고 있지 않는다.
     */
    val attributeToken: String? = null,
    /**
     * 모델이 뽑은 색의 **표시값**(sRGB hex, 다색이면 여러 개).
     *
     * 검색에 쓰지 않는다 — 보여 주고 되돌리기 위해 보관한다. 순서가 의미를 갖는다(여러 색을
     * 조각 원으로 그릴 때 이 순서다).
     */
    val colorHexes: List<String>? = null,
    /** 대표 모양. `SEMICIRCLE` 은 오지 않는다(`OTHER` 로 온다). 표시·되돌리기용이다. */
    val shape: PillShape? = null,
    /** 대표 제형. `OTHER` 는 오지 않는다. 표시·되돌리기용이다. */
    val formulation: PillFormulation? = null,
    /** 이 알약만 실패했을 때의 코드(`EXTRACTION_FAILED`). 성공이면 null. */
    val error: String? = null
) {
    /** 이 알약은 아예 못 읽었는가. 카드를 '인식 실패'로 표시하는 기준이다. */
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

/** 허가 상태. `REVOKED` 는 취하·취소·유효기간만료·폐업을 묶은 값이다(spec §LicenseStatus). */
@Serializable(with = LicenseStatusSerializer::class)
enum class LicenseStatus { NORMAL, REVOKED, UNKNOWN }

object LicenseStatusSerializer : FallbackEnumSerializer<LicenseStatus>(
    "LicenseStatus",
    LicenseStatus.entries.toTypedArray(),
    LicenseStatus.UNKNOWN
)

/**
 * 후보 검색의 **면 조건** — `POST /api/v1/pill-candidates` 요청 전용.
 *
 * ⚠️ **응답 [PillFace] 와 null 의미가 정반대다.** 응답에서 null 은 "그 알약에 없다" 지만,
 * 요청에서 null 은 **"조건에서 빼라"** 다. 없음을 조건으로 걸려면 값을 명시해야 한다:
 * - 각인 없음 → `imprint = ""` (null 이 아니다)
 * - 분할선 없음 → `dividingLine = NONE`
 * - 마크 없음 → `hasMark = false`
 *
 * 둘을 뒤섞으면 "각인 없는 알약"을 찾으려다 조건이 통째로 빠져 엉뚱한 후보가 나온다.
 */
@Serializable
data class PillFaceRequest(
    val imprint: String? = null,
    /**
     * [imprint] 의 출처 — 있으면 **필수**다 (NM-517).
     *
     * 서버가 둘을 다르게 매칭한다. `MODEL` 은 OCR 확신 글자라 다른 글자가 더 있을 수 있다는
     * 전제로, `USER` 는 사용자가 일부만 적었을 수 있다는 전제로 맞춘다 — 틀리면 예외가 아니라
     * **조용히 다른 후보**가 나온다.
     *
     * **면 단위다.** 한 글자라도 고치면 그 면 전체가 `USER` 이고, 값이 같아도 사용자가
     * 입력했으면 `USER` 다. 초기 상태와 명시적 원복만 `MODEL` 이다.
     */
    val imprintSource: ImprintSource? = null,
    val dividingLine: DividingLine? = null,
    /**
     * 마크 유무 — 하드 필터.
     *
     * ⚠️ **모델은 `false` 를 보내지 않는다.** 유무 점수가 임계를 넘으면 `true`, 아니면 `null`
     * (모르겠다)이다. `false` 는 사용자가 「마크 없음」을 직접 골랐을 때만이다 —
     * 놓친 면에 `false` 가 나가면 정답 약이 통째로 빠진다(규칙은 `MarkPresence`).
     */
    val hasMark: Boolean? = null,
    /** 마크 임베딩 8×768 fp16 을 base64 로. 자르지 않고 **줄만 세운다**. */
    val markEmbedding: String? = null,
    /** [markEmbedding] 을 뽑은 모델 버전. 서버가 카탈로그 임베딩과 판을 맞춘다. */
    val markEmbeddingModel: String? = null
) {
    /** 보낼 것이 하나도 없는가. 전부 null 이면 요청에서 면 자체를 뺀다. */
    fun isEmpty(): Boolean = imprint == null && dividingLine == null && hasMark == null && markEmbedding == null
}

/**
 * 후보 알약 하나.
 *
 * @param pillCode 품목기준코드. 후보 선택을 확정하는 키다.
 * @param licenseStatus [LicenseStatus.REVOKED] 면 '허가 종료' 배지를 달고 뒤로 밀린다.
 *                      **선택은 막지 않는다** — 지참약이 허가 종료 품목일 수 있어서
 *                      허가상태는 판단 보조 정보지 차단 조건이 아니다(spec §수정·후보 선택).
 * @param pillThumbnailUrl 서버가 pillCode 로 **항상** 조립해 준다. 다만 낱알 이미지가 없는
 *                         품목은 CDN 이 404 를 주므로 로드 실패 시 플레이스홀더로 처리한다.
 */
@Serializable
data class PillCandidate(
    val pillCode: String,
    val licenseStatus: LicenseStatus,
    val pillName: String,
    val companyName: String,
    val pillThumbnailUrl: String,
    val pillImageUrl: String
)

/**
 * 후보 조회 결과 — 커서 페이지네이션을 대체한다 (NM-488 · NM-489).
 *
 * ## 순서를 응답이 고정한다
 * 서버가 점수로 정렬한 **`ids` 전체**(최대 200)와 **앞 20개 상세**가 한 번에 온다. 첫 화면을
 * 왕복 한 번으로 그리기 위해서다. 21번째부터는 `POST /api/v1/pill-candidates/items` 로
 * ID 조회하고, **앱이 [ids] 순서대로 다시 배치한다** — 응답 순서는 보장되지 않는다.
 *
 * 점수는 노출되지 않는다.
 *
 * @param truncated 하드 필터를 통과한 후보가 200개를 넘어 뒤가 잘렸는가. 목록 끝에
 *                  「찾는 약이 없다면 조건을 더 입력해 주세요」를 붙이는 기준이다
 */
@Serializable
data class PillCandidateResult(
    val ids: List<String> = emptyList(),
    val candidates: List<PillCandidate> = emptyList(),
    val truncated: Boolean = false
)

/**
 * 후보 카드 일괄 조회 결과 (NM-489).
 *
 * @param missing 요청했지만 없는 pillCode(데이터 갱신으로 사라진 품목 등).
 *                앱은 이 ID 를 목록에서 **뺀다** — 로딩 중으로 남기지 않는다
 */
@Serializable
data class PillCandidateItems(val items: List<PillCandidate> = emptyList(), val missing: List<String> = emptyList())
