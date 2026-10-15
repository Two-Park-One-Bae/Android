package app.nursemate.core.designsystem.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme

/**
 * V2.1.0 버튼 — **볼록** 재질(NM-580 결정 5). 띄운 버튼만 유리다.
 *
 * 위아래 밝기 차이가 거의 없는 면 + 아주 옅은 바깥 그림자 1겹 + 윗면의 가는 빛(안쪽 y1).
 * 상태는 정본 `Button States` 를 따른다.
 *
 * | 상태 | 보조(흰) | 주(파랑) |
 * |---|---|---|
 * | 기본 | 볼록 + `neutral-200` 1 | 볼록 |
 * | 누름 | **평면** `neutral-100` + 테두리 | **평면** `primary-600` |
 * | 비활성 | 평면 `neutral-100` · 글자 `text-tertiary` | 평면 `primary-100` · 글자 흰 |
 *
 * ⚠️ 살구(`Button Caution`)와 빨강(`Button Destructive`)의 누름 · 비활성은 정본에 없다.
 *    주(파랑)와 같은 규칙으로 한 단계 진한 색(`-600`)과 옅은 색으로 이었다.
 */
enum class NmV2ButtonKind {
    Primary,
    Secondary,

    /** 타이머 만료 카드의 `완료` 에만 쓴다(spec design README). */
    Caution,
    Destructive
}

/** 버튼 모서리 12. */
val NmV2ButtonShape = RoundedCornerShape(NmRadius.md)

private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)

@Composable
fun NmV2Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: NmV2ButtonKind = NmV2ButtonKind.Primary,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    shape: Shape = NmV2ButtonShape,
    contentPadding: PaddingValues = ButtonPadding
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val look = kind.look(pressed = pressed, enabled = enabled)
    Row(
        modifier = modifier
            .v2Raised(look, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon !=
            null
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = look.content,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(text, style = NmV2Type.button, color = look.content, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun NmV2ButtonPrimary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    NmV2Button(text, onClick, modifier, NmV2ButtonKind.Primary, enabled)

@Composable
fun NmV2ButtonSecondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    NmV2Button(text, onClick, modifier, NmV2ButtonKind.Secondary, enabled)

/** `Button Caution` — 정본은 체크 아이콘을 앞에 둔다. */
@Composable
fun NmV2ButtonCaution(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    NmV2Button(text, onClick, modifier, NmV2ButtonKind.Caution, enabled, icon = NmSymbols.Check)

@Composable
fun NmV2ButtonDestructive(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    NmV2Button(text, onClick, modifier, NmV2ButtonKind.Destructive, enabled)

/** `Button Text` — 면 없이 글자만. */
@Composable
fun NmV2ButtonText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .clip(NmV2ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = NmV2Type.textButton,
            color = if (enabled) NmColor.Primary.C600 else NmTheme.semanticColors.textTertiary
        )
    }
}

// ── 볼록 면 ─────────────────────────────────────────────────────────

/** 한 상태의 볼록 면 모양. 평면(누름 · 비활성)이면 [top] == [bottom] 이고 그림자가 없다. */
internal data class RaisedLook(
    val top: Color,
    val bottom: Color,
    val stroke: Color?,
    val shadow: Color?,
    val highlight: Color?,
    val content: Color
)

@Composable
internal fun NmV2ButtonKind.look(pressed: Boolean, enabled: Boolean): RaisedLook {
    val colors = NmTheme.semanticColors
    return when (this) {
        NmV2ButtonKind.Secondary -> when {
            !enabled -> flat(NmColor.Neutral.C100, colors.textTertiary)

            pressed -> flat(NmColor.Neutral.C100, colors.textPrimary, stroke = NmColor.Neutral.C200)

            else -> RaisedLook(
                top = colors.surface,
                bottom = NmV2Color.Raised.neutralBottom,
                stroke = NmColor.Neutral.C200,
                shadow = NmV2Color.Shadow.listContact,
                highlight = NmV2Color.Glass.innerEdge,
                content = colors.textPrimary
            )
        }

        NmV2ButtonKind.Primary -> tinted(
            pressed,
            enabled,
            NmV2Color.Raised.primaryTop,
            NmV2Color.Raised.primaryBottom,
            NmV2Color.Raised.primaryShadow,
            pressedColor = NmColor.Primary.C600,
            disabledColor = NmColor.Primary.C100
        )

        NmV2ButtonKind.Caution -> tinted(
            pressed,
            enabled,
            NmV2Color.Raised.cautionTop,
            NmV2Color.Raised.cautionBottom,
            NmV2Color.Raised.cautionShadow,
            pressedColor = NmV2Color.Caution.C600,
            disabledColor = NmV2Color.Caution.C300
        )

        NmV2ButtonKind.Destructive -> tinted(
            pressed,
            enabled,
            NmV2Color.Raised.errorTop,
            NmV2Color.Raised.errorBottom,
            NmV2Color.Raised.errorShadow,
            pressedColor = NmColor.Error.C600,
            disabledColor = NmColor.Error.C100
        )
    }
}

private fun flat(fill: Color, content: Color, stroke: Color? = null) =
    RaisedLook(fill, fill, stroke, shadow = null, highlight = null, content = content)

@Suppress("LongParameterList")
private fun tinted(
    pressed: Boolean,
    enabled: Boolean,
    top: Color,
    bottom: Color,
    shadow: Color,
    pressedColor: Color,
    disabledColor: Color
) = when {
    !enabled -> flat(disabledColor, NmColor.Neutral.C0)

    pressed -> flat(pressedColor, NmColor.Neutral.C0)

    else -> RaisedLook(
        top,
        bottom,
        stroke = null,
        shadow = shadow,
        highlight = NmV2Color.Raised.highlight,
        content = NmColor.Neutral.C0
    )
}

/** 볼록 면을 그린다. 내용은 이 면 위에 놓인다 — 윗면 빛은 1px 선이라 글자를 가리지 않는다. */
internal fun Modifier.v2Raised(look: RaisedLook, shape: Shape): Modifier {
    var m = this
    if (look.shadow != null) {
        m = m.v2OuterShadows(listOf(V2Shadow(look.shadow, NmV2Shadow.contactY, NmV2Shadow.contactBlur)), shape)
    }
    m = m.clip(shape).drawBehind {
        if (look.top ==
            look.bottom
        ) {
            drawRect(look.top)
        } else {
            drawRect(Brush.verticalGradient(listOf(look.top, look.bottom)))
        }
    }
    if (look.highlight != null) m = m.v2InnerShadows(listOf(V2Shadow(look.highlight, y = 1.dp, blur = 0.dp)), shape)
    if (look.stroke != null) m = m.border(1.dp, look.stroke, shape)
    return m
}

/** 부모 너비를 채우는 버튼 줄 사이 간격. 모달 · 카드 버튼 줄이 쓴다. */
internal val ButtonRowGap = 8.dp
