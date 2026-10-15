package app.nursemate.pill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2Menu
import app.nursemate.core.designsystem.v2.NmV2MenuItem
import app.nursemate.core.designsystem.v2.NmV2MenuWidth
import app.nursemate.core.designsystem.v2.NmV2Modal

// 식별 결과(PillResultScreen) 위에 뜨는 것 — ⋮ 메뉴 · 저장 실패 모달.

/** ⋮ 메뉴 — `수정` · `삭제`. 바깥을 누르면 닫는다(정본 ④ 는 막을 깔지 않는다). */
@Composable
internal fun PillCardMenu(topEnd: IntOffset, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val menuWidth = with(LocalDensity.current) { NmV2MenuWidth.roundToPx() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = onDismiss
            )
    ) {
        NmV2Menu(
            items = listOf(
                NmV2MenuItem("수정", NmSymbols.Edit, onClick = onEdit),
                NmV2MenuItem("삭제", NmSymbols.Delete, destructive = true, onClick = onDelete)
            ),
            modifier = Modifier.offset { IntOffset(topEnd.x - menuWidth, topEnd.y) }
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
