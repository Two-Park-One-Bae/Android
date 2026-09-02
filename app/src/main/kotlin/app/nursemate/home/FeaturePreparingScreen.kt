package app.nursemate.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 아직 구현되지 않은 기능의 안내 화면.
 * 탭을 눌렀을 때 빈 화면이 나오면 앱이 고장난 것처럼 보이므로 상태를 명시한다.
 */
@Composable
fun FeaturePreparingScreen(
    title: String,
    description: String,
    icon: Painter,
    iconBackground: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .padding(NmSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(iconBackground, RoundedCornerShape(NmRadius.xl)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(48.dp)
            )
        }
        Text(
            text = title,
            style = NmTypography.heading3,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = NmSpacing.lg)
        )
        Text(
            text = "준비 중인 기능이에요",
            style = NmTypography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = NmSpacing.xs)
        )
        Text(
            text = description,
            style = NmTypography.body,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = NmSpacing.md)
        )
    }
}
