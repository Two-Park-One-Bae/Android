package app.nursemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 런치 테마(스플래시)에서 앱 테마로 되돌린다 — Android 12+ SplashScreen API 패턴
        setTheme(R.style.Theme_NurseMate)
        super.onCreate(savedInstanceState)
        setContent {
            // 디자인 정본이 라이트 단일이라 시스템 다크 설정을 따르지 않는다.
            NurseMateTheme(darkTheme = false) {
                NurseMateApp()
            }
        }
    }
}

@Composable
private fun NurseMateApp() {
    var tab by remember { mutableStateOf(NmTab.Home) }
    val colors = NmTheme.semanticColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                NmTab.Home -> HomeScreen(
                    onPillClick = { tab = NmTab.Pill },
                    onTimerClick = { tab = NmTab.Timer },
                    onActiveTimerClick = { tab = NmTab.Timer }
                )

                NmTab.Pill -> FeaturePreparingScreen(
                    title = "알약 식별",
                    description = "알약을 촬영하면 후보 의약품을 찾아주는 기능을 준비하고 있어요.\n" +
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

                // 설정 화면의 내용(로그아웃·탈퇴, 타이머 울림 방식)은 인증·타이머 스펙에 딸려 있다.
                // 그 기능들이 붙기 전까지는 임의로 채우지 않는다 — spec/feature/auth·care-timer 참고.
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
        NmBottomBar(selected = tab, onSelect = { tab = it })
    }
}
