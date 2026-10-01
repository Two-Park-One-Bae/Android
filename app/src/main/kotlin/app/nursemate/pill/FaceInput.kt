package app.nursemate.pill

import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillFace
import app.nursemate.core.model.PillFaceRequest

/**
 * 한 면의 각인 입력값 — 화면 전용 모델.
 *
 * ## 왜 [PillFace] 를 그대로 쓰지 않는가
 * 도메인 [PillFace] 는 "없다"만 말할 수 있고 **"아직 안 정했다"** 를 담지 못한다. 그런데 이 화면의
 * 입력은 전부 하드 필터라 둘을 구분하지 못하면 사용자가 손대지도 않은 조건으로 후보가 걸러진다.
 * 그래서 '해당 없음'([blank]) 을 따로 들고, 내보낼 때만 두 도메인 타입으로 갈라 준다.
 *
 * @param blank 이 면에는 아무것도 없다(정본 '해당 없음'). 켜면 아래 입력은 감춘다.
 * @param hasMark 마크가 **있다**. 꺼져 있는 것은 "없다"가 아니라 "조건으로 걸지 않는다"이다 —
 *                정본 라벨이 '마크 있음' 이라 체크가 존재의 주장이고, 해제는 주장의 부재다.
 *                마크가 없음을 조건으로 걸려면 면 전체를 [blank] 로 둔다.
 */
data class FaceInput(
    val blank: Boolean = false,
    val imprint: String = "",
    val dividingLine: DividingLine? = null,
    val hasMark: Boolean = false
) {
    private val untouched: Boolean
        get() = imprint.isBlank() && dividingLine == null && !hasMark

    /** 카드의 표기값 줄이 읽는 값. 아무것도 안 정했으면 null 이라 '미인식'으로 보인다. */
    fun toFace(): PillFace? = when {
        // 셋 다 비운 [PillFace] = 각인·구분선 없음 + 마크 없음. 카드가 '없음'으로 읽는다.
        blank -> PillFace()

        untouched -> null

        else -> PillFace(
            imprint = imprint.ifBlank { null },
            dividingLine = dividingLine,
            hasMark = hasMark
        )
    }

    /**
     * 후보 검색의 면 조건.
     *
     * ⚠️ [PillFaceRequest] 는 null 이 **"조건에서 빼라"** 다. 그래서 '해당 없음'은 null 이 아니라
     * 빈 문자열·NONE·false 를 **명시**해야 한다 — null 로 보내면 "각인 없는 알약"을 찾으려던
     * 조건이 통째로 사라진다.
     */
    fun toRequest(): PillFaceRequest? = when {
        blank -> PillFaceRequest(imprint = "", dividingLine = DividingLine.NONE, hasMark = false)

        untouched -> null

        else -> PillFaceRequest(
            imprint = imprint.ifBlank { null },
            dividingLine = dividingLine,
            hasMark = hasMark.takeIf { it }
        )
    }
}

/** 앞뒤 한 쌍. 앞면은 **사진에 찍힌 면** 기준이다(spec §수정·후보 선택). */
data class FaceInputs(val front: FaceInput = FaceInput(), val back: FaceInput = FaceInput()) {
    companion object {
        /**
         * 온디바이스가 읽은 값에서 시작한다.
         *
         * ⚠️ **서버는 각인·마크를 주지 않는다**(v1). 앱이 `ImprintReader`·`MarkReader` 로 읽은
         * 값이 여기 들어온다 — 아직 연결 전이라 지금은 빈 값으로 시작한다.
         */
        fun from(front: PillFace? = null, back: PillFace? = null) = FaceInputs(
            front = front.toInput(),
            back = back.toInput()
        )

        private fun PillFace?.toInput() = FaceInput(
            imprint = this?.imprint.orEmpty(),
            dividingLine = this?.dividingLine,
            hasMark = this?.hasMark == true
        )
    }
}

/** 어느 면을 만지고 있는가. 기호 바가 어느 칸에 넣을지 이 값으로 고른다. */
enum class FaceSide { Front, Back }
