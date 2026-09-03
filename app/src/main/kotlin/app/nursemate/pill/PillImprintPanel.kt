package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.DividingLine

/*
 각인 입력판 — 디자인 `⑧-e 각인 입력 (키보드)`.

 앞뒤 두 덩이가 같은 모양이고, 각 덩이는 '해당 없음' 체크 · 각인 칸 · 구분선 3분할 · 마크 체크로
 이뤄진다. '해당 없음'을 켜면 그 면의 입력을 통째로 감춘다 — 아무것도 없다고 말한 뒤에
 각인 칸이 남아 있으면 무엇을 적으라는 건지 알 수 없다.

 앞면은 **사진에 찍힌 면**이다(spec §수정·후보 선택). 사용자가 알약을 뒤집어 볼 필요가 있는지
 알려 주는 기준이라 라벨에 그대로 적는다.
*/

@Composable
internal fun ImprintPanel(
    faces: FaceInputs,
    frontText: TextFieldValue,
    backText: TextFieldValue,
    onChange: (FaceInputs) -> Unit,
    onTextChange: (FaceSide, TextFieldValue) -> Unit,
    onFocus: (FaceSide?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NmColor.Neutral.C100, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FaceSection(
            title = "앞면 (사진 면)",
            side = FaceSide.Front,
            input = faces.front,
            text = frontText,
            onInputChange = { onChange(faces.copy(front = it)) },
            onTextChange = onTextChange,
            onFocus = onFocus
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NmTheme.semanticColors.border))
        FaceSection(
            title = "뒷면",
            side = FaceSide.Back,
            input = faces.back,
            text = backText,
            onInputChange = { onChange(faces.copy(back = it)) },
            onTextChange = onTextChange,
            onFocus = onFocus
        )
    }
}

@Composable
private fun ColumnScope.FaceSection(
    title: String,
    side: FaceSide,
    input: FaceInput,
    text: TextFieldValue,
    onInputChange: (FaceInput) -> Unit,
    onTextChange: (FaceSide, TextFieldValue) -> Unit,
    onFocus: (FaceSide?) -> Unit
) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = SectionTitle, color = colors.textPrimary)
        Row(
            modifier = Modifier.clickable { onInputChange(input.copy(blank = !input.blank)) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NmCheckbox(checked = input.blank)
            Text(text = "해당 없음", style = ControlLabel, color = colors.textTertiary)
        }
    }

    if (input.blank) return

    ImprintField(
        text = text,
        onValueChange = { value ->
            onTextChange(side, value)
            onInputChange(input.copy(imprint = value.text))
        },
        onFocusChange = { focused -> onFocus(if (focused) side else null) }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "구분선", style = SegmentLabel, color = colors.textSecondary)
            DividingLineSegment(selected = input.dividingLine) { line ->
                // 고른 것을 다시 누르면 조건에서 뺀다.
                onInputChange(input.copy(dividingLine = line.takeIf { it != input.dividingLine }))
            }
        }
        Row(
            modifier = Modifier.clickable { onInputChange(input.copy(hasMark = !input.hasMark)) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NmCheckbox(checked = input.hasMark)
            Text(text = "마크 있음", style = ControlLabel, color = colors.textPrimary)
        }
    }
}

/**
 * 각인 칸.
 *
 * ⚠️ 정본에 자리 안내 문구가 없지만 넣었다 — 빈 칸만 덩그러니 두면 무엇을 적는 칸인지 알 수 없다.
 * 숫자·영문·특수기호가 섞여 들어오므로 자동 대문자만 켜고 키보드 종류는 건드리지 않는다.
 */
@Composable
private fun ImprintField(
    text: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    val colors = NmTheme.semanticColors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = text,
        onValueChange = onValueChange,
        textStyle = FieldValue.copy(color = colors.textPrimary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done
        ),
        cursorBrush = SolidColor(NmColor.Primary.C500),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                focused = state.isFocused
                onFocusChange(state.isFocused)
            }
            .clip(shape)
            .background(colors.surface)
            // 정본은 이 칸이 열린 상태(포커스)만 그려 둔다 — 테두리 primary-500 1.5.
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) NmColor.Primary.C500 else colors.border,
                shape = shape
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        decorationBox = { inner ->
            if (text.text.isEmpty()) {
                Text(text = "각인을 입력하세요", style = FieldValue, color = colors.textTertiary)
            }
            inner()
        }
    )
}

/** 구분선 3분할 — 없음 · − · +. */
@Composable
private fun DividingLineSegment(selected: DividingLine?, onSelect: (DividingLine) -> Unit) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier.clip(shape).border(1.dp, colors.border, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentOptions.forEach { (line, label) ->
            val on = line == selected
            Box(
                modifier = Modifier
                    .background(if (on) NmColor.Primary.C50 else colors.surface)
                    .clickable { onSelect(line) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = if (on) SegmentValueOn else SegmentValue,
                    color = if (on) NmColor.Primary.C600 else colors.textSecondary
                )
            }
        }
    }
}

/** 16×16 체크박스. Material3 Checkbox 는 최소 터치 영역 48 을 강제해 줄 간격이 벌어진다. */
@Composable
private fun NmCheckbox(checked: Boolean) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(if (checked) NmColor.Primary.C500 else Color.Transparent, shape)
            .border(1.5.dp, if (checked) NmColor.Primary.C500 else colors.textTertiary, shape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_check),
                contentDescription = null,
                tint = NmColor.Neutral.C0,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

private val SegmentOptions = listOf(
    DividingLine.NONE to "없음",
    DividingLine.MINUS to "−",
    DividingLine.PLUS to "+"
)

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val SectionTitle = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val ControlLabel = NmTypography.caption
private val SegmentLabel = NmTypography.caption.copy(fontWeight = FontWeight.Medium)
private val SegmentValue = NmTypography.body.copy(fontSize = 13.sp)
private val SegmentValueOn = SegmentValue.copy(fontWeight = FontWeight.Bold)
private val FieldValue = NmTypography.bodyLarge.copy(fontSize = 15.sp)
