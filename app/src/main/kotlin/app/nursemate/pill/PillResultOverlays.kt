package app.nursemate.pill

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2Menu
import app.nursemate.core.designsystem.v2.NmV2MenuItem
import app.nursemate.core.designsystem.v2.NmV2Modal

// 식별 결과(PillResultScreen) 위에 뜨는 것 — ⋮ 메뉴 · 저장 실패 모달.

/**
 * ⋮ 메뉴 — `수정` · `삭제`. 정본 ④ 는 막을 깔지 않는다.
 *
 * **팝업(창이 따로)** 으로 띄운다. 화면 안에 그리면 탭 바를 덮지 못해 메뉴를 연 채 탭을 눌러 빠져나갈 수 있다.
 * 바깥을 누르거나 뒤로를 누르면 메뉴만 닫힌다.
 *
 * @param topEnd 메뉴 오른쪽 위가 놓일 **창 기준** 좌표 — 가로는 카드 오른쪽 끝, 세로는 ⋮ 아래. 아래로 넘치면
 *               창 안으로 끌어올린다.
 */
@Composable
internal fun PillCardMenu(topEnd: IntOffset, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val position = remember(topEnd) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ) = IntOffset(
                x = (topEnd.x - popupContentSize.width).coerceAtLeast(0),
                y = topEnd.y.coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0)
            )
        }
    }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        NmV2Menu(
            items = listOf(
                NmV2MenuItem("수정", NmSymbols.Edit, onClick = onEdit),
                NmV2MenuItem("삭제", NmSymbols.Delete, destructive = true, onClick = onDelete)
            )
        )
    }
}

/**
 * 저장 실패 — 통신 · 서버면 `나가기` / `다시 시도`, 요청이 잘못됐으면(400) `나가기` 하나(정본 ⑧ · ⑧-b).
 * 바깥을 누르면 모달만 닫고 화면에 남는다.
 */
@Composable
internal fun SaveFailedModal(
    failed: HistorySave.Failed,
    onRetry: () -> Unit,
    onLeave: () -> Unit,
    onDismissRequest: () -> Unit
) {
    if (failed.retryable) {
        NmV2Modal(
            title = SAVE_FAILED_TITLE,
            message = "네트워크 연결을 확인하고 다시 시도해 주세요",
            confirmText = "다시 시도",
            dismissText = "나가기",
            onConfirm = onRetry,
            onDismiss = onLeave,
            onDismissRequest = onDismissRequest
        )
    } else {
        NmV2Modal(
            title = SAVE_FAILED_TITLE,
            message = "이 결과는 기록에 남길 수 없어요",
            confirmText = "나가기",
            onConfirm = onLeave,
            onDismissRequest = onDismissRequest
        )
    }
}

private const val SAVE_FAILED_TITLE = "식별 결과를 저장하지 못했어요"
