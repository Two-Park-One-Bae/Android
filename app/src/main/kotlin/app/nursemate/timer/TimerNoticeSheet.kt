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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmV2ButtonPrimary
import app.nursemate.core.designsystem.v2.NmV2ButtonShape
import app.nursemate.core.designsystem.v2.NmV2Type

/**
 * 안내 시트 공통 골격 — 정본 `A3 알람 권한 안내`·`A3 알람 권한 거부`·`첫 시작 — 울림 방식 선택`
 * 세 프레임이 **완전히 같은 뼈대**를 쓴다(원형 아이콘 64 → 제목 → 본문 → 내용 → 주 버튼 → 보조).
 *
 * 셋을 각각 짜면 여백·크기가 조금씩 어긋나므로 하나로 둔다.
 */
@Composable
fun TimerNoticeSheet(
    icon: Int,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    // 보조 버튼이 없는 시트도 있다 — 워치 안내처럼 **막지 않고 알리기만** 하는 경우다.
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    val colors = NmTheme.semanticColors
    TimerSheet(onDismiss = onDismiss, handleGap = 16.dp) {
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
                Text(title, style = TimerSheetTitle, color = colors.textPrimary, textAlign = TextAlign.Center)
                Text(body, style = BodyStyle, color = colors.textSecondary, textAlign = TextAlign.Center)
            }

            content?.invoke()

            NmV2ButtonPrimary(primaryLabel, onPrimary, Modifier.fillMaxWidth())

            if (secondaryLabel != null && onSecondary != null) {
                Text(
                    text = secondaryLabel,
                    style = NmV2Type.menu,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(NmV2ButtonShape)
                        .clickable(role = Role.Button, onClick = onSecondary)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// 정본 수치 — A3 · 첫 시작 시트 공통
private val IconBox = 64.dp

/** 정본 14 · 줄 높이 1.5. */
private val BodyStyle = NmV2Type.menu.copy(fontWeight = FontWeight.Normal, lineHeight = 21.sp)
