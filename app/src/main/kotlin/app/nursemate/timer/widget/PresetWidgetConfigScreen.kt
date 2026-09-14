package app.nursemate.timer.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.formatDuration
import app.nursemate.timer.tagBackground
import app.nursemate.timer.tagForeground

/**
 * 위젯에 담을 프리셋 고르기.
 *
 * 정본에 없는 화면이다 — iOS 는 이 지정을 시스템 위젯 편집 UI 가 대신 해 준다(`AppIntent`
 * 파라미터). Android 에는 그런 자리가 없어 앱이 직접 그린다. C3 프리셋 시트의 행 생김새를
 * 그대로 따라가, 앱 안에서 프리셋을 고를 때와 같아 보이게 했다.
 */
@Composable
fun PresetWidgetConfigScreen(presets: List<TimerPreset>, onPick: (TimerPreset) -> Unit, onClose: () -> Unit) {
    val colors = NmTheme.semanticColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("위젯에 넣을 프리셋", style = TitleStyle, color = colors.textPrimary)
            Text(
                text = if (presets.isEmpty()) {
                    "프리셋이 없어요. 앱에서 추가한 뒤 다시 지정해 주세요"
                } else {
                    "고른 프리셋이 위젯에 표시되고, 누르면 바로 시작됩니다"
                },
                style = SubStyle,
                color = colors.textSecondary
            )
        }

        presets.forEach { preset ->
            PresetRow(preset = preset, onClick = { onPick(preset) })
        }

        NmButtonSecondary(text = "닫기", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PresetRow(preset: TimerPreset, onClick: () -> Unit) {
    val colors = NmTheme.semanticColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(colors.surface, RowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        // ⚠️ **가중치를 가진 자식은 하나여야 한다.** 라벨과 여백에 각각 `weight(1f)` 를 주면
        // 남는 폭이 반씩 나뉘어, **라벨이 짧을수록 시간이 왼쪽으로 붙는다.** 왼쪽 묶음
        // 하나만 늘리고 시간은 SpaceBetween 이 끝으로 밀게 한다.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                // 라벨이 길어 폭을 다 쓸 때 시간과 붙지 않도록.
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = preset.label,
                style = LabelStyle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = preset.category.label,
                style = TagStyle,
                color = tagForeground(preset.category),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(TagShape)
                    .background(tagBackground(preset.category))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(formatDuration(preset.durationSeconds), style = DurationStyle, color = colors.textSecondary)
    }
}

private val RowShape = RoundedCornerShape(14.dp)
private val TagShape = RoundedCornerShape(6.dp)
private val TitleStyle = NmTypography.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
private val SubStyle = NmTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)
private val LabelStyle = NmTypography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val TagStyle = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
private val DurationStyle = NmTypography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
