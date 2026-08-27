package app.nursemate.home

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 하단 탭바 — 흰 알약형 바 위에 4개 탭. 선택된 탭만 배경 알약과 primary 색을 갖는다.
 */
@Composable
fun NmBottomBar(selected: NmTab, onSelect: (NmTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NmSpacing.md, vertical = NmSpacing.sm)
            .background(colors.surface, RoundedCornerShape(percent = 50))
            .padding(NmSpacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NmTab.entries.forEach { tab ->
            TabItem(tab = tab, selected = tab == selected, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun TabItem(tab: NmTab, selected: Boolean, onClick: () -> Unit) {
    val colors = NmTheme.semanticColors
    val tint = if (selected) NmColor.Primary.C500 else colors.textSecondary
    Box(
        modifier = Modifier
            .background(
                color = if (selected) NmColor.Primary.C50 else colors.surface,
                shape = RoundedCornerShape(NmRadius.xl)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = NmSpacing.md, vertical = NmSpacing.sm)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NmSpacing.xs)
        ) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Text(text = tab.label, style = NmTypography.caption, color = tint)
        }
    }
}
