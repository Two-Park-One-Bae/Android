package app.nursemate.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 하단 탭바 — 디자인 `홈 화면 / Tab Bar`.
 *
 * 폭을 꽉 채운 바다. `surface` 바탕에 상단 구분선, 위 패딩 12, 4등분.
 * **선택 표시는 색뿐이다** — 배경 알약이나 인디케이터를 넣지 않는다.
 *
 * ## 높이를 83dp로 고정하지 않는 이유
 * 정본의 83은 **iOS 값**이고 홈 인디케이터 34pt를 포함한다. Android는 제스처 내비게이션이냐
 * 3버튼이냐에 따라 하단 인셋이 기기마다 달라서, 83을 고정하면 어떤 기기에서는 뜨고
 * 어떤 기기에서는 잘린다. **콘텐츠 49dp + `navigationBars` 인셋**으로 잡아 같은 의도를 낸다.
 */
@Composable
fun NmBottomBar(selected: NmTab, onSelect: (NmTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 12.dp)
                .height(ContentHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            NmTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabItem(tab: NmTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    val tint = if (selected) NmColor.Primary.C500 else colors.textTertiary
    Column(
        modifier = modifier.clickable(
            // 꽉 찬 바에서 탭마다 물결이 퍼지면 디자인에 없는 배경이 생긴다.
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(tab.iconRes),
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(text = tab.label, style = TabLabel, color = tint)
    }
}

/** 아이콘 24 + gap 4 + 라벨 한 줄. 하단 인셋은 바깥에서 더한다. */
private val ContentHeight = 49.dp

/** 정본 10·400. 스케일에 없는 크기라 명시한다. */
private val TabLabel = NmTypography.caption.copy(
    fontSize = 10.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.sp
)
