package app.nursemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.home.HomeScreen
import app.nursemate.home.NmBottomBar
import app.nursemate.home.NmTab
import app.nursemate.ui.SystemBarIcons
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템 바 뒤까지 그린다. 없으면 상·하단에 회색 띠가 남는다.
        enableEdgeToEdge()
        setContent { NurseMateTheme { NurseMateApp() } }
    }
}

/**
 * 앱 셸.
 *
 * 지금은 탭 상태만 들고 있다. 로그인·동의 온보딩이 붙으면 세션에 따라 화면을 가르는
 * 네비게이션이 이 자리를 대신한다.
 *
 * ⚠️ **탭바를 모든 화면에 깔지 않는다.** 디자인에서 탭바를 가진 화면은 홈·타이머·설정뿐이고
 * 전체화면 플로우(알약 촬영)는 아니다. 지금은 탭 루트만 있어 문제가 없지만, 전체화면
 * 화면이 붙을 때 이 구조를 바꿔야 한다.
 */
@Composable
private fun NurseMateApp() {
    val colors = NmTheme.semanticColors
    var selected by remember { mutableStateOf(NmTab.Home) }
    SystemBarIcons(darkIcons = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgApp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            when (selected) {
                // 알약·타이머·설정 화면은 뒤 커밋에서 붙는다.
                else -> HomeScreen(
                    onPillClick = { selected = NmTab.Pill },
                    onTimerClick = { selected = NmTab.Timer },
                    onActiveTimerClick = { selected = NmTab.Timer }
                )
            }
        }
        NmBottomBar(selected = selected, onSelect = { selected = it })
    }
}
