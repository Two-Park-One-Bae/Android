package app.nursemate.pill

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2Radius
import app.nursemate.core.designsystem.v2.NmV2Type

/**
 * 공유 방식 선택 — V2.1.0 「공유 — 방식 선택」(식별 기록 ⑧ · 식별 결과 같은 시트).
 *
 * 시트는 **흰 면 · 위 모서리 28** 이다 — 어두운 막 위에서 유리는 탁한 회색으로 보인다(NM-591).
 * `텍스트 복사` 가 먼저다 — 외부 파일이 열리지 않는 병원이 많다(spec §완료 · 공유).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PillShareSheetV2(onCopyText: () -> Unit, onSavePdf: () -> Unit, onDismiss: () -> Unit) {
    val colors = NmTheme.semanticColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = NmV2Radius.x2l, topEnd = NmV2Radius.x2l)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("공유", style = NmV2Type.sheetTitle, color = colors.textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareRow(NmSymbols.ContentCopy, "텍스트 복사", onCopyText)
                ShareRow(NmSymbols.Description, "PDF로 저장", onSavePdf)
            }
        }
    }
}

@Composable
private fun ShareRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(NmRadius.md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NmColor.Neutral.C50)
            .border(1.dp, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = NmColor.Primary.C500, modifier = Modifier.size(20.dp))
        Text(label, style = NmV2Type.textButton, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Icon(painterResource(NmSymbols.ChevronRight), null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
    }
}
