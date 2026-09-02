package app.nursemate.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** M3 슬롯 밖의 시맨틱 색 접근용 — `NmTheme.semanticColors` 로 사용 */
val LocalNmSemanticColors = staticCompositionLocalOf { NmSemanticColors.Light }

private val LightColorScheme = lightColorScheme(
    primary = NmColor.Primary.C500,
    onPrimary = NmColor.Neutral.C0,
    primaryContainer = NmColor.Primary.C100,
    onPrimaryContainer = NmColor.Primary.C900,
    secondary = NmColor.Secondary.C500,
    onSecondary = NmColor.Neutral.C0,
    secondaryContainer = NmColor.Secondary.C100,
    onSecondaryContainer = NmColor.Secondary.C900,
    error = NmColor.Error.C500,
    onError = NmColor.Neutral.C0,
    errorContainer = NmColor.Error.C100,
    onErrorContainer = NmColor.Error.C900,
    background = NmSemanticColors.Light.bgApp,
    onBackground = NmSemanticColors.Light.textPrimary,
    surface = NmSemanticColors.Light.surface,
    onSurface = NmSemanticColors.Light.textPrimary,
    onSurfaceVariant = NmSemanticColors.Light.textSecondary,
    outline = NmSemanticColors.Light.border
)

private val DarkColorScheme = darkColorScheme(
    primary = NmColor.Primary.C300,
    onPrimary = NmColor.Primary.C900,
    primaryContainer = NmColor.Primary.C700,
    onPrimaryContainer = NmColor.Primary.C100,
    secondary = NmColor.Secondary.C300,
    onSecondary = NmColor.Secondary.C900,
    secondaryContainer = NmColor.Secondary.C700,
    onSecondaryContainer = NmColor.Secondary.C100,
    error = NmColor.Error.C300,
    onError = NmColor.Error.C900,
    errorContainer = NmColor.Error.C700,
    onErrorContainer = NmColor.Error.C100,
    background = NmSemanticColors.Dark.bgApp,
    onBackground = NmSemanticColors.Dark.textPrimary,
    surface = NmSemanticColors.Dark.surface,
    onSurface = NmSemanticColors.Dark.textPrimary,
    onSurfaceVariant = NmSemanticColors.Dark.textSecondary,
    outline = NmSemanticColors.Dark.border
)

private val NmMaterialTypography = Typography(
    displayLarge = NmTypography.display,
    headlineLarge = NmTypography.heading1,
    headlineMedium = NmTypography.heading2,
    headlineSmall = NmTypography.heading3,
    titleLarge = NmTypography.title,
    bodyLarge = NmTypography.bodyLarge,
    bodyMedium = NmTypography.body,
    labelSmall = NmTypography.caption
)

@Composable
fun NurseMateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val semanticColors = if (darkTheme) NmSemanticColors.Dark else NmSemanticColors.Light
    CompositionLocalProvider(LocalNmSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = NmMaterialTypography,
            content = content
        )
    }
}

/** 테마 토큰 편의 접근자 */
object NmTheme {
    val semanticColors: NmSemanticColors
        @Composable get() = LocalNmSemanticColors.current
}
