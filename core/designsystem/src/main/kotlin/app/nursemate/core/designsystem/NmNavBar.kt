package app.nursemate.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 상단 내비게이션 바 — 디자인 `Component / Nav Bar`.
 *
 * 뒤로가기(26dp `chevron-left`) · 가운데 정렬 제목(17sp/600) · 오른쪽 26dp 여백.
 * 오른쪽 여백은 제목이 **시각적으로 정중앙**에 오게 하려고 둔다(뒤로가기와 폭을 맞춘 균형추).
 *
 * @param onBack null이면 뒤로가기를 그리지 않는다(루트 화면).
 */
@Composable
fun NmNavBar(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val colors = NmTheme.semanticColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NavBarHeight)
            .background(colors.bgApp)
            .padding(horizontal = NmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NmSpacing.sm)
    ) {
        if (onBack != null) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_chevron_left),
                contentDescription = "뒤로",
                tint = colors.textPrimary,
                modifier = Modifier
                    .size(SideSlot)
                    .clickable(onClick = onBack)
            )
        } else {
            Box(modifier = Modifier.size(SideSlot))
        }

        Text(
            text = title,
            // 정본은 17·600. title(18·600)에서 크기만 내린다.
            style = NmTypography.title.copy(fontSize = 17.sp),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        Box(modifier = Modifier.size(SideSlot))
    }
}

private val NavBarHeight = 56.dp
private val SideSlot = 26.dp
