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
import androidx.lifecycle.lifecycleScope
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.navigation.NurseMateApp
import app.nursemate.timer.alarm.TimerAlarmIntents
import app.nursemate.timer.widget.PresetWidgetLaunch
import app.nursemate.timer.widget.PresetWidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

    /**
     * 위젯에서 시작하려던 프리셋 — 권한이 없어 앱으로 넘어온 경우다(spec §위젯).
     *
     * 여기까지 들고 오지 않으면 앱은 타이머 탭만 열고 만다. 사용자는 왜 열렸는지 모른 채
     * 위젯으로 돌아가 다시 눌러야 한다.
     */
    private val startPresetId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTimerTab.value = intent.wantsTimerTab()
        // ⚠️ 위젯 요청은 **새로 열렸을 때만** 읽는다. onStartPresetHandled 가 비우는 것은
        // 상태뿐이고 인텐트 extra 는 남아서, 액티비티가 다시 만들어질 때 여기서 또 읽으면
        // **같은 타이머가 하나 더 생긴다**(화면 회전, 프로세스 사망 후 복귀).
        // extra 를 지우는 것으로는 못 막는다 — 사망 복귀 때 인텐트가 새로 복원된다.
        //
        // 바로 윗줄의 탭 이동은 여러 번 해도 결과가 같아 이 가드를 두지 않는다. 오히려
        // 시작 도중 재생성되면 요청이 사라져, 알람을 눌렀는데 홈에 남는다.
        if (savedInstanceState == null) {
            startPresetId.value = intent.widgetPresetId()
        }

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
                    onTimerTabOpened = { openTimerTab.value = false },
                    startPresetId = startPresetId.value,
                    onStartPresetHandled = { startPresetId.value = null }
                )
            }
        }
    }

    /**
     * 앱을 떠날 때 위젯을 한 번 맞춘다.
     *
     * 위젯은 **그릴 때 권한을 판정해** 탭 동작을 정한다(콜백으로 바로 시작 / 앱 열기).
     * 그런데 권한을 받는 자리는 앱이라, 받고 나서 위젯을 다시 그리지 않으면 위젯은 계속
     * "권한 없음"인 줄 알고 앱만 연다. 앱을 떠나는 순간이 그 판정을 새로 하기에 가장 정확한
     * 지점이다 — 곧 위젯이 보이는 화면으로 나가는 참이다.
     */
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            PresetWidgetRefresher.refreshAll(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.wantsTimerTab()) openTimerTab.value = true
        intent.widgetPresetId()?.let { startPresetId.value = it }
    }
}

private fun Intent.wantsTimerTab(): Boolean = getBooleanExtra(TimerAlarmIntents.EXTRA_OPEN_TIMER, false)

private fun Intent.widgetPresetId(): String? = getStringExtra(PresetWidgetLaunch.EXTRA_PRESET_ID)
