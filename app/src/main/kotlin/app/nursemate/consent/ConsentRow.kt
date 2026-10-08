package app.nursemate.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmSemanticColors
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.ConsentDefinition

/**
 * 동의 항목 한 줄 — 체크박스 · `[필수]`/`[선택]` · 항목명 · `보기`.
 *
 * 동의 온보딩([ConsentScreen])과 「약관 및 동의」([ConsentSettingsScreen])가 같은 줄을 쓴다.
 * 정본이 두 화면에 **같은 행**을 그려 뒀고(spec §약관 및 동의 「동의 온보딩과 같은 행」),
 * 따로 두면 한쪽만 고쳐져 두 화면이 서로 다른 말을 하게 된다.
 *
 * @param locked 체크를 바꿀 수 없다 — 「약관 및 동의」의 **필수** 항목. 체크된 채 회색으로
 *   굳는다. 끌 수 없는 체크를 파랗게 두면 누를 수 있는 것처럼 보인다.
 */
@Composable
internal fun ConsentRow(
    definition: ConsentDefinition,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onOpenPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false
) {
    val colors = NmTheme.semanticColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            // 체크 토글은 행 전체가 받고, '보기'만 따로 가로챈다. 체크박스만 누르게 하면
            // 22dp 과녁이라 손가락으로 맞추기 어렵다.
            .clickable(enabled = enabled && !locked, onClick = onToggle)
            .padding(vertical = 14.dp, horizontal = NmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NmConsentCheckBox(
            checked = checked,
            size = 22.dp,
            radius = 7.dp,
            mark = 14.dp,
            locked = locked
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 정본이 두 꼬리표의 색을 가른다 — 필수는 primary-600, 선택은 text-tertiary.
            // 선택을 같은 색으로 두면 「필수」 두 줄과 나란히 놓였을 때 셋 다 필수로 읽힌다.
            if (definition.required) {
                Text(text = "필수", style = Badge, color = NmColor.Primary.C600)
            } else {
                Text(text = "선택", style = Badge, color = colors.textTertiary)
            }
            // 서버가 준 표시용 항목명. 앱에 문구를 갖고 있지 않다.
            Text(text = definition.title, style = ItemLabel, color = colors.textPrimary)
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(NmRadius.sm))
                .clickable(enabled = enabled, onClick = onOpenPolicy)
                .padding(horizontal = NmSpacing.xs, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = "보기", style = ViewLabel, color = colors.textSecondary)
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_chevron_right),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 체크박스 — DS 컴포넌트 목록에 없어 동의 화면들이 함께 쓴다. 크기가 두 가지(26·22)라 인자로 받는다.
 *
 * @param locked 바꿀 수 없는 체크. 정본이 회색(`neutral-300`)으로 굳혀 둔다.
 */
@Composable
internal fun NmConsentCheckBox(
    checked: Boolean,
    size: Dp,
    radius: Dp,
    mark: Dp,
    locked: Boolean = false,
    colors: NmSemanticColors = NmTheme.semanticColors
) {
    val shape = RoundedCornerShape(radius)
    val fill = when {
        locked -> NmColor.Neutral.C300
        checked -> NmColor.Primary.C500
        else -> colors.surface
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(fill)
            .let { if (checked || locked) it else it.border(1.5.dp, colors.border, shape) },
        contentAlignment = Alignment.Center
    ) {
        // 미체크 상태에서는 아예 그리지 않는다. 체크할 때 아이콘이 나타나는 것이 정본이다.
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_check),
                contentDescription = null,
                tint = NmColor.Neutral.C0,
                modifier = Modifier.size(mark)
            )
        }
    }
}

// 정본 스케일에 없는 크기다. 두 화면이 같은 값을 쓰도록 여기 둔다.
private val Badge = NmTypography.caption.copy(fontWeight = FontWeight.SemiBold)
private val ItemLabel = NmTypography.bodyLarge.copy(fontSize = 15.sp)
private val ViewLabel = NmTypography.body.copy(fontSize = 13.sp)
