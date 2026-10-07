package app.nursemate.pill

import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.ImprintSource
import app.nursemate.core.model.PillFaceRequest

/**
 * 한 면의 조건 — 화면 전용 모델.
 *
 * ## 세 칸이 모두 **3단**이다 (NM-516)
 * 각인·구분선·마크가 각각 「전체 · 없음 · 값」을 갖는다. 정본이 세 칸을 같은 드롭다운으로
 * 그리는 이유이기도 하다.
 *
 * | | 전체 | 없음 | 값 |
 * |---|---|---|---|
 * | [imprint] | `null` | `""` | `"ALX3"` |
 * | [dividingLine] | `null` | [DividingLine.NONE] | `PLUS`·`MINUS` |
 * | [hasMark] | `null` | `false` | `true` |
 *
 * **전체와 없음은 정반대다.** 전체는 「이 항으로 자르지 마라」이고, 없음은 「이게 없는 알약만」
 * 이라는 하드 조건이다. 계약의 `PillFaceRequest` 가 그렇게 읽는다. 각인 칸을 비우고 확인하는
 * 것은 **없음**이고, 조건을 푸는 것은 드롭다운의 **전체**다 — 헷갈리게 만들면 사용자가
 * 무심코 정답을 지운다.
 *
 * ## 모델이 「없음」을 말하지 않는다
 * 셋 다 모델은 값 아니면 `null` 만 낸다. 못 읽은 것을 「없음」으로 보내면 정답 약이 통째로
 * 빠진다(`MarkPresence` · `ImprintReader`). 「없음」은 사용자가 직접 고를 때만이다.
 *
 * @param imprintSource [imprint] 의 출처. 값이 있으면 **필수**다 — 서버가 `MODEL` 과 `USER` 를
 *   다르게 매칭한다(NM-517). 한 글자라도 고치면 면 전체가 `USER` 다
 * @param markEmbedding 모델이 이 면에서 뽑은 마크 임베딩(base64 fp16 8×768). **조건이 아니다** —
 *   사용자가 무엇을 고르든 사진에서 나온 값 그대로 가고, 서버가 정렬에만 쓴다
 * @param species 마크 종 번호. 사람에게 보여 줄 식약처 그림을 고르는 데 쓴다. 0 이면 못 읽었다
 */
data class FaceInput(
    val imprint: String? = null,
    val imprintSource: ImprintSource? = null,
    val dividingLine: DividingLine? = null,
    val hasMark: Boolean? = null,
    val markEmbedding: String? = null,
    val species: Int = 0
) {
    /** 후보를 **자르는** 조건이 하나라도 있는가. 임베딩은 정렬 재료라 세지 않는다. */
    val hasCondition: Boolean
        get() = imprint != null || dividingLine != null || hasMark != null

    /**
     * 사용자가 각인을 손댔다고 표시한다.
     *
     * 값이 같아도 사용자가 입력했으면 `USER` 다 — 계약이 「초기 상태와 명시적 원복만 모델값」
     * 으로 정했다. 되돌리기만 [ImprintSource.MODEL] 로 되돌린다.
     */
    fun typed(text: String?): FaceInput = copy(
        imprint = text,
        imprintSource = text?.let { ImprintSource.USER }
    )

    /**
     * 후보 검색의 면 조건.
     *
     * 보낼 것이 하나도 없으면 null 이다 — 면 자체를 요청에서 뺀다.
     */
    fun toRequest(): PillFaceRequest? = PillFaceRequest(
        imprint = imprint,
        // 각인이 있으면 출처가 **필수**다. 빠지면 서버가 400 INVALID_REQUEST 를 준다.
        imprintSource = imprintSource?.takeIf { imprint != null },
        dividingLine = dividingLine,
        hasMark = hasMark,
        markEmbedding = markEmbedding
    ).takeIf { !it.isEmpty() }
}

/** 앞뒤 한 쌍. 앞면은 **사진에 찍힌 면** 기준이다(spec §수정·후보 선택). */
data class FaceInputs(val front: FaceInput = FaceInput(), val back: FaceInput = FaceInput())

/**
 * 온디바이스가 읽은 값을 조건 칸의 **초기값**으로 옮긴다.
 *
 * 뒷면은 사진이 없어 읽을 것이 없다 — 「전체」로 남는다.
 */
fun FaceReading.toInputs(): FaceInputs = FaceInputs(
    front = FaceInput(
        imprint = imprint,
        // 모델이 읽은 값이라 MODEL 이다. 사용자가 손대면 그때 USER 가 된다.
        imprintSource = imprint?.let { ImprintSource.MODEL },
        hasMark = hasMark,
        markEmbedding = markEmbedding,
        species = species
    )
)

/** 어느 면을 만지고 있는가. 기호 바가 어느 칸에 넣을지 이 값으로 고른다. */
enum class FaceSide { Front, Back }
