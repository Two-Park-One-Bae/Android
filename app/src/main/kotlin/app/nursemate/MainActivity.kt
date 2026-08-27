package app.nursemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.home.FeaturePreparingScreen
import app.nursemate.home.HomeScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 런치 테마(스플래시)에서 앱 테마로 되돌린다 — Android 12+ SplashScreen API 패턴
        setTheme(R.style.Theme_NurseMate)
        super.onCreate(savedInstanceState)
        setContent {
            NurseMateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NurseMateApp()
                }
            }
        }
    }
}

/**
 * 화면 이동. 목적지가 셋뿐이라 Navigation 라이브러리 없이 상태로 처리한다.
 * 기능이 늘어나면 navigation-compose로 교체한다.
 */
private enum class Screen { Home, Pill, Timer }

@Composable
private fun NurseMateApp() {
    var screen by remember { mutableStateOf(Screen.Home) }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (screen) {
        Screen.Home -> HomeScreen(
            onPillClick = { screen = Screen.Pill },
            onTimerClick = { screen = Screen.Timer }
        )

        Screen.Pill -> FeaturePreparingScreen(
            title = "알약 식별",
            description = "알약을 촬영하면 후보 의약품을 찾아주는 기능을 준비하고 있어요.\n" +
                "테스트 기간 중 업데이트로 제공될 예정입니다.",
            icon = painterResource(DsR.drawable.nm_ic_pill),
            iconBackground = NmColor.Primary.C50,
            iconTint = NmColor.Primary.C500,
            onBack = { screen = Screen.Home }
        )

        Screen.Timer -> FeaturePreparingScreen(
            title = "처치 타이머",
            description = "여러 처치 시간을 한 번에 관리하는 타이머를 준비하고 있어요.\n" +
                "테스트 기간 중 업데이트로 제공될 예정입니다.",
            icon = painterResource(DsR.drawable.nm_ic_timer),
            iconBackground = NmColor.Secondary.C50,
            iconTint = NmColor.Secondary.C500,
            onBack = { screen = Screen.Home }
        )
    }
}
