package app.nursemate.pill

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.model.PillCandidate

/**
 * 식별 결과 카드의 **상태 셋** — 정본 「알약 식별 (V2.1.0)」 ①~③ (NM-490 · NM-516 · NM-587).
 *
 * ## 한 줄 카드는 상태만 말한다
 * V1 에서 결과 화면은 속성·조건·후보 수를 **보여 주지 않는다.** 그건 수정 화면의 일이다.
 * 여기 남는 것은 「이 알약이 지금 어느 단계인가」 하나뿐이고, 그걸 **배경 톤과 번호 색**으로
 * 드러낸다.
 *
 * ## 사진 위 표시도 같은 색을 쓴다
 * 카드와 사진 위 테두리·번호 태그가 **한 상태를 세 군데서** 그린다. 색이 갈리면 어느 카드가
 * 어느 알약인지 눈으로 못 잇는다 — 그래서 색을 여기 한 곳에서 정하고 셋이 같이 읽는다.
 */
enum class PillResultStatus {
    /** 아직 후보를 고르지 않았다. 유리 카드에 파란 번호. */
    PENDING,

    /**
     * 서버가 이 알약을 못 읽었다(`EXTRACTION_FAILED`). 카드 면은 선택 전과 같은 유리 카드이고(정본 ②),
     * 빨간 번호와 빨간 제목 `정보를 읽지 못했어요` · 둘째 줄 `직접 입력` 으로 가른다.
     */
    FAILED,

    /** 후보를 골라 확정했다. 연초록 카드에 품목명·업체. */
    IDENTIFIED;

    companion object {
        /**
         * 무엇이 상태를 가르나.
         *
         * 확정이 가장 세다 — 추출에 실패했어도 사용자가 직접 채워 고르면 **식별 완료**다.
         *
         * ⚠️ V1 은 속성별 부분 실패가 없다. [PillAttribute.failed] 하나로 갈린다 —
         * 「색은 읽었는데 모양은 못 읽었다」가 없다.
         */
        fun of(edit: PillEdit, selected: PillCandidate?): PillResultStatus = when {
            selected != null -> IDENTIFIED
            edit.attribute.failed -> FAILED
            else -> PENDING
        }
    }
}

/**
 * 상태 하나가 쓰는 색 묶음. 카드 면은 상태별 재질이 정한다(`PillResultScreen`) — 여기는 글자 · 배지 · 사진 표시만.
 *
 * @param badge 번호 배지 배경
 * @param number 번호 글자
 * @param title 첫 줄 글자
 * @param marker 사진 위 테두리·번호 태그 바탕. 흰 글자가 얹히므로 **진한 쪽**을 쓴다
 */
data class PillStatusColors(val badge: Color, val number: Color, val title: Color, val marker: Color)

/** 정본 「알약 식별 (V2.1.0)」 ①~③ 의 토큰. */
@Composable
fun PillResultStatus.colors(): PillStatusColors {
    val semantic = NmTheme.semanticColors
    return when (this) {
        // 정본 ① 은 번호 글자를 success-700 으로 칠했지만 배지가 primary-50 이고 상태가 「선택 전」이라
        // 파란 계열로 맞춘다 — 초록은 식별 완료의 색이다.
        PillResultStatus.PENDING -> PillStatusColors(
            badge = NmColor.Primary.C50,
            number = NmColor.Primary.C600,
            title = semantic.textSecondary,
            marker = NmColor.Primary.C500
        )

        PillResultStatus.FAILED -> PillStatusColors(
            badge = NmColor.Error.C50,
            number = NmColor.Error.C700,
            title = NmColor.Error.C700,
            marker = NmColor.Error.C500
        )

        PillResultStatus.IDENTIFIED -> PillStatusColors(
            badge = NmColor.Success.C100,
            number = NmColor.Success.C700,
            title = semantic.textPrimary,
            marker = NmColor.Success.C500
        )
    }
}
