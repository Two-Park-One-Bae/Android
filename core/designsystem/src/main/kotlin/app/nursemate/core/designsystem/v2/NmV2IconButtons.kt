package app.nursemate.core.designsystem.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme

/*
 * V2.1.0 아이콘 버튼 — 띄운 버튼(유리) · 아이콘 버튼(볼록) · 사진 위 아이콘 버튼(유리).
 */

/**
 * `Button Floating` — 56 동그라미, 파랑으로 물든 유리. 알약 탭 촬영 · 타이머 추가 자리.
 * 흐림은 그리지 않는다(뒤가 흰 목록이라 차이가 없다 — NM-580 결정 4).
 */
@Composable
fun NmV2ButtonFloating(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .v2OuterShadows(listOf(V2Shadow(NmV2Color.Raised.primaryShadow, y = 8.dp, blur = 20.dp)), CircleShape)
            .clip(CircleShape)
            .drawWithCache {
                val tint = Brush.verticalGradient(
                    listOf(NmV2Color.Glass.tintPrimaryTop, NmV2Color.Glass.tintPrimaryBottom)
                )
                onDrawBehind {
                    // 물든 면이 85~92% 라 아래 바깥 그림자가 비친다 — 흰 바탕을 먼저 깐다(NmV2Surface 와 같은 이유).
                    drawRect(NmColor.Neutral.C0)
                    drawRect(tint)
                }
            }
            .v2InnerShadows(listOf(V2Shadow(NmV2Color.Raised.highlight, y = 1.dp, blur = 1.dp)), CircleShape)
            .border(NmV2Glass.strokeWidthSmall, GlassStrokeBrush, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = NmColor.Neutral.C0,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** `Icon Button` — 40 동그라미 볼록(보조). */
@Composable
fun NmV2IconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val look = NmV2ButtonKind.Secondary.look(pressed = pressed, enabled = enabled)
    Box(
        modifier = modifier
            .size(40.dp)
            .v2Raised(look, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = look.content,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * `Icon Button Glass` — 사진 위 28 동그라미 유리(후보 사진의 `크게 보기`).
 * 보이는 크기는 28 이지만 누르는 영역은 48 이다.
 */
@Composable
fun NmV2IconButtonGlass(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(TouchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .v2OuterShadows(
                    listOf(V2Shadow(NmV2Color.Shadow.small, NmV2Shadow.smallY, NmV2Shadow.smallBlur)),
                    CircleShape
                )
                .clip(CircleShape)
                .drawWithCache {
                    val sheen = Brush.radialGradient(
                        listOf(NmV2Color.Glass.sheen, Color.Transparent),
                        center = Offset(size.width * 0.3f, size.height * 0.2f),
                        radius = size.width * 0.35f
                    )
                    // 사진 위에 놓이는 유리라 일부러 비친다. 흰 바탕을 깔지 않는다.
                    onDrawBehind {
                        drawRect(NmV2Color.Glass.strokeClear)
                        drawRect(sheen)
                    }
                }
                .v2InnerShadows(listOf(V2Shadow(NmV2Color.Glass.highlight, y = 1.dp, blur = 1.dp)), CircleShape)
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(NmV2Color.Glass.strokeWhite, NmV2Color.Glass.strokeClear, NmV2Color.Glass.highlight)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon),
                contentDescription = contentDescription,
                tint = NmTheme.semanticColors.textPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** 누르는 영역 최소 크기(NM-612 — 칩 30pt 도 누르는 영역은 48dp). */
internal val TouchTarget: Dp = 48.dp
