package app.nursemate.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2ButtonPrimary
import app.nursemate.core.designsystem.v2.NmV2RaisedButton
import app.nursemate.core.designsystem.v2.NmV2TextField
import app.nursemate.core.designsystem.v2.NmV2Type
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

    TimerSheet(onDismiss = onDismiss, handleGap = SectionGap) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                // 키보드가 올라오면 폼이 화면에 안 들어간다. 스크롤을 주지 않으면 통이 눌려
                // 휠이 찌그러지고, 아래쪽 버튼이 잘린다.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(SectionGap)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (preset == null) "프리셋 추가" else "프리셋 편집",
                    style = TimerSheetTitle,
                    color = colors.textPrimary
                )
                // 보이는 아이콘은 22 지만 누르는 영역은 48 이다.
                Icon(
                    painter = painterResource(NmSymbols.Close),
                    contentDescription = "닫기",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .size(22.dp)
                )
            }

            Field("처치 키워드") {
                NmV2TextField(
                    value = label,
                    onValueChange = { if (it.length <= LABEL_MAX) label = it },
                    placeholder = "예: 수액 교체",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
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
                    // 정본은 흰 볼록 버튼에 빨간 글자다 — 빨간 면(Button Destructive)은 확인 모달의 몫이다.
                    NmV2RaisedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Icon(
                            painterResource(NmSymbols.Delete),
                            contentDescription = null,
                            tint = NmColor.Error.C600,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("삭제", style = NmV2Type.button, color = NmColor.Error.C600, maxLines = 1)
                    }
                }
                NmV2ButtonPrimary(
                    text = "저장",
                    onClick = { onSave(label.trim(), category, seconds) },
                    modifier = Modifier.weight(1f),
                    enabled = canSave
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = NmV2Type.chipConfirmed, color = NmTheme.semanticColors.textSecondary)
        content()
    }
}

/** 분류 한 칸 — 볼록 보조 버튼. 고른 칸은 `primary-500` 1.5 테두리에 `primary-700` 굵은 글자다. */
@Composable
private fun CategorySegment(
    category: TimerCategory,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NmV2RaisedButton(
        onClick = onClick,
        modifier = modifier.semantics { selected = active },
        selected = active,
        contentPadding = SegmentPadding
    ) { content ->
        Text(
            text = category.label,
            style = if (active) SegmentActive else SegmentIdle,
            color = if (active) NmColor.Primary.C700 else content,
            maxLines = 1
        )
    }
}

// 정본 수치 — `C3 프리셋 추가` · `C3 프리셋 편집`
private val SectionGap = 18.dp
private val SegmentPadding = PaddingValues(vertical = 10.dp)
private const val DEFAULT_SECONDS = 15 * 60
private const val LABEL_MAX = 20

private val SegmentIdle = NmV2Type.menu
private val SegmentActive = NmV2Type.menu.copy(fontWeight = FontWeight.Bold)
