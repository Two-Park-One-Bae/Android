package app.nursemate.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.home.NmBottomBar
import app.nursemate.home.NmTab
import app.nursemate.ui.SystemBarIcons

/**
 * 탭 루트 화면을 하단 탭바로 감싼다.
 *
 * ## 왜 앱 셸이 아니라 화면마다 두는가
 * 디자인에서 **탭바를 가진 화면은 홈 · 타이머 리스트 · 설정뿐이다.** 알약 식별 플로우는
 * 촬영부터 결과까지 전부 전체화면이다(`spec/design/DESIGN.pen` 의 ①~⑦ 프레임에 Tab Bar 가 없다).
 *
 * 앱 셸에 탭바를 깔면 알약 화면까지 탭바가 따라붙어 **카메라 뷰파인더가 눌리고 잘린다.**
 * 그래서 셸은 화면만 그리고, 탭바가 필요한 루트만 이걸로 감싼다.
 *
 * 상단은 상태바 인셋만 피한다. 하단 인셋은 [NmBottomBar] 가 직접 흡수한다 —
 * 바 배경이 제스처 영역까지 이어져야 잘린 것처럼 보이지 않는다.
 *
 * @param overlay 화면과 **탭바 위**에 덮는 것(확인 모달 등). [content] 안에서 그리면
 *                탭바를 덮지 못해, 모달을 띄운 채 탭을 눌러 빠져나갈 수 있다.
 *                정본도 dim 이 탭바까지 덮는다.
 */
@Composable
fun NmTabScaffold(
    selected: NmTab,
    onSelect: (NmTab) -> Unit,
    modifier: Modifier = Modifier,
    overlay: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = NmTheme.semanticColors
    // 밝은 bg-app 위라 시스템 바 아이콘은 어둡게.
    SystemBarIcons(darkIcons = true)

    Box(modifier = modifier.fillMaxSize().background(colors.bgApp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                content()
            }
            NmBottomBar(selected = selected, onSelect = onSelect)
        }
        overlay?.invoke()
    }
}
