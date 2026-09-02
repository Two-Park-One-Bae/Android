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
import androidx.compose.ui.res.painterResource
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.home.FeaturePreparingScreen
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
                NmTab.Home -> HomeScreen(
                    onPillClick = { selected = NmTab.Pill },
                    onTimerClick = { selected = NmTab.Timer },
                    onActiveTimerClick = { selected = NmTab.Timer }
                )

                // 알약 식별 화면은 NM-394 가 이 자리를 대체한다.
                NmTab.Pill -> FeaturePreparingScreen(
                    title = "알약 식별",
                    description = "사진으로 알약을 찾아주는 기능을 준비하고 있어요.\n" +
                        "테스트 기간 중 업데이트로 제공될 예정입니다.",
                    icon = painterResource(DsR.drawable.nm_ic_pill),
                    iconBackground = NmColor.Primary.C50,
                    iconTint = NmColor.Primary.C500
                )

                NmTab.Timer -> FeaturePreparingScreen(
                    title = "처치 타이머",
                    description = "여러 처치 시간을 한 번에 관리하는 타이머를 준비하고 있어요.\n" +
                        "테스트 기간 중 업데이트로 제공될 예정입니다.",
                    icon = painterResource(DsR.drawable.nm_ic_timer),
                    iconBackground = NmColor.Secondary.C50,
                    iconTint = NmColor.Secondary.C500
                )

                // 설정의 내용(로그아웃·탈퇴, 타이머 울림 방식)은 인증·타이머 스펙에 딸려 있다.
                // 그 기능이 붙기 전까지 임의로 채우지 않는다.
                NmTab.Settings -> FeaturePreparingScreen(
                    title = "설정",
                    description = "계정과 알림 설정을 준비하고 있어요.\n" +
                        "테스트 기간 중 업데이트로 제공될 예정입니다.",
                    icon = painterResource(DsR.drawable.nm_ic_settings),
                    iconBackground = NmColor.Neutral.C100,
                    iconTint = NmColor.Neutral.C500
                )
            }
        }
        NmBottomBar(selected = selected, onSelect = { selected = it })
    }
}
