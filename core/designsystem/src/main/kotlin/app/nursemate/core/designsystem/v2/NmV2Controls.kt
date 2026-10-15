package app.nursemate.core.designsystem.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme

/*
 * V2.1.0 칩 · 태그 · 입력 칸 · 배너 · 메뉴.
 *
 * 드롭다운 칩만 **볼록**(누르는 것)이고 나머지는 **평면**(읽는 것 · 값 표시)이다(NM-580 결정 3).
 */

/** 칩 높이 30. 누르는 영역은 [TouchTarget] 48 이다(NM-612). */
private val ChipHeight = 30.dp

/**
 * `Dropdown Chip` — 누르면 메뉴를 여는 칩. [confirmed] 면 확정값 표시(`primary-500` 1.5 · 글자 파랑).
 *
 * 보이는 높이는 30 이고 위아래로 9 씩 투명한 누르는 영역을 더해 48 을 맞춘다.
 */
@Composable
fun NmV2DropdownChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, confirmed: Boolean = false) {
    val colors = NmTheme.semanticColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = NmV2ButtonKind.Secondary.look(pressed = pressed, enabled = true)
    val look = if (confirmed) base.copy(stroke = NmColor.Primary.C500) else base
    Box(
        modifier = modifier
            .heightIn(min = TouchTarget)
            .clickable(interactionSource = interaction, indication = null, role = Role.DropdownList, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(ChipHeight)
                .v2Raised(look.copy(stroke = null), CircleShape)
                .border(if (confirmed) 1.5.dp else 1.dp, look.stroke ?: NmColor.Neutral.C200, CircleShape)
                .padding(start = 12.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = if (confirmed) NmV2Type.chipConfirmed else NmV2Type.chip,
                color = if (confirmed) NmColor.Primary.C600 else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                painterResource(NmSymbols.KeyboardArrowDown),
                contentDescription = null,
                tint = if (confirmed) NmColor.Primary.C500 else colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** `Chip` — 표시용(누르지 않음). */
@Composable
fun NmV2Chip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(ChipHeight)
            .background(NmColor.Neutral.C100, CircleShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = NmV2Type.chip, color = NmTheme.semanticColors.textPrimary, maxLines = 1)
    }
}

/** 태그 색. 기본은 처치(청록). 프리셋 행의 검사는 [Info]. */
enum class NmV2TagTone(val container: Color, val content: Color) {
    Secondary(NmColor.Secondary.C50, NmColor.Secondary.C700),
    Info(NmColor.Info.C50, NmColor.Info.C700)
}

/** `Tag` — 분류 표시. */
@Composable
fun NmV2Tag(label: String, modifier: Modifier = Modifier, tone: NmV2TagTone = NmV2TagTone.Secondary) {
    Text(
        label,
        style = NmV2Type.tag,
        color = tone.content,
        modifier = modifier
            .background(tone.container, RoundedCornerShape(NmRadius.sm))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * `Text Field` — 평면 흰 + 테두리. 입력 중 `primary-500` 1.5, 오류 `error-500` 1.5,
 * 비활성은 회색 면에 테두리 없음(NM-580 결정 7).
 */
@Composable
fun NmV2TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = NmTheme.semanticColors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(NmRadius.md)
    val fieldModifier = when {
        !enabled -> Modifier.background(NmColor.Neutral.C100, shape)
        isError -> Modifier.background(colors.surface, shape).border(1.5.dp, NmColor.Error.C500, shape)
        focused -> Modifier.background(colors.surface, shape).border(1.5.dp, NmColor.Primary.C500, shape)
        else -> Modifier.background(colors.surface, shape).border(1.dp, colors.border, shape)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = NmV2Type.chip, color = colors.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = NmV2Type.field.copy(color = if (enabled) colors.textPrimary else colors.textTertiary),
            cursorBrush = SolidColor(NmColor.Primary.C500),
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(fieldModifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = NmV2Type.field, color = colors.textTertiary)
                    }
                    inner()
                }
            }
        )
    }
}

/** `Banner` — 평면 살구(`caution-50` 면 + `caution-300` 테두리). */
@Composable
fun NmV2Banner(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int = NmSymbols.Warning) {
    val shape = RoundedCornerShape(NmRadius.md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NmV2Color.Caution.C50, shape)
            .border(1.dp, NmV2Color.Caution.C300, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = NmV2Color.Caution.C500,
            modifier = Modifier.size(18.dp)
        )
        Text(text, style = NmV2Type.chip, color = NmV2Color.Caution.C700, modifier = Modifier.weight(1f))
    }
}

/** 더보기 메뉴 한 줄. [destructive] 면 빨강(`error-600`). */
data class NmV2MenuItem(
    val label: String,
    @param:DrawableRes val icon: Int,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * `Menu` — 평면 흰 + 테두리 + 목록 그림자. 너비 160, 한 줄 44.
 * 띄우는 자리(팝업 · 앵커)는 화면이 정한다 — 이 컴포넌트는 판만 그린다.
 */
@Composable
fun NmV2Menu(items: List<NmV2MenuItem>, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(NmRadius.md)
    Column(
        modifier = modifier
            .width(160.dp)
            .v2OuterShadows(ShadowList, shape)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
    ) {
        items.forEachIndexed { index, item ->
            val tint = if (item.destructive) NmColor.Error.C600 else colors.textPrimary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(role = Role.Button, onClick = item.onClick)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(item.icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                Text(item.label, style = NmV2Type.menu, color = tint)
            }
            if (index < items.lastIndex) HorizontalDivider(thickness = 1.dp, color = NmColor.Neutral.C100)
        }
    }
}
