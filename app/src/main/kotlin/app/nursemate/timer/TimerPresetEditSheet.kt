package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerPreset

/**
 * 프리셋 추가·편집 — 정본 `타이머 / C3 프리셋 추가` · `C3 프리셋 편집`.
 *
 * 두 프레임이 제목과 삭제 버튼 유무만 다르고 나머지가 같아 하나로 둔다.
 * [preset] 이 null 이면 추가다.
 *
 * ## 저장은 라벨이 있어야 열린다
 * 정본 추가 화면의 저장 버튼이 비활성으로 그려져 있다. 라벨 없는 프리셋은 리스트에서
 * 빈칸으로 보여 무엇인지 알 수 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerPresetEditSheet(
    preset: TimerPreset?,
    onSave: (label: String, category: TimerCategory, seconds: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val colors = NmTheme.semanticColors
    var label by remember { mutableStateOf(preset?.label.orEmpty()) }
    var category by remember { mutableStateOf(preset?.category ?: TimerCategory.TEST) }
    var seconds by remember { mutableIntStateOf(preset?.durationSeconds ?: DEFAULT_SECONDS) }
    val canSave = label.isNotBlank() && seconds > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (preset == null) "프리셋 추가" else "프리셋 편집",
                    style = SheetTitle,
                    color = colors.textPrimary
                )
                Icon(
                    painter = painterResource(DsR.drawable.nm_ic_close),
                    contentDescription = "닫기",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onDismiss)
                )
            }

            Field("처치 키워드") {
                LabelInput(value = label, onChange = { label = it })
            }

            Field("분류") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 정본 순서 — 투약 · 처치 · 검사
                    listOf(TimerCategory.MEDICATION, TimerCategory.TREATMENT, TimerCategory.TEST)
                        .forEach { option ->
                            CategorySegment(
                                modifier = Modifier.weight(1f),
                                category = option,
                                active = option == category,
                                onClick = { category = option }
                            )
                        }
                }
            }

            Field("시간") {
                DurationPicker(seconds = seconds, onChange = { seconds = it })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ButtonShape)
                            .background(colors.surface, ButtonShape)
                            .border(1.dp, NmColor.Error.C300, ButtonShape)
                            .clickable(onClick = onDelete)
                            .padding(vertical = 15.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(DsR.drawable.nm_ic_trash_2),
                            contentDescription = null,
                            tint = NmColor.Error.C600,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "삭제",
                            style = ButtonLabel,
                            color = NmColor.Error.C600,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                Text(
                    text = "저장",
                    style = ButtonLabel,
                    color = NmColor.Neutral.C0,
                    modifier = Modifier
                        .weight(1f)
                        .clip(ButtonShape)
                        .background(
                            if (canSave) NmColor.Primary.C500 else NmColor.Neutral.C300,
                            ButtonShape
                        )
                        .clickable(enabled = canSave) { onSave(label.trim(), category, seconds) }
                        .padding(vertical = 15.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = FieldLabel, color = NmTheme.semanticColors.textSecondary)
        content()
    }
}

@Composable
private fun LabelInput(value: String, onChange: (String) -> Unit) {
    val colors = NmTheme.semanticColors
    val focused = value.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InputShape)
            .background(NmColor.Neutral.C50, InputShape)
            .border(
                if (focused) 1.5.dp else 1.dp,
                if (focused) NmColor.Primary.C500 else colors.border,
                InputShape
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LabelField(value = value, onChange = onChange)
    }
}

/** 자리표시자를 겹쳐 그린다 — `BasicTextField` 는 placeholder 를 제공하지 않는다. */
@Composable
private fun LabelField(value: String, onChange: (String) -> Unit) {
    val colors = NmTheme.semanticColors
    Box(Modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text("예: 수액 교체", style = InputStyle, color = colors.textTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= LABEL_MAX) onChange(it) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = InputStyle.copy(color = colors.textPrimary),
            singleLine = true,
            cursorBrush = SolidColor(NmColor.Primary.C500),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
    }
}

@Composable
private fun CategorySegment(
    category: TimerCategory,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    Text(
        text = category.label,
        style = if (active) SegmentActive else SegmentIdle,
        color = if (active) NmColor.Primary.C700 else colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(SegmentShape)
            .background(if (active) NmColor.Primary.C50 else NmColor.Neutral.C50, SegmentShape)
            .border(
                if (active) 1.5.dp else 1.dp,
                if (active) NmColor.Primary.C500 else colors.border,
                SegmentShape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

// 정본 수치 — `C3 프리셋 추가` · `C3 프리셋 편집`
private val InputShape = RoundedCornerShape(12.dp)
private val SegmentShape = RoundedCornerShape(10.dp)
private val ButtonShape = RoundedCornerShape(12.dp)
private const val DEFAULT_SECONDS = 15 * 60
private const val LABEL_MAX = 20

private val SheetTitle = NmTypography.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
private val FieldLabel = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
private val InputStyle = NmTypography.body.copy(fontSize = 15.sp)
private val SegmentActive = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val SegmentIdle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal)
private val ButtonLabel = NmTypography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
