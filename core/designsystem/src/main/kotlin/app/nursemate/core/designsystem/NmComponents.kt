package app.nursemate.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 목록 행 — 디자인 `Component / List Row`.
 *
 * `surface` / radius 14 / 패딩 16×14 / 아이콘 박스 40(r10)+아이콘 20 /
 * 제목 14·600 / 부제 12·400 `text-tertiary` / chevron 18.
 *
 * ⚠️ **홈의 기능 카드는 이것이 아니다** — 더 크고 캡션 자리가 따로 있다. [NmFeatureCard] 를 쓸 것.
 */
@Composable
fun NmListRow(
    icon: Painter,
    title: String,
    subtitle: String,
    iconBackground: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            // ⚠️ clip 이 clickable 보다 **앞**에 와야 한다. 없으면 눌렀을 때 리플이 둥근 모서리를
            //    무시하고 사각형으로 번져 나간다 — background(shape) 는 배경만 깎을 뿐
            //    자식 인디케이션까지 잘라 주지 않는다.
            .clip(shape)
            .background(colors.surface, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = NmSpacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBackground, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = RowTitle, color = colors.textPrimary)
            Text(text = subtitle, style = RowSubtitle, color = colors.textTertiary)
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_chevron_right),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 홈의 기능 카드 — 디자인 `홈 화면 / Feature Cards`.
 *
 * `surface` / radius 20 / 패딩 20 / gap 16 / 아이콘 박스 56(r16)+아이콘 28 /
 * 제목 16·600 / 설명 13·400 `text-secondary` / 캡션 12·600 / chevron 18.
 *
 * @param caption 제목·설명 아래 보조 문구(예: "오늘 남은 횟수 12/15"). null이면 자리를 차지하지 않는다.
 */
@Composable
fun NmFeatureCard(
    icon: Painter,
    title: String,
    description: String,
    iconBackground: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionColor: Color = iconTint,
    onClick: (() -> Unit)? = null
) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            // ⚠️ clip 이 clickable 보다 **앞**에 와야 한다. 없으면 눌렀을 때 리플이 둥근 모서리를
            //    무시하고 사각형으로 번져 나간다 — background(shape) 는 배경만 깎을 뿐
            //    자식 인디케이션까지 잘라 주지 않는다.
            .clip(shape)
            .background(colors.surface, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            // 20dp — spacing 토큰(16/24) 사이 값이라 명시한다.
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NmSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(iconBackground, RoundedCornerShape(NmRadius.lg)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NmSpacing.xs)
        ) {
            Text(text = title, style = CardTitle, color = colors.textPrimary)
            Text(text = description, style = CardDescription, color = colors.textSecondary)
            if (caption != null) {
                Text(text = caption, style = CardCaption, color = captionColor)
            }
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_chevron_right),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 상태를 짧게 보여주는 칩 — 디자인 `Component / Chip`.
 *
 * radius 20 / 패딩 14×8 / gap 6 / 아이콘 14 / 라벨 13·500 / **테두리 1dp**.
 * 기본은 primary 계열이고, 홈의 "활성 타이머"처럼 secondary로 쓰려면 색 셋을 넘긴다.
 *
 * > iOS `DSChip`은 기본 프리셋이 primary가 아니라 info라 하늘색 대신 파랑으로 나온다.
 * > 정본은 primary이므로 여기서는 primary를 기본값으로 둔다.
 */
@Composable
fun NmChip(
    text: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    contentColor: Color = NmColor.Primary.C600,
    containerColor: Color = NmColor.Primary.C50,
    borderColor: Color = NmColor.Primary.C100,
    accentColor: Color = NmColor.Primary.C500,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            // ⚠️ clip 이 clickable 보다 **앞**에 와야 한다. 없으면 눌렀을 때 리플이 둥근 모서리를
            //    무시하고 사각형으로 번져 나간다 — background(shape) 는 배경만 깎을 뿐
            //    자식 인디케이션까지 잘라 주지 않는다.
            .clip(shape)
            .background(containerColor, shape)
            .border(1.dp, borderColor, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = NmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(text = text, style = ChipLabel, color = contentColor)
    }
}

// 정본 스케일(46/36/28/22/18/16/14/12)에 없는 크기들이다. 화면이 요구하는 값이라
// 컴포넌트에 명시로 둔다 — 토큰을 늘리면 스케일이 흐려진다.
private val RowTitle = NmTypography.body.copy(fontWeight = FontWeight.SemiBold)
private val RowSubtitle = NmTypography.caption.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
private val CardTitle = NmTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
private val CardDescription = NmTypography.body.copy(fontSize = 13.sp, lineHeight = 20.sp)
private val CardCaption = NmTypography.caption.copy(fontWeight = FontWeight.SemiBold)
private val ChipLabel = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
