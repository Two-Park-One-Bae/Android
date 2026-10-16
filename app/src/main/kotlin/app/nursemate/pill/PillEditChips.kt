package app.nursemate.pill

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2Color
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.DividingLine

// 속성 카드([PillEditCard])의 부품 — 드롭다운 칩 · 칩 글자 · 마크 메뉴.

/**
 * 마크 유무 메뉴 — `모름 · 없음 · 있음`(정본 ⑦). 칩 바로 아래, 오른쪽 끝을 칩에 맞춰 160 폭으로 뜬다.
 * 손대지 않은 칸은 체크가 없다. 마크는 종류가 296가지라 그림으로 고르지 않고 유무만 묻는다.
 */
@Composable
internal fun MarkMenu(input: FaceInput, onSelect: (Boolean?) -> Unit, onDismiss: () -> Unit) {
    val colors = NmTheme.semanticColors
    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
    // 칸(라벨 + 칩) 바로 아래, 오른쪽 끝을 칩 끝에 맞춘다. 화면 밖으로 넘치면 안으로 당긴다.
    val position = remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ) = IntOffset(
                x = (anchorBounds.right - popupContentSize.width).coerceIn(
                    0,
                    windowSize.width - popupContentSize.width
                ),
                y = (anchorBounds.bottom + gap).coerceAtMost(windowSize.height - popupContentSize.height)
            )
        }
    }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        BackHandler(onBack = onDismiss)
        val shape = RoundedCornerShape(NmRadius.md)
        Column(
            Modifier
                .width(160.dp)
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.border, shape)
                .padding(6.dp)
        ) {
            MarkOptions.forEach { (value, label) ->
                val checked = if (value == null) input.markUnknown else input.hasMark == value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (checked) NmColor.Neutral.C100 else Color.Transparent)
                        .clickable(role = Role.Button) { onSelect(value) }
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(16.dp)) {
                        if (checked) {
                            Icon(
                                painterResource(NmSymbols.Check),
                                null,
                                tint = NmColor.Primary.C600,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        label,
                        style = if (checked) NmV2Type.menu.copy(fontWeight = FontWeight.Bold) else NmV2Type.menu,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

private val MarkOptions = listOf(null to "모름", false to "없음", true to "있음")

/**
 * 드롭다운 칩 — 높이 30 · 글자 13 · 캡슐. iOS 최소 컨트롤 28pt 를 넘기고, 누르는 영역은 48dp 로 잡는다.
 *
 * @param certain 확실한 값 — 파란 테두리(`primary-500` 1.5). 아니면 회색(`neutral-200` 1)
 * @param active 이 칩의 메뉴 · 입력 줄이 열려 있다 — `primary-100` 면 + 테두리 2
 */
@Composable
internal fun EditChip(
    certain: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = CircleShape
    val border = when {
        active -> Modifier.border(2.dp, NmColor.Primary.C500, shape)
        certain -> Modifier.border(1.5.dp, NmColor.Primary.C500, shape)
        else -> Modifier.border(1.dp, NmColor.Neutral.C200, shape)
    }
    val fill = if (active) {
        Modifier.background(NmColor.Primary.C100, shape)
    } else {
        Modifier.background(
            Brush.verticalGradient(listOf(NmTheme.semanticColors.surface, NmV2Color.Raised.neutralBottom)),
            shape
        )
    }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .height(ChipHeight)
            .clip(shape)
            .then(fill)
            .then(border)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
        Icon(
            painterResource(NmSymbols.KeyboardArrowDown),
            contentDescription = null,
            tint = if (certain || active) NmColor.Primary.C500 else NmTheme.semanticColors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 칩 글자의 세 가지 — 확실한 값 · 사용자가 고른 `모름` · 손대지 않은 안내 글자. */
internal enum class ChipTone { Certain, Unknown, Placeholder }

@Composable
internal fun ChipText(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    Text(
        text,
        style = if (tone == ChipTone.Certain) NmV2Type.chipConfirmed else NmV2Type.chip,
        color = when (tone) {
            ChipTone.Certain -> NmColor.Primary.C600
            ChipTone.Unknown -> colors.textSecondary
            ChipTone.Placeholder -> colors.textTertiary
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// ── 칸 글자 — spec §각인 · 구분선 · 마크 — 네 상태 ─────────────────────

internal fun FaceInput.imprintText(): String = when {
    imprint == "" -> "없음"
    imprint != null -> imprint
    imprintUnknown -> "모름"
    else -> "입력"
}

internal fun FaceInput.imprintTone(): ChipTone = tone(imprint != null, imprintUnknown)

internal fun FaceInput.lineText(): String = when (dividingLine) {
    DividingLine.NONE -> "없음"
    DividingLine.PLUS -> "(+)형"
    DividingLine.MINUS -> "(−)형"
    else -> if (lineUnknown) "모름" else "선택"
}

internal fun FaceInput.lineTone(): ChipTone = tone(dividingLine != null, lineUnknown)

internal fun FaceInput.markText(): String = when (hasMark) {
    true -> "있음"
    false -> "없음"
    null -> if (markUnknown) "모름" else "선택"
}

internal fun FaceInput.markTone(): ChipTone = tone(hasMark != null, markUnknown)

private fun tone(certain: Boolean, unknown: Boolean): ChipTone = when {
    certain -> ChipTone.Certain
    unknown -> ChipTone.Unknown
    else -> ChipTone.Placeholder
}

internal val ChipHeight = 30.dp
