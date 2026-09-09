package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.TimerPreset

/**
 * C3 프리셋 시트 — 정본 `타이머 / C3 프리셋 시트` · `— 편집 모드`.
 *
 * ## 누르면 곧바로 시작한다
 * 정본 부제가 "누르면 타이머가 바로 시작됩니다"다. 확인 단계를 넣지 않는다 — 처치 중에
 * 한 손으로 쓰는 화면이라 탭 수가 곧 비용이다. 잘못 눌러도 카드에서 바로 정지할 수 있다.
 *
 * ## 편집 모드는 같은 시트를 갈아입힌다
 * 정본이 프레임을 둘로 그렸지만 목록·행 생김새가 같고 부제와 행 양끝만 바뀐다.
 * 화면을 새로 띄우면 "어디로 갔지"가 생기므로 자리에서 바꾼다.
 *
 * ⚠️ 편집 모드에서 행을 눌러도 **시작하지 않는다** — 수정 시트가 열린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerPresetSheet(
    presets: List<TimerPreset>,
    editing: Boolean,
    onStart: (TimerPreset) -> Unit,
    onToggleEditing: () -> Unit,
    onEditPreset: (TimerPreset) -> Unit,
    onDeletePreset: (TimerPreset) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NmTheme.semanticColors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 프리셋 6개면 절반 높이(partial)를 넘긴다. 거기서 멈추면 마지막 항목이 시트 밖으로
        // 밀려 잘린 채 보이므로, 처음부터 콘텐츠 높이만큼 펼친다.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 3버튼 내비게이션에서는 하단 인셋이 커서, 흡수하지 않으면 마지막 프리셋이 가린다.
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("프리셋", style = SheetTitle, color = colors.textPrimary)
                    Text(
                        text = if (editing) "완료" else "편집",
                        style = if (editing) DoneStyle else EditStyle,
                        color = NmColor.Primary.C600,
                        modifier = Modifier.clickable(onClick = onToggleEditing)
                    )
                }
                Text(
                    text = if (editing) {
                        "프리셋을 눌러 수정 · 휴지통으로 삭제"
                    } else {
                        "누르면 타이머가 바로 시작됩니다"
                    },
                    style = SheetSubStyle,
                    color = colors.textSecondary
                )
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    PresetRow(
                        preset = preset,
                        editing = editing,
                        onClick = { if (editing) onEditPreset(preset) else onStart(preset) },
                        onDelete = { onDeletePreset(preset) }
                    )
                }
            }

            // 정본은 「프리셋 추가」를 편집 모드에서만 보여 준다 — 평소에는 시작만 하는 시트다.
            if (editing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RowShape)
                        .border(1.dp, NmColor.Neutral.C300, RowShape)
                        .clickable(onClick = onAdd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(DsR.drawable.nm_ic_plus),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "프리셋 추가",
                        style = AddStyle,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetRow(preset: TimerPreset, editing: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(NmColor.Neutral.C50, RowShape)
            .border(1.dp, colors.border, RowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editing) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_trash_2),
                contentDescription = "${preset.label} 삭제",
                tint = NmColor.Error.C500,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete)
            )
        }
        Text(preset.label, style = PresetLabel, color = colors.textPrimary)
        Text(
            text = preset.category.label,
            style = TagStyle,
            color = tagForeground(preset.category),
            modifier = Modifier
                .clip(TagShape)
                .background(tagBackground(preset.category), TagShape)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatDuration(preset.durationSeconds), style = DurationStyle, color = colors.textSecondary)
            if (editing) {
                // 순서 변경(드래그)은 아직 없다 — 손잡이만 정본대로 두면 눌러도 안 되는 UI 가 되므로
                // 붙일 때 함께 넣는다.
                Icon(
                    painter = painterResource(DsR.drawable.nm_ic_chevron_right),
                    contentDescription = null,
                    tint = NmColor.Neutral.C400,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    painter = painterResource(DsR.drawable.nm_ic_circle_play),
                    contentDescription = null,
                    tint = NmColor.Primary.C500,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// 정본 수치 — `타이머 / C3 프리셋 시트`
private val RowShape = RoundedCornerShape(12.dp)
private val TagShape = RoundedCornerShape(6.dp)

private val SheetTitle = NmTypography.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
private val SheetSubStyle = NmTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)
private val EditStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
private val DoneStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val PresetLabel = NmTypography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val TagStyle = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
private val DurationStyle = NmTypography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
private val AddStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
