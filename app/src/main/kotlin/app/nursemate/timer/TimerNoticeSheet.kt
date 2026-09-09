package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 안내 시트 공통 골격 — 정본 `A3 알람 권한 안내`·`A3 알람 권한 거부`·`첫 시작 — 울림 방식 선택`
 * 세 프레임이 **완전히 같은 뼈대**를 쓴다(원형 아이콘 64 → 제목 → 본문 → 내용 → 주 버튼 → 보조).
 *
 * 셋을 각각 짜면 여백·크기가 조금씩 어긋나므로 하나로 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerNoticeSheet(
    icon: Int,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    val colors = NmTheme.semanticColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(IconBox)
                    .clip(CircleShape)
                    .background(iconBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(30.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = TitleStyle, color = colors.textPrimary, textAlign = TextAlign.Center)
                Text(body, style = BodyStyle, color = colors.textSecondary, textAlign = TextAlign.Center)
            }

            content?.invoke()

            Text(
                text = primaryLabel,
                style = PrimaryStyle,
                color = NmColor.Neutral.C0,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ButtonShape)
                    .background(NmColor.Primary.C500, ButtonShape)
                    .clickable(onClick = onPrimary)
                    .padding(vertical = 15.dp)
            )

            Text(
                text = secondaryLabel,
                style = SecondaryStyle,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(ButtonShape)
                    .clickable(onClick = onSecondary)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// 정본 수치 — A3 · 첫 시작 시트 공통
private val IconBox = 64.dp
private val ButtonShape = RoundedCornerShape(12.dp)

private val TitleStyle = NmTypography.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
private val BodyStyle = NmTypography.body.copy(fontSize = 14.sp, lineHeight = 21.sp)
private val PrimaryStyle = NmTypography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
private val SecondaryStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
