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
 * C3 프리셋 시트 — 정본 `타이머 / C3 프리셋 시트`.
 *
 * ## 누르면 곧바로 시작한다
 * 정본 부제가 "누르면 타이머가 바로 시작됩니다"다. 확인 단계를 넣지 않는다 — 처치 중에
 * 한 손으로 쓰는 화면이라 탭 수가 곧 비용이다. 잘못 눌러도 카드에서 바로 정지할 수 있다.
 *
 * 프리셋 편집(추가·수정·삭제·순서)은 아직 없다. [onEdit] 가 비어 있으면 「편집」을 감춘다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerPresetSheet(
    presets: List<TimerPreset>,
    onStart: (TimerPreset) -> Unit,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null
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
                    if (onEdit != null) {
                        Text(
                            text = "편집",
                            style = EditStyle,
                            color = NmColor.Primary.C600,
                            modifier = Modifier.clickable(onClick = onEdit)
                        )
                    }
                }
                Text("누르면 타이머가 바로 시작됩니다", style = SheetSubStyle, color = colors.textSecondary)
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    PresetRow(preset = preset, onClick = { onStart(preset) })
                }
            }

            if (onAdd != null) {
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
private fun PresetRow(preset: TimerPreset, onClick: () -> Unit) {
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
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_circle_play),
                contentDescription = null,
                tint = NmColor.Primary.C500,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// 정본 수치 — `타이머 / C3 프리셋 시트`
private val RowShape = RoundedCornerShape(12.dp)
private val TagShape = RoundedCornerShape(6.dp)

private val SheetTitle = NmTypography.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
private val SheetSubStyle = NmTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)
private val EditStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
private val PresetLabel = NmTypography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val TagStyle = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
private val DurationStyle = NmTypography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
private val AddStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
