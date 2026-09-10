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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

    /**
     * 만료를 손목에 알리려면 알림 권한이 필요하다(Android 13+).
     *
     * ⚠️ **거부해도 앱을 막지 않는다.** 목록을 보고 시작하는 것까지는 권한 없이 되고,
     * 알림 권한 판정은 폰 쪽 몫이다(스냅샷의 `alarmAuthorized`). 여기서 잠그면 워치가
     * 폰보다 엄격해져 "폰에서는 되는데 워치에서는 안 된다"가 된다.
     */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
