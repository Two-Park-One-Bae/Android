package app.nursemate.pill

import androidx.compose.runtime.Composable
import app.nursemate.core.designsystem.v2.NmV2Modal
import app.nursemate.core.model.Usage

/**
 * 한도 안내 알럿 — 정본 ②-a 「오늘 식별 횟수를 모두 사용했어요」.
 *
 * 버튼이 `확인` 하나다. 고를 것이 없는 **안내**라 취소 자리를 만들면 "안 하면 되나?" 하고 읽힌다.
 * 다이얼로그라 탭 바까지 덮는다(spec §화면 공통 — 모달은 탭 바를 덮는다). 바깥을 눌러도 확인과 같다.
 *
 * @param usage 리셋 시각을 안내하려고 받는다. 모르면(null) 시각 없이 안내만 한다.
 */
@Composable
fun PillLimitAlert(usage: Usage?, onConfirm: () -> Unit) {
    NmV2Modal(
        title = "오늘 식별 횟수를 모두 사용했어요",
        message = usage.resetGuide(),
        confirmText = "확인",
        onConfirm = onConfirm,
        onDismissRequest = onConfirm
    )
}

/**
 * 리셋 안내 문구.
 *
 * 정본은 "내일 00:00에 초기화돼요" 다. 서버가 주는 [Usage.resetAt] 은 KST 자정이라
 * 시각이 늘 00:00 이지만, **그 값을 파싱해 다시 조립하지는 않는다** — 시간대·형식 처리를
 * 들이는 값어치가 없고, 서버가 정책을 바꾸면 문구만 고치면 된다.
 * 값을 모를 때만 시각을 뺀다.
 */
private fun Usage?.resetGuide(): String = if (this == null) "잠시 후 다시 확인해 주세요" else "내일 00:00에 초기화돼요"
