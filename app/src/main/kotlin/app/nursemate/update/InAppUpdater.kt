package app.nursemate.update

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play 인앱 업데이트 — **유연(FLEXIBLE)** 흐름으로 권한다 (NM-626).
 *
 * ## 왜 강제가 아니라 권유인가
 * 강제 업데이트(`min_supported_version_android`)는 앱을 막아 마찰이 크다(2026-10-17 결정 — 2.0.1 로 강제하지 않음).
 * Play 자동 업데이트를 끈 사용자는 옛 버전에 남는데, 그들을 막지 않고 끌어올리는 길이 이것이다.
 *
 * ## 앱이 그리는 화면이 없다
 * 1. 권할 때: **Play 가 띄우는 확인 창**이 나온다(우리 화면 아님).
 * 2. 받는 동안: 뒤에서 받는다 — 사용자는 하던 일을 그대로 한다.
 * 3. 다 받으면: **앱이 뒤로 갈 때** [installIfDownloaded] 로 조용히 설치한다. Play 안내대로 앱이 백그라운드일 때
 *    `completeUpdate` 를 부르면 화면 없이 설치된다.
 *
 * 「다시 시작해 적용」 안내 같은 앱 화면은 정본(spec · 디자인)이 생기면 그때 붙인다 — 정본 없이 만들지 않는다.
 *
 * ## Play 설치본에서만 돈다
 * 사이드로드 · `adb install` 설치본은 Play 가 「업데이트 없음」으로 답하지만, 그 전에 거른다 — 개발 중에 Play 와
 * 통신할 이유가 없다.
 */
@Singleton
class InAppUpdater @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val manager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(context) }
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** 받는 중 · 받은 상태를 로그로만 남긴다. 다 받은 것은 [installIfDownloaded] 가 매번 Play 에 다시 묻는다. */
    private val listener = InstallStateUpdatedListener { state ->
        Log.i(TAG, "설치 상태 ${state.installStatus()} · ${state.bytesDownloaded()}/${state.totalBytesToDownload()}")
    }

    /**
     * 새 버전이 있고 권할 때가 됐으면 Play 확인 창을 띄운다. 앱을 새로 열 때 한 번 부른다.
     * 실패는 삼킨다 — 업데이트를 못 권했다고 앱이 멈출 일은 아니다.
     */
    @Suppress("TooGenericExceptionCaught") // Play Core 는 네트워크 · 서비스 상태마다 다른 예외를 던진다.
    suspend fun offerIfDue(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (!installedFromPlay()) return
        try {
            val info = manager.requestAppUpdateInfo()
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            val now = System.currentTimeMillis()
            if (!UpdateOfferPolicy.shouldOffer(available, now, prefs.getLong(KEY_LAST_OFFERED, 0L))) return
            manager.registerListener(listener)
            // 띄우기 전에 적는다 — 창을 닫든 받든 간격은 같은 규칙으로 센다.
            prefs.edit().putLong(KEY_LAST_OFFERED, now).apply()
            Log.i(TAG, "업데이트 권유 — versionCode ${info.availableVersionCode()}")
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            )
        } catch (e: Exception) {
            Log.w(TAG, "업데이트 확인 실패 — 이번엔 건너뛴다", e)
        }
    }

    /** 앱이 뒤로 갈 때 부른다. 다 받아 둔 업데이트가 있으면 화면 없이 설치한다. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun installIfDownloaded() {
        if (!installedFromPlay()) return
        try {
            if (manager.requestAppUpdateInfo().installStatus() == InstallStatus.DOWNLOADED) {
                Log.i(TAG, "받아 둔 업데이트를 설치한다(백그라운드)")
                manager.completeUpdate()
            }
        } catch (e: Exception) {
            Log.w(TAG, "업데이트 설치 확인 실패", e)
        }
    }

    @Suppress("DEPRECATION", "TooGenericExceptionCaught")
    private fun installedFromPlay(): Boolean = try {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_STORE
    } catch (e: Exception) {
        Log.w(TAG, "설치 출처를 못 읽었다 — 업데이트를 권하지 않는다", e)
        false
    }

    private companion object {
        const val TAG = "NM626"
        const val PREFS = "in_app_update"
        const val KEY_LAST_OFFERED = "last_offered_at"
        const val PLAY_STORE = "com.android.vending"
    }
}
