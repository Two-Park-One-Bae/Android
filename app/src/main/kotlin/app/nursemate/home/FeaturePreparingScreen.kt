package app.nursemate.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR

/**
 * 아직 구현되지 않은 기능의 안내 화면.
 * 홈 카드가 눌리지 않고 멈추면 앱이 고장난 것처럼 보이므로, 상태를 명시하고 되돌아갈 길을 준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturePreparingScreen(
    title: String,
    description: String,
    icon: Painter,
    iconBackground: Color,
    iconTint: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    Scaffold(
        // 상단바가 상태바에 가리지 않도록 시스템 인셋을 반영한다
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = colors.bgApp,
        topBar = {
            TopAppBar(
                title = { Text(title, style = NmTypography.heading3, color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(DsR.drawable.nm_ic_arrow_back),
                            contentDescription = "뒤로",
                            tint = colors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                text = "준비 중인 기능이에요",
                style = NmTypography.heading3,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = NmSpacing.lg)
            )
            Text(
                text = description,
                style = NmTypography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = NmSpacing.sm)
            )
        }
    }
}
