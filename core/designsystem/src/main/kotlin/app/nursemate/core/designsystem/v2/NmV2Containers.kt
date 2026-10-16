package app.nursemate.core.designsystem.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.R

/**
 * `Card` — 유리 카드에 제목 · 본문 · 버튼 줄. 여백 20, 간격 12.
 * 모양이 다른 카드(홈 기능 카드 · 결과 카드 등)는 화면 티켓이 [NmV2Surface] 로 직접 짠다.
 */
@Composable
fun NmV2Card(
    modifier: Modifier = Modifier,
    style: NmV2SurfaceStyle = NmV2SurfaceStyle.Card,
    content: @Composable ColumnScope.() -> Unit
) {
    NmV2Surface(style = style, modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** 카드 제목 — 16/600. */
@Composable
fun NmV2CardTitle(text: String) = Text(text, style = NmV2Type.button, color = NmTheme.semanticColors.textPrimary)

/** 카드 본문 — 13/400. */
@Composable
fun NmV2CardBody(text: String) = Text(text, style = NmV2Type.cardBody, color = NmTheme.semanticColors.textSecondary)

/** 카드 · 모달 아래 버튼 줄 — 칸을 똑같이 나눈다. */
@Composable
fun NmV2ButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonRowGap), content = content)
}

/** `List Row` — 목록 카드 면에 아이콘 · 보조 설명 · 제목 · 꺾쇠. */
@Composable
fun NmV2ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = NmColor.Neutral.C700,
    titleColor: Color = NmTheme.semanticColors.textPrimary
) {
    val colors = NmTheme.semanticColors
    NmV2Surface(
        style = NmV2SurfaceStyle.ListCard,
        modifier = modifier.fillMaxWidth()
    ) {
        // 누름 영역은 면 안쪽에 건다 — 바깥 Modifier 에 clip 을 걸면 목록 그림자까지 잘린다.
        Row(
            Modifier
                .clip(NmV2CardShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon !=
                null
            ) {
                Icon(painterResource(icon), null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (subtitle != null) Text(subtitle, style = NmV2Type.rowSubtitle, color = colors.textSecondary)
                Text(
                    title,
                    style = NmV2Type.rowTitle,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painterResource(NmSymbols.ChevronRight),
                null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * `Preset Row` — 볼록 행. 누르면 바로 시작한다.
 *
 * ⚠️ 정본의 행 안 태그는 모서리 6 으로 적혀 있는데 `Tag` 컴포넌트는 8(`radius-sm`)이다.
 *    NM-580 이 「6 은 8 로 맞춘다」고 정했으므로 [NmV2Tag] 를 그대로 쓴다.
 */
@Composable
fun NmV2PresetRow(
    label: String,
    tag: String,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tagTone: NmV2TagTone = NmV2TagTone.Info
) {
    val colors = NmTheme.semanticColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(NmRadius.md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .v2Raised(NmV2ButtonKind.Secondary.look(pressed = pressed, enabled = true), shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = NmV2Type.rowTitle,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        NmV2Tag(tag, tone = tagTone)
        Spacer(Modifier.weight(1f))
        Text(duration, style = NmV2Type.chip, color = colors.textSecondary)
        Spacer(Modifier.width(2.dp))
        Icon(painterResource(NmSymbols.PlayCircle), null, tint = NmColor.Primary.C500, modifier = Modifier.size(28.dp))
    }
}

/**
 * `Modal` — 유리 판(`glass-fallback`) + 볼록 버튼. 너비 300, 모서리 24, 여백 20, 간격 16.
 *
 * [dismissText] 가 없으면 버튼이 하나다(확인만 있는 안내).
 */
@Composable
fun NmV2Modal(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    message: String? = null,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    confirmKind: NmV2ButtonKind = NmV2ButtonKind.Primary
) {
    // 창을 판 크기로 두면 판 그림자(y14 · 흐림34)가 창 경계에서 잘린다. 기본 폭을 끄고 그림자 자리만큼 여백을 둔다.
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(ModalShadowRoom)) {
            NmV2ModalPanel(title, confirmText, onConfirm, message, dismissText, onDismiss, confirmKind)
        }
    }
}

/** 모달 판 그림자가 들어갈 창 여백 — 흐림 34 + y 14. */
private val ModalShadowRoom = 48.dp

/** [NmV2Modal] 의 판만. 미리보기 · 캡처용으로 따로 둔다. */
@Composable
fun NmV2ModalPanel(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    message: String? = null,
    dismissText: String? = null,
    onDismiss: () -> Unit = {},
    confirmKind: NmV2ButtonKind = NmV2ButtonKind.Primary,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(NmRadius.xl)
    Column(
        modifier = modifier
            .width(300.dp)
            .v2OuterShadows(ShadowCard, shape)
            .clip(shape)
            .background(NmColor.Neutral.C0)
            .background(NmV2Color.Glass.fallback)
            .v2InnerShadows(listOf(V2Shadow(NmV2Color.Glass.innerEdge, y = 1.dp, blur = 1.dp)), shape)
            .border(NmV2Glass.strokeWidthSmall, GlassStrokeBrush, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 정본 「Texts」 묶음 — 제목과 본문 사이는 6 이다(판 간격 16 은 글 묶음과 버튼 줄 사이).
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = NmV2Type.button, color = colors.textPrimary, textAlign = TextAlign.Center)
            if (message != null) {
                Text(message, style = NmV2Type.cardBody, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
        }
        NmV2ButtonRow {
            if (dismissText != null) {
                NmV2Button(dismissText, onDismiss, Modifier.weight(1f), NmV2ButtonKind.Secondary)
            }
            NmV2Button(confirmText, onConfirm, Modifier.weight(1f), confirmKind)
        }
    }
}

/** `Wordmark` — 글자 로고 152×26. 흰 바탕 위에만 쓴다(글자 속을 흰 면으로 덮어 그렸다). */
@Composable
fun NmV2Wordmark(modifier: Modifier = Modifier, contentDescription: String = "NurseMate") {
    Image(painterResource(R.drawable.nm_wordmark), contentDescription = contentDescription, modifier = modifier)
}
