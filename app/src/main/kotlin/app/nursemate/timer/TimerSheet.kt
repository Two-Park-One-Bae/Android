package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmV2Radius
import app.nursemate.core.designsystem.v2.NmV2Type

/**
 * 타이머 시트 틀 — 정본 V2.1.0 시트는 **흰 면 · 위 모서리 28 · 손잡이 40×4**다(design README 「평면」).
 *
 * 시트는 뜨는 면이지만 유리로 그리지 않는다. 어두운 막 위에서 유리는 탁한 회색으로 보인다(NM-591).
 * 손잡이는 기본 것(32×4 · 위아래 22)을 쓰지 않고 정본대로 그린다 — 위 10, 아래는 시트마다 다른
 * 간격([handleGap])이라 내용 첫 줄과의 거리가 정본과 같아진다.
 *
 * 다섯 시트(프리셋 · 추가/편집 · 알람 권한 · 거부 · 울림 방식)가 이 틀 하나를 쓴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimerSheet(
    onDismiss: () -> Unit,
    handleGap: Dp,
    sheetGesturesEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = sheetGesturesEnabled,
        // 프리셋 6개면 절반 높이(partial)를 넘긴다. 거기서 멈추면 마지막 항목이 시트 밖으로
        // 밀려 잘린 채 보이므로, 처음부터 콘텐츠 높이만큼 펼친다.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NmTheme.semanticColors.surface,
        shape = SheetShape,
        dragHandle = { SheetHandle(handleGap, onDismiss) },
        content = content
    )
}

/**
 * 기본 손잡이는 화면 낭독기에 「닫기」 동작을 준다. 그림만 바꾸면 그것이 사라지므로 직접 단다 —
 * 낭독기 사용자는 끌어 내릴 수 없어 이것이 시트를 닫는 길이다.
 */
@Composable
private fun SheetHandle(gap: Dp, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = gap)
            .semantics {
                contentDescription = "시트 손잡이"
                dismiss(label = "닫기") {
                    onDismiss()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).background(NmColor.Neutral.C200, HandleShape))
    }
}

private val SheetShape = RoundedCornerShape(topStart = NmV2Radius.x2l, topEnd = NmV2Radius.x2l)
private val HandleShape = RoundedCornerShape(2.dp)

/** 시트 제목 — 정본 18/700. V2 글자에 없는 크기라 시트 제목(17/700)에서 크기만 바꾼다. */
internal val TimerSheetTitle = NmV2Type.sheetTitle.copy(
    fontSize = 18.sp,
    lineHeight = 25.sp,
    fontWeight = FontWeight.Bold
)
