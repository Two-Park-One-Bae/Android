package app.nursemate.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/**
 * 아이콘 + 제목 + 부제로 구성된 카드형 행. iOS DSKit DSListRow 대응.
 *
 * @param caption 우측 하단 보조 문구(예: 남은 횟수). null이면 표시하지 않는다.
 */
@Composable
fun NmListRow(
    icon: Painter,
    title: String,
    subtitle: String,
    iconBackground: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionColor: Color = iconTint,
    onClick: (() -> Unit)? = null
) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(NmRadius.lg))
            .border(1.dp, colors.border, RoundedCornerShape(NmRadius.lg))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(NmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NmSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(iconBackground, RoundedCornerShape(NmRadius.md)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(NmSpacing.xs)) {
            Text(text = title, style = NmTypography.heading3, color = colors.textPrimary)
            Text(text = subtitle, style = NmTypography.body, color = colors.textSecondary)
            if (caption != null) {
                Text(text = caption, style = NmTypography.caption, color = captionColor)
            }
        }
    }
}

/** 상태를 짧게 보여주는 알약형 칩. iOS DSKit DSChip 대응. */
@Composable
fun NmChip(
    text: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    contentColor: Color = NmColor.Secondary.C600,
    containerColor: Color = NmColor.Secondary.C50
) {
    Row(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(percent = 50))
            .padding(horizontal = NmSpacing.md, vertical = NmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NmSpacing.xs)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(text = text, style = NmTypography.caption, color = contentColor)
    }
}
