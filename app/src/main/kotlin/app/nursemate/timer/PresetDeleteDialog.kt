package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 프리셋 삭제 확인 — 정본 `타이머 / C3 프리셋 삭제 확인`.
 *
 * 되돌릴 수 없어 한 번 묻는다. 프리셋은 매일 쓰는 것이라 실수로 지우면 다시 만들어야 한다.
 */
@Composable
fun PresetDeleteDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = NmTheme.semanticColors
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .width(DialogWidth)
                .wrapContentHeight()
                .clip(DialogShape)
                .background(colors.surface, DialogShape)
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("이 프리셋을 삭제할까요?", style = TitleStyle, color = colors.textPrimary)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DialogButton(
                    modifier = Modifier.weight(1f),
                    label = "취소",
                    background = NmColor.Neutral.C100,
                    foreground = colors.textPrimary,
                    onClick = onCancel
                )
                DialogButton(
                    modifier = Modifier.weight(1f),
                    label = "삭제",
                    background = NmColor.Error.C500,
                    foreground = NmColor.Neutral.C0,
                    onClick = onConfirm
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(ButtonHeight)
            .clip(ButtonShape)
            .background(background, ButtonShape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = ButtonStyle, color = foreground)
    }
}

// 정본 수치 — `C3 프리셋 삭제 확인`
private val DialogWidth = 300.dp
private val DialogShape = RoundedCornerShape(16.dp)
private val ButtonShape = RoundedCornerShape(12.dp)
private val ButtonHeight = 48.dp

private val TitleStyle = NmTypography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
private val ButtonStyle = NmTypography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
