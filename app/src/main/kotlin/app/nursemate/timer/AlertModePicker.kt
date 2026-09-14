package app.nursemate.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import app.nursemate.core.model.AlertMode

/**
 * 울림 방식 선택기 — 정본 `Component / 울림 방식 선택기`.
 *
 * ## 정본은 2칸, 여기는 3칸이다
 * 정본이 소리·무음 둘만 둔 근거는 **iOS 제약 #7** — iOS 시스템 알람은 발화 시 항상 진동하고
 * 앱이 끌 수 없어 '진동'과 '무음'이 동작상 같아진다. Android 는 진동이 알림 채널 속성이라
 * 따로 끄고 켤 수 있어 셋을 준다(2026-09-09 결정). 칸 생김새는 정본 그대로다.
 */
@Composable
fun AlertModePicker(selected: AlertMode, onSelect: (AlertMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AlertMode.entries.forEach { mode ->
            ModeCard(
                modifier = Modifier.weight(1f),
                mode = mode,
                active = mode == selected,
                onClick = { onSelect(mode) }
            )
        }
    }
}

@Composable
private fun ModeCard(mode: AlertMode, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    val background = if (active) NmColor.Primary.C50 else NmColor.Neutral.C50
    val stroke = if (active) NmColor.Primary.C500 else colors.border
    val strokeWidth = if (active) 1.5.dp else 1.dp
    val tint = if (active) NmColor.Primary.C600 else colors.textSecondary
    val labelColor = if (active) NmColor.Primary.C700 else colors.textSecondary

    Column(
        modifier = modifier
            .clip(CardShape)
            .background(background, CardShape)
            .border(strokeWidth, stroke, CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(mode.iconRes()),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(mode.label(), style = LabelStyle, color = labelColor)
    }
}

fun AlertMode.label(): String = when (this) {
    AlertMode.SOUND -> "소리"
    AlertMode.VIBRATE -> "진동"
    AlertMode.SILENT -> "무음"
}

private fun AlertMode.iconRes(): Int = when (this) {
    AlertMode.SOUND -> DsR.drawable.nm_ic_volume_2
    AlertMode.VIBRATE -> DsR.drawable.nm_ic_vibrate
    AlertMode.SILENT -> DsR.drawable.nm_ic_volume_x
}

/**
 * 선택기 아래 안내 문구 — 정본 문구를 Android 동작에 맞게 고쳤다.
 *
 * 정본은 "'무음'은 벨소리가 켜져있으면 진동, 꺼져있으면 무음"이라고 쓰는데, 이건 진동을
 * 끌 수 없는 iOS 얘기다. 여기서는 '무음'이 **진짜 무음**이라 그대로 쓰면 거짓말이 된다.
 */
const val ALERT_MODE_NOTICE = "‘소리’는 무음 모드에서도 크게 울려요. ‘진동’은 소리 없이 진동만, " +
    "‘무음’은 화면 알림만 떠요."

private val CardShape = RoundedCornerShape(12.dp)
private val LabelStyle = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
