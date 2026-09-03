package app.nursemate.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 한 줄 입력 — 정본 `Foundation / Components` §Text Field.
 *
 * 라벨 13/500 · 간격 6 · 필드 패딩 14×16 · 입력 15/regular.
 *
 * ## 상태 색은 램프 규칙으로 정했다
 * 정본에 그려진 건 **기본 상태 하나뿐**이라 나머지는 색 램프의 일관된 쓰임을 따랐다.
 * 실제 화면(⑧-e 각인 입력)에 올려 보고 어긋나면 그때 맞춘다.
 *
 * | 상태 | 테두리 | 배경 |
 * |---|---|---|
 * | 기본 | border | surface |
 * | 포커스 | primary-500 | surface |
 * | 오류 | error-500 | surface |
 * | 비활성 | border | neutral-100 |
 *
 * `BasicTextField` 를 쓰는 이유는 Material 의 `OutlinedTextField` 가 자체 패딩·라벨
 * 애니메이션을 강제해 정본 수치(14×16)를 맞출 수 없기 때문이다.
 */
@Composable
fun NmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val colors = NmTheme.semanticColors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val outline = when {
        isError -> NmColor.Error.C500
        focused -> NmColor.Primary.C500
        else -> colors.border
    }
    val container = if (enabled) colors.surface else NmColor.Neutral.C100

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(text = label, style = LabelStyle, color = colors.textSecondary)
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSource,
            textStyle = InputStyle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(NmColor.Primary.C500),
            modifier = Modifier
                .fillMaxWidth()
                .background(container, SHAPE)
                .border(if (focused || isError) 1.5.dp else 1.dp, outline, SHAPE)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) { inner ->
            // placeholder 는 값이 비었을 때만 겹쳐 그린다. BasicTextField 는 이걸 제공하지 않는다.
            Box {
                if (value.isEmpty() && placeholder != null) {
                    Text(text = placeholder, style = InputStyle, color = colors.textTertiary)
                }
                inner()
            }
        }

        if (helperText != null) {
            Text(
                text = helperText,
                style = LabelStyle,
                color = if (isError) NmColor.Error.C600 else colors.textTertiary
            )
        }
    }
}

private val SHAPE = RoundedCornerShape(NmRadius.md)

// 정본 13/500 · 15/regular. 스케일에 그 크기가 없어 여기서 명시한다.
private val LabelStyle = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
private val InputStyle = NmTypography.bodyLarge.copy(fontSize = 15.sp)
