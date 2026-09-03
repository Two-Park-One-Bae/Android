package app.nursemate.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/**
 * 아이콘만 있는 버튼 — 정본 `Foundation / Components` §Icon Button.
 *
 * 40×40 터치 타깃 안에 22 아이콘을 가운데 둔다. 배경이 없다 — 정본에도 없고,
 * 앱바·툴바 위에 얹히는 용도라 바탕색을 가리면 안 된다.
 *
 * ⚠️ **40dp 를 줄이지 말 것.** 접근성 최소 터치 크기가 48dp 권장인데 정본이 40 이라
 * 그대로 두되, 더 줄이면 누르기 어려워진다.
 *
 * 리플이 사각형으로 번지지 않게 [clip] 을 [clickable] 앞에 둔다.
 */
@Composable
fun NmIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NmTheme.semanticColors.textPrimary,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(SIZE)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(ICON_SIZE)
        )
    }
}

private val SIZE = 40.dp
private val ICON_SIZE = 22.dp
