package app.nursemate.core.designsystem.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme

/*
 * V2.1.0 바 — 내비게이션 바 · 탭 바 · 하단 바.
 */

/**
 * `Nav Bar` — 높이 56, 흰 바탕, 가운데 제목. [onBack] 이 없으면 뒤로 자리를 비운다.
 * [trailing] 은 오른쪽 26 자리(더보기 등)다.
 */
@Composable
fun NmV2NavBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "뒤로",
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.surface)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(NavSlot), contentAlignment = Alignment.Center) {
            if (onBack != null) {
                Box(
                    Modifier
                        // 칸은 26 이지만 누르는 영역은 48 — `size` 는 부모 26 에 눌리므로 `requiredSize` 로 넘친다.
                        .requiredSize(TouchTarget)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(NmSymbols.ChevronLeft),
                        backLabel,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(NavSlot)
                    )
                }
            }
        }
        Text(
            title,
            style = NmV2Type.navTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(NavSlot), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

private val NavSlot = 26.dp

/** 탭 하나. */
data class NmV2Tab(val label: String, @param:DrawableRes val icon: Int)

/**
 * `Tab Bar` — 화면 아래에 띄운 캡슐. 높이 62, 안쪽 여백 5.
 *
 * 면은 `glass-fallback`(NM-608). 선택한 탭은 안쪽에 `glass-inner-glow` 캡슐 + 흰 테두리를 두고
 * 아이콘 `primary-500` · 글자 `primary-600` 600 이다. 하단 인셋은 이 컴포넌트를 감싸는 쪽이 흡수한다.
 */
@Composable
fun NmV2TabBar(tabs: List<NmV2Tab>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    NmV2Surface(NmV2SurfaceStyle.Floating, modifier.fillMaxWidth().height(62.dp)) {
        Row(Modifier.fillMaxWidth().padding(5.dp)) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier
                                    .background(NmV2Color.Glass.innerGlow, CircleShape)
                                    .border(1.dp, NmV2Color.Glass.strokeWhite, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Tab, onClick = { onSelect(index) }),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(tab.icon),
                        contentDescription = null,
                        tint = if (selected) NmColor.Primary.C500 else colors.textTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        tab.label,
                        style = if (selected) NmV2Type.tabSelected else NmV2Type.tab,
                        color = if (selected) NmColor.Primary.C600 else colors.textTertiary
                    )
                }
            }
        }
    }
}

/**
 * `Bottom Bar` — **판 없이** 버튼 · 면책 고지만 탭 바 위에 띄운다(spec design README).
 * 여백은 위 10 · 좌우 20 · 아래 6. 주 버튼은 캡슐([NmV2BottomBarButton]).
 */
@Composable
fun NmV2BottomBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * 하단 바의 버튼 — 캡슐 · 위아래 여백 16. 보통 주 버튼 하나지만, 식별 결과처럼 `공유`(보조) · `완료`(주)를
 * 나란히 둘 때는 [kind] 로 보조를 고른다.
 */
@Composable
fun NmV2BottomBarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: NmV2ButtonKind = NmV2ButtonKind.Primary
) {
    NmV2Button(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        kind = kind,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    )
}
