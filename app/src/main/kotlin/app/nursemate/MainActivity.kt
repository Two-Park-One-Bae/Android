package app.nursemate

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.navigation.NurseMateApp
import app.nursemate.timer.alarm.TimerAlarmIntents
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 만료 알람에서 들어왔는가 — spec §만료·알람 "알람 확인·탭 후 랜딩 = C1".
     *
     * 인텐트를 그때그때 읽지 않고 상태로 들고 있는 이유는 **앱이 이미 떠 있을 때**다.
     * 그때는 [onNewIntent] 로 오는데, Compose 는 `intent` 를 관찰하지 않아 새 인텐트가
     * 와도 화면이 모른다.
     */
    private val openTimerTab = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTimerTab.value = intent.wantsTimerTab()

        // 시스템 바 뒤까지 그린다. 없으면 상·하단에 회색 띠가 남는다.
        //
        // ⚠️ **인자 없이 부르면 안 된다.** 기본값이 내비게이션 바에 90% 흰색 스크림을 깐다.
        // 밝은 화면에서는 티가 안 나지만, 어두운 화면 위에서는 아래쪽에 흰 띠가 생긴다 —
        // 동의 온보딩의 dim 이 내비바 영역만 안 먹은 것처럼 보였고, 알약 촬영 화면의
        // 뷰파인더에서는 더 크게 드러난다. 양쪽 다 완전 투명으로 둔다.
        //
        // 바 아이콘 색은 여기서 정하지 않는다 — 화면마다 다르므로 SystemBarIcons 가 맡는다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        // Android 10+ 는 투명 내비바에 시스템이 자체 대비 스크림을 넣을 수 있다. 그것도 끈다.
        // ⚠️ 이 속성 자체는 API 29 부터다 — minSdk 가 26 이라 가드 없이 부르면 lint 가
        // NewApi 로 막는다(26~28 단말에서는 그냥 스크림이 남는다, 치명적이지 않다).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            NurseMateTheme {
                NurseMateApp(
                    openTimerTab = openTimerTab.value,
                    onTimerTabOpened = { openTimerTab.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.wantsTimerTab()) openTimerTab.value = true
    }
}

private fun Intent.wantsTimerTab(): Boolean = getBooleanExtra(TimerAlarmIntents.EXTRA_OPEN_TIMER, false)
