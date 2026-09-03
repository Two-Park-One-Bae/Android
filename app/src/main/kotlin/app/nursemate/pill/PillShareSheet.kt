package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 결과 공유 방식 고르기.
 *
 * ## 정본이 없다
 * 스펙(NM-136)은 MVP 를 "텍스트 복사"까지만 잡았고 디자인에도 이 시트가 없다. iOS 가
 * 간호사 인터뷰를 반영해 **텍스트 / PDF** 를 갈라 냈고(ShareActionSheetVC), 같은 결과를
 * 두 플랫폼이 다르게 주지 않도록 따라간다. 생김새는 우리 토큰으로 다시 지었다.
 *
 * ## 텍스트가 먼저다
 * 인터뷰에서 나온 이유가 "보안 때문에 외부 파일이 아예 안 열리는 병원이 많다"였다.
 * 그래서 폰에서 바로 붙여넣을 수 있는 텍스트를 위에 두고, PDF 는 문서로 남겨야 할 때다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PillShareSheet(onCopyText: () -> Unit, onSavePdf: () -> Unit, onDismiss: () -> Unit) {
    val colors = NmTheme.semanticColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "공유하기",
                style = SheetTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            ShareOption(
                icon = R.drawable.nm_ic_copy,
                title = "텍스트 복사",
                subtitle = "바로 붙여넣기 좋아요",
                onClick = onCopyText
            )
            ShareOption(
                icon = R.drawable.nm_ic_file_down,
                title = "PDF로 저장",
                subtitle = "문서 파일로 저장·공유",
                onClick = onSavePdf
            )
        }
    }
}

@Composable
private fun ShareOption(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bgApp)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(NmColor.Primary.C50),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = NmColor.Primary.C500,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = OptionTitle, color = colors.textPrimary)
            Text(text = subtitle, style = OptionSubtitle, color = colors.textTertiary)
        }
    }
}

private val SheetTitle = NmTypography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)
private val OptionTitle = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val OptionSubtitle = NmTypography.caption
