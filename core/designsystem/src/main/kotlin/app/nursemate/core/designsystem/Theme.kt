package app.nursemate.core.designsystem

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

// 라이트 고정이라 지금은 쓰이지 않는다. 다크 시안이 나오면 NurseMateTheme 에서 되살린다.
@Suppress("UnusedPrivateProperty")
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

/**
 * 앱 테마 — **라이트 고정**.
 *
 * 디자인 정본(`spec/design/DESIGN.pen`)에 다크 시안이 없다. 86개 프레임이 전부 라이트다.
 * 시스템 설정을 따라가게 두면 색을 우리가 지어내야 하고, 그러면 다크 시안이 나왔을 때
 * 다시 만들게 된다.
 *
 * 실제로 그 대가를 이미 봤다 — 컴포넌트 갤러리를 다크로 띄웠더니 `NmButtonSecondary` 와
 * 비활성 텍스트 필드가 **밝은 배경에 밝은 글씨**로 읽히지 않았다. 밝은 고정색을 쓰면서
 * 글자만 시맨틱 토큰을 따라갔기 때문이다.
 *
 * 다크를 지원할 때는 [NmSemanticColors.Dark] 와 [DarkColorScheme] 이 이미 있으니
 * 여기서 분기를 되살리고, 고정색을 쓰는 컴포넌트를 전부 훑으면 된다.
 */
@Composable
fun NurseMateTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNmSemanticColors provides NmSemanticColors.Light) {
        MaterialTheme(
            colorScheme = LightColorScheme,
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
