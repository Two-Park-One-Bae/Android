package app.nursemate.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 주 버튼 — 디자인 `Component / Button Primary`.
 * 채움 `primary-500` · 라운드 `radius-md` · 패딩 24×15 · 라벨 16sp/600.
 */
@Composable
fun NmButtonPrimary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(NmRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = NmColor.Primary.C500,
            contentColor = NmColor.Neutral.C0,
            disabledContainerColor = NmColor.Neutral.C200,
            disabledContentColor = NmColor.Neutral.C500
        ),
        contentPadding = ButtonPadding,
        modifier = modifier
    ) {
        Text(text = text, style = ButtonLabel)
    }
}

/**
 * 보조 버튼 — 디자인 `Component / Button Secondary`.
 * 채움 `neutral-100` · 테두리 `border` · 라벨 `text-primary`.
 */
@Composable
fun NmButtonSecondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = NmTheme.semanticColors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(NmRadius.md),
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = NmColor.Neutral.C100,
            contentColor = colors.textPrimary,
            disabledContainerColor = NmColor.Neutral.C50,
            disabledContentColor = NmColor.Neutral.C400
        ),
        contentPadding = ButtonPadding,
        modifier = modifier
    ) {
        Text(text = text, style = ButtonLabel)
    }
}

private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 15.dp)

/** 디자인 라벨은 16·600. [NmTypography.bodyLarge]가 16·400이라 굵기만 올린다. */
private val ButtonLabel = NmTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
