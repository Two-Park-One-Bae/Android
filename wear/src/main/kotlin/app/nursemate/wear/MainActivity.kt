package app.nursemate.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.MaterialTheme
import app.nursemate.wear.ui.NurseMateWearApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 허용하든 거부하든 폰에 알린다 — 폰은 이 값으로 안내를 띄울지 정한다.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()
        // 타일의 [더 보기] 는 프리셋 페이지를 곧장 연다 — 타일에 다 못 담은 나머지를
        // 보러 오는 길이라 활성 페이지에서 한 번 더 쓸게 하면 헛걸음이다.
        val openPresets = intent?.getBooleanExtra(EXTRA_OPEN_PRESETS, false) == true
        setContent {
            MaterialTheme { NurseMateWearApp(openPresets = openPresets) }
        }
    }

    companion object {
        /** 타일에서 [더 보기] 를 눌렀을 때. 프리셋 페이지로 연다. */
        const val EXTRA_OPEN_PRESETS = "open_presets"
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    /**
     * 만료를 손목에 알리려면 알림 권한이 필요하다(Android 13+).
     *
     * ⚠️ **거부해도 앱을 막지 않는다.** 권한이 없어도 만료는 알린다 — 알람 화면은
     * 포그라운드 서비스가 직접 띄우고, 진동은 `VIBRATE` 만 있으면 된다. 없어지는 것은
     * 알림 카드와 진행 중 표시뿐이라, 그걸 이유로 기능을 잠글 일이 아니다.
     */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!canPostNotifications()) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
