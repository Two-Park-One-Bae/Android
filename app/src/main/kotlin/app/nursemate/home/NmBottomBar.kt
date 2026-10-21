package app.nursemate.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.v2.NmV2Tab
import app.nursemate.core.designsystem.v2.NmV2TabBar

/**
 * 하단 탭 바 — V2.1.0 「Tab Bar」. 화면 아래에 띄운 캡슐이다(NM-610).
 *
 * 정본 홈 프레임은 탭 바 둘레를 위 6 · 좌우 20 · 아래 22 로 둔다. 아래 22 는 **iOS 홈 인디케이터**
 * 자리를 포함한 값이라 Android 는 `navigationBars` 인셋 위에 8 만 더한다 — 제스처 · 3버튼에 따라
 * 인셋이 기기마다 달라서 22 를 고정하면 어떤 기기에서는 뜨고 어떤 기기에서는 붙는다.
 * 하단 인셋은 지금처럼 이 바가 흡수한다.
 */
@Composable
fun NmBottomBar(selected: NmTab, onSelect: (NmTab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = remember { NmTab.entries.map { NmV2Tab(it.label, it.iconRes) } }
    NmV2TabBar(
        tabs = tabs,
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(NmTab.entries[it]) },
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 8.dp)
    )
}
