package app.nursemate.navigation

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.nursemate.core.model.PillCandidate
import app.nursemate.pill.AttributePanel
import app.nursemate.pill.DetectionPhase
import app.nursemate.pill.PillAnalyticsSession
import app.nursemate.pill.PillEdit
import app.nursemate.pill.PillRecognitionViewModel
import app.nursemate.pill.PillUiState
import app.nursemate.pill.editOf
import app.nursemate.pill.enteredValuesSummary
import app.nursemate.pill.isManualPill
import app.nursemate.pill.pillId
import app.nursemate.pill.pillIds

/**
 * 수정 화면(⑧)의 지표 배선 — 체류시간 측정과 이탈 집계.
 *
 * 화면 함수에서 빼낸 것은 길이 때문만이 아니다. **생명주기에 붙는 부수효과**라
 * 화면을 그리는 코드와 섞이면 어느 것이 화면이고 어느 것이 계측인지 읽히지 않는다.
 *
 * 카드 번호·수동 여부를 밖에서 받지 않고 [state] 에서 뽑는다 — 호출부가 계산하면
 * **0 기반으로 새는 순간 라벨이 통째로 한 칸 밀리고**, 그건 리포트를 봐도 안 드러난다.
 */
@Composable
internal fun rememberEditTracking(session: PillAnalyticsSession, state: PillUiState, pillId: String): EditTracking {
    // 지표의 pill_index 는 **1 기반**이다. 수동 추가는 아직 목록에 없어(확인 시 들어간다)
    // 뒤에 붙을 자리를 쓴다 — 0 을 보내면 라벨이 "unknown" 이 된다.
    val pillIndex = state.pillIds().indexOf(pillId).takeIf { it >= 0 }?.plus(1)
        ?: (state.pillIds().size + 1)
    val tracking = remember(pillId) {
        EditTracking(session, pillId, pillIndex, manual = pillId.isManualPill)
    }

    // 체류시간은 **화면이 실제로 보이는 구간만** 더한다. 탭 전환·앱 백그라운드·세부정보(⑩)
    // 진입이 전부 여기서 끊긴다 — 벽시계로 재면 그 시간이 통째로 얹힌다.
    LifecycleResumeEffect(pillId) {
        session.enterEdit(pillId)
        onPauseOrDispose { session.leaveEdit(pillId) }
    }

    return tracking
}

/**
 * 수정 화면 지표가 쓰는 화면-로컬 상태와 호출 묶음.
 *
 * 알약 id·순번·수동 여부를 한 번만 받아 들고 있는다 — 호출부마다 다시 넘기면 그중 하나가
 * 0 기반으로 새는 순간 라벨이 통째로 한 칸씩 밀린다.
 */
internal class EditTracking(
    private val session: PillAnalyticsSession,
    private val pillId: String,
    private val pillIndex: Int,
    private val manual: Boolean
) {

    /** 지금 펼쳐 둔 선택판. 진입 직후는 각인이다. */
    var openPanel by mutableStateOf<AttributePanel?>(AttributePanel.Imprint)

    /** 확인을 눌렀는가. 눌렀으면 이탈이 아니다. */
    private var confirmed by mutableStateOf(false)

    /** 이탈을 이미 보냈는가. 뒤로가기와 취소가 겹쳐 두 번 세는 것을 막는다. */
    private var exitReported = false

    fun attrEdited(attribute: String) = session.attrEdited(pillId, pillIndex, attribute = attribute, manual = manual)

    /** 후보 확정. [candidateIndex] 는 **1 기반**이다. */
    fun confirm(candidateIndex: Int) {
        confirmed = true
        session.confirmed(pillId = pillId, pillIndex = pillIndex, candidateIndex = candidateIndex)
    }

    fun buttonTapped(target: String) = session.buttonTapped(target = target, screen = SCREEN)

    /**
     * 확정 없이 나갔다.
     *
     * ⚠️ **호출부는 「나가는 동작」이다 — 화면이 사라지는 것이 아니다.** 처음에는 엔트리
     * 생명주기의 `ON_DESTROY` 를 봤는데, 실기기에서 **한 번도 오지 않았다**(2026-09-19).
     * pop 하면 컴포저블이 먼저 사라져 `onDispose` 가 옵서버를 떼고, 그때 엔트리는 아직
     * `CREATED` 다(로그로 확인). 그래서 취소·뒤로처럼 **사용자가 실제로 누른 자리**에서 부른다.
     *
     * 세부정보(⑩)로 들어가는 것은 이 경로를 타지 않아 이탈로 세지 않는다 — iOS 가
     * `isMovingFromParent` 로 가리는 것과 같은 구분이다.
     */
    fun reportExit(edit: PillEdit) {
        if (confirmed || exitReported) return
        exitReported = true
        session.flowExited(
            pillId = pillId,
            pillIndex = pillIndex,
            editingAttribute = openPanel?.name?.lowercase() ?: "none",
            enteredValues = edit.enteredValuesSummary(),
            manual = manual
        )
    }

    private companion object {
        const val SCREEN = "pill_edit"
    }
}

/**
 * 취소했을 때 되돌릴 진입 시점의 값.
 *
 * 속성·각인은 후보를 실시간으로 조회해야 해서 고치는 즉시 뷰모델에 들어간다 —
 * 스펙이 "선택·확인 시 갱신, 취소 시 폐기"라 되돌릴 값을 화면이 따로 붙잡아 둬야 한다.
 */
@Composable
internal fun rememberOriginalEdit(state: PillUiState, pillId: String): PillEdit =
    remember(pillId) { state.editOf(pillId) }

/** 검출 결과에서 이 알약의 자리. 수동 추가 알약은 사진에 대응 영역이 없어 null 이다. */
internal fun PillUiState.detectedIndexOf(pillId: String): Int? {
    val detected = (detection as? DetectionPhase.Success)?.result?.pills.orEmpty()
    return detected.indices.firstOrNull { pillId(it) == pillId }
}

/** 카드에 띄울 크롭. */
internal fun PillUiState.cropOf(index: Int?): Bitmap? =
    index?.let { (detection as? DetectionPhase.Success)?.result?.pills?.getOrNull(it)?.crop }

/**
 * ⑧ 확인 — 고른 후보를 확정하고, 수동 추가라면 그제야 목록에 넣는다.
 *
 * 목록 편입을 확인 시점에 두는 것은 스펙(NM-187)이다 — 진입할 때 넣으면 취소하고 나온
 * 자리에 빈 카드가 남는다.
 */
internal fun confirmEdit(
    viewModel: PillRecognitionViewModel,
    tracking: EditTracking,
    pillId: String,
    chosen: PillCandidate?,
    candidates: List<PillCandidate>
) {
    chosen?.let {
        // 후보 순번은 **1 기반**으로 넘긴다 — 리포트에서 "1번째 후보"로 읽히게.
        tracking.confirm(candidateIndex = candidates.indexOf(it) + 1)
        viewModel.corrections.selectCandidate(pillId, it)
    }
    if (pillId.isManualPill) viewModel.corrections.addManualPill(pillId)
}
