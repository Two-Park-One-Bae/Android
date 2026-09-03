package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 인식 결과 카드의 ⋮ 메뉴 — 디자인 `⑤ 카드 ⋮ 메뉴 (열림)`.
 *
 * 폭 150 · radius 12 · 항목 [수정] [삭제], 사이에 1px 구분선.
 *
 * ## 스크림이 모달 dim 보다 옅다
 * 정본이 `#0F172A4D`(30%)다. 확인 모달의 `#0F172A99`(60%)와 다른데, 메뉴는 **뒤 내용을
 * 계속 보여주면서** 바깥을 눌러 닫으라는 신호이기 때문이다. 같은 농도로 두면 모달처럼
 * 읽혀서 바깥을 누를 생각을 안 하게 된다.
 *
 * @param topEnd 메뉴의 **우측 상단이 놓일 화면 좌표**(px).
 *               가로는 카드 오른쪽 끝(⋮ 버튼에 맞추면 카드 안쪽 여백만큼 들어가 보인다),
 *               세로는 ⋮ 버튼 바로 아래다(버튼을 가리지 않는다). 계산은 카드가 한다.
 * @param onDismiss 스크림을 눌렀을 때. 메뉴는 선택 없이 닫힐 수 있다.
 */
@Composable
fun PillCardMenu(
    topEnd: IntOffset,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize().background(Scrim).clickable(onClick = onDismiss)) {
        Column(
            modifier = Modifier
                // 우측 상단을 받은 지점에 맞춘다 — 폭만큼 왼쪽으로 민다.
                .offset { IntOffset(x = topEnd.x - with(density) { MenuWidth.roundToPx() }, y = topEnd.y) }
                .width(MenuWidth)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
        ) {
            MenuItem(
                icon = R.drawable.nm_ic_pencil,
                label = "수정",
                tint = colors.textSecondary,
                labelColor = colors.textPrimary,
                onClick = onEdit
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NmColor.Neutral.C100))
            MenuItem(
                icon = R.drawable.nm_ic_trash,
                label = "삭제",
                tint = NmColor.Error.C500,
                labelColor = NmColor.Error.C500,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun MenuItem(icon: Int, label: String, tint: Color, labelColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(text = label, style = MenuLabel, color = labelColor)
    }
}

private val MenuWidth = 150.dp

/** 정본 `#0F172A4D` — 확인 모달(60%)보다 옅다. */
private val Scrim = Color(0x4D0F172A)

private val MenuLabel = NmTypography.body.copy(fontSize = 14.sp)
