package app.nursemate.pill

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 외형 메뉴를 **화면 폭으로** 띄운다 — 정본은 좌우 16 을 남기고 칩 줄 바닥에서 7 아래다.
 *
 * ## 왜 카드 안에 깔지 않는가
 * 색 메뉴는 네 줄이라 351 이다. 카드 안에 깔면 카드가 그만큼 길어져 **후보 목록이 화면 밖으로
 * 밀려난다** — 조건을 고치는 내내 후보가 어떻게 바뀌는지를 못 보게 된다. 정본이 띄운 이유다.
 *
 * 앵커의 창 기준 좌표를 그대로 받아 쓰므로 카드가 어디 있든 같은 자리에 선다.
 */
@Composable
internal fun AttributeMenuPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val side = with(density) { MenuSideMargin.roundToPx() }
    val gap = with(density) { MenuAnchorGap.roundToPx() }
    // ⚠️ 팝업은 **열릴 때 잰 창 높이**로 자리를 잡고 그대로 남는다. 각인을 치다 칩을 누르면
    //    키보드가 떠 있는 동안의 짧은 창에 맞춰 위로 붙고, 키보드가 내려가도 그 자리다 —
    //    메뉴가 속성 카드를 덮어 어느 칩을 열었는지조차 안 보인다. 키보드가 오르내리면
    //    팝업을 다시 띄워 자리를 새로 잡는다.
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    val provider = remember(side, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                // 아래로 넘치면 위로 붙인다 — 긴 색 메뉴가 작은 기기에서 잘리지 않게.
                val below = anchorBounds.bottom + gap
                val y = if (below + popupContentSize.height <= windowSize.height) {
                    below
                } else {
                    (windowSize.height - popupContentSize.height - gap).coerceAtLeast(0)
                }
                return IntOffset(x = side, y = y)
            }
        }
    }
    key(imeOpen) {
        AttributeMenuWindow(provider = provider, onDismiss = onDismiss, content = content)
    }
}

@Composable
private fun AttributeMenuWindow(
    provider: PopupPositionProvider,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        // ⚠️ 포커스를 주지 않는다. 포커스를 가진 팝업은 바깥 탭을 **자기가 먹고** 닫기만 해서,
        //    메뉴가 열린 채 다른 칩을 누르면 메뉴만 닫히고 그 칩은 안 열린다 — 두 번 눌러야 했다.
        //    포커스를 안 가지면 그 탭이 아래 칩까지 가서 색 → 모양 → 제형을 한 번씩만 눌러도 넘어간다.
        properties = PopupProperties(focusable = false)
    ) {
        // 포커스를 안 가지면 뒤로가기가 팝업으로 안 온다. 안 받아 두면 메뉴를 열어 둔 채
        // 뒤로가기를 눌렀을 때 화면이 통째로 닫힌다.
        BackHandler(onBack = onDismiss)
        Box(modifier = Modifier.width(LocalConfiguration.current.screenWidthDp.dp - MenuSideMargin * 2)) {
            content()
        }
    }
}

/** 정본 메뉴는 화면 좌우 16 을 남기고 칩 줄 바닥에서 7 아래에 선다. */
private val MenuSideMargin = 16.dp
private val MenuAnchorGap = 7.dp
