package app.nursemate.pill

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.DividingLine

/**
 * 조건 칸의 **색 규칙** — 정본 ③ (NM-516).
 *
 * ## 회색과 호박색을 가르는 것은 「확실한가」다
 * 필터인지 점수인지가 아니다. 계약이 바뀌어 어떤 항이 하드에서 소프트로 옮겨가도 이 기준은
 * 그대로 선다 — 사용자가 보는 것은 「내가 정한 값인가」뿐이다.
 *
 * | | 칸 | 테두리 | 글자 | 꺾쇠 |
 * |---|---|---|---|---|
 * | 추정값 · 조건 없음 | `$surface` | `$neutral-300` | `$text-primary` 600 | `$text-tertiary` |
 * | 확실한 값 | `$warning-50` | `$warning-300` | `$warning-900` 700 | `$warning-700` |
 */
@Composable
internal fun conditionTone(certain: Boolean): ConditionTone {
    val colors = NmTheme.semanticColors
    return if (certain) {
        ConditionTone(
            fill = NmColor.Warning.C50,
            stroke = NmColor.Warning.C300,
            label = NmColor.Warning.C900,
            chevron = NmColor.Warning.C700,
            weight = FontWeight.Bold
        )
    } else {
        ConditionTone(
            fill = colors.surface,
            stroke = NmColor.Neutral.C300,
            label = colors.textPrimary,
            chevron = colors.textTertiary,
            weight = FontWeight.SemiBold
        )
    }
}

internal data class ConditionTone(
    val fill: Color,
    val stroke: Color,
    val label: Color,
    val chevron: Color,
    val weight: FontWeight
)

/**
 * 조건 한 칸 — 라벨 위, 드롭다운 아래.
 *
 * 정본 드롭다운은 높이 30 · radius 7 · 글자 12 다. 라벨은 11/500 `$text-tertiary` 로 칸 위에
 * 붙는다([ConditionLabel]).
 */
@Composable
internal fun ConditionDropdown(value: String, certain: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tone = conditionTone(certain)
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .background(tone.fill)
            .border(1.dp, tone.stroke, shape)
            .clickable(onClick = onClick)
            // 정본 padding [0,4,0,7] · gap 2.
            .padding(start = 7.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = DropdownValue.copy(fontWeight = tone.weight),
            color = tone.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // ⚠️ 여기에 스페이서를 더하면 글자와 폭을 반씩 나눠 가져 「전체」가 「…」로 잘린다.
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.nm_ic_chevron_down),
            contentDescription = null,
            tint = tone.chevron,
            modifier = Modifier.size(10.dp)
        )
    }
}

/** 칸 위의 작은 이름 — '각인' · '구분선' · '마크'. 정본 11/500 `$text-tertiary`. */
@Composable
internal fun ConditionLabel(text: String) {
    Text(text = text, style = FieldLabel, color = NmTheme.semanticColors.textTertiary)
}

/**
 * 조건 한 칸의 **읽기 모양** — 접힌 카드(정본 ② · ⑫)가 쓴다.
 *
 * 펼침의 드롭다운과 달리 꺾쇠가 없고 알약 모양(r12)이다. 색 규칙은 같다 —
 * 접었다 폈다 할 때 읽는 법이 바뀌면 사용자가 같은 값을 둘로 읽는다.
 */
@Composable
internal fun ConditionValue(value: String, certain: Boolean, fontSize: TextUnit, padding: PaddingValues) {
    val colors = NmTheme.semanticColors
    val tone = conditionTone(certain)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (certain) tone.fill else NmColor.Neutral.C100)
            .border(1.dp, if (certain) tone.stroke else NmColor.Neutral.C200, RoundedCornerShape(12.dp))
            .padding(padding)
    ) {
        Text(
            text = value,
            style = DropdownValue.copy(fontSize = fontSize, fontWeight = tone.weight),
            color = if (certain) tone.label else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 조건 메뉴 — 맨 위가 늘 **전체**다.
 *
 * ## 전체가 곧 되돌리기다
 * 정본이 「되돌리기: 각인 칸 안 · 외형은 메뉴의 전체」로 적는다. 조건을 푸는 길이 메뉴 안에
 * 있어야 사용자가 「잘못 골랐다」를 같은 자리에서 되돌린다.
 *
 * @param options 전체 **아래**에 붙는 것들. 「없음」도 여기 들어온다 — 모델은 못 내지만
 *   사용자는 고를 수 있는 값이다
 */
@Composable
internal fun <T> ConditionMenu(
    options: List<Pair<T, String>>,
    selected: T?,
    width: Dp = 160.dp,
    onDismiss: () -> Unit,
    onSelect: (T?) -> Unit
) {
    val colors = NmTheme.semanticColors
    Popup(
        // 드롭다운 **아래**로 내린다. 그대로 두면 메뉴가 칸을 덮어 지금 무엇을 고치는 중인지
        // 안 보인다 — 칸 높이(30)에 조금 띄운 값이다.
        offset = IntOffset(0, with(LocalDensity.current) { 34.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        // 외형 메뉴와 같은 이유로 포커스를 주지 않는다 — 구분선 메뉴를 연 채 마크를 누르면
        // 메뉴만 닫히고 마크는 안 열린다. 뒤로가기는 아래에서 직접 받는다.
        properties = PopupProperties(focusable = false)
    ) {
        BackHandler(onBack = onDismiss)
        Column(
            modifier = Modifier
                .width(width)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, NmColor.Neutral.C200, RoundedCornerShape(12.dp))
                .padding(vertical = 6.dp)
        ) {
            MenuRow(label = "전체", checked = selected == null) {
                onSelect(null)
                onDismiss()
            }
            options.forEach { (value, label) ->
                MenuRow(label = label, checked = value == selected) {
                    onSelect(value)
                    onDismiss()
                }
            }
        }
    }
}

/** 정본 메뉴 줄 — 높이 40, 체크 16, 고른 줄은 `$neutral-100` 바탕에 `$primary-600` 체크. */
@Composable
private fun MenuRow(label: String, checked: Boolean, onClick: () -> Unit) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (checked) NmColor.Neutral.C100 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_check),
                    contentDescription = null,
                    tint = NmColor.Primary.C600,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(text = label, style = MenuLabel, color = colors.textPrimary)
    }
}

/**
 * 조건이 없을 때 적을 말.
 *
 * 수동 추가 알약은 읽어 본 적이 없어 「전체」가 어색하다 — 정본 ⑧-h 가 '-' 다.
 */
internal fun unconditioned(manual: Boolean) = if (manual) "-" else "전체"

/**
 * 구분선 칸에 적을 말. **접힘과 펼침이 같은 말을 써야 한다** — 접었다 폈다 할 때 읽는 법이
 * 바뀌면 사용자가 둘을 다른 값으로 읽는다.
 */
internal fun DividingLine?.conditionLabel(manual: Boolean = false): String = when (this) {
    null -> unconditioned(manual)

    DividingLine.NONE -> "없음"

    DividingLine.PLUS -> "(+)형"

    DividingLine.MINUS -> "(−)형"

    // 서버 enum 이 늘어난 경우. 조건을 걸지 않는 쪽으로 읽는다.
    DividingLine.UNKNOWN -> unconditioned(manual)
}

/** 마크 칸에 적을 말. `false` 는 사용자가 「없음」을 고른 것이다 — 모델은 내지 않는다. */
internal fun Boolean?.markConditionLabel(manual: Boolean = false): String = when (this) {
    null -> unconditioned(manual)
    false -> "없음"
    true -> "있음"
}

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val FieldLabel = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
private val DropdownValue = NmTypography.body.copy(fontSize = 12.sp)
private val MenuLabel = NmTypography.body.copy(fontSize = 14.sp)
