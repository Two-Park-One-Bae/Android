package app.nursemate.update

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import app.nursemate.core.timer.TimerRepository
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Play 인앱 업데이트 — **유연(FLEXIBLE)** 흐름으로 권한다 (NM-626).
 *
 * ## 왜 강제가 아니라 권유인가
 * 강제 업데이트(`min_supported_version_android`)는 앱을 막아 마찰이 크다(2026-10-17 결정 — 2.0.1 로 강제하지 않음).
 * Play 자동 업데이트를 끈 사용자는 옛 버전에 남는데, 그들을 막지 않고 끌어올리는 길이 이것이다.
 *
 * ## 하는 일은 **앱을 새로 열 때 한 번**뿐이다 ([onColdStart])
 * - 다 받아 둔 업데이트가 있으면 → **설치**(Play 가 잠깐 화면을 띄우고 앱을 다시 연다)
 * - 받는 중이면 → 아무것도 안 한다
 * - 새 버전이 있고 권할 때가 됐으면 → Play 가 띄우는 **확인 창**(우리 화면 아님). 수락하면 뒤에서 받는다
 *
 * ## ⚠️ 설치는 앱을 죽인다 — 그래서 시점을 좁혔다
 * `completeUpdate` 는 프로세스를 끝내고 새 버전으로 다시 연다. 처음엔 「앱이 뒤로 갈 때 조용히 설치」로 짰는데
 * `MainActivity.onStop` 은 **사진 고르기 · 카카오 로그인 · 공유 창 · 타이머 알람 화면**으로 넘어갈 때도 불린다.
 * 그 순간 설치되면 알람이 울리다 끊기고 하던 일이 날아간다(코드 리뷰 지적). 그래서:
 * - 사용자가 **아직 아무것도 안 한** 새 실행 때만 설치한다
 * - **진행 · 일시정지 · 울리는 타이머가 하나라도 있으면** 설치하지 않는다 — 알람을 놓치면 안 된다
 * - 알람 · 위젯으로 열린 실행은 부르는 쪽([app.nursemate.MainActivity])이 거른다
 *
 * 「다시 시작해 적용」 같은 앱 화면은 정본(spec · 디자인)이 생기면 그때 붙인다 — 정본 없이 만들지 않는다.
 *
 * ## Play 설치본에서만 돈다
 * 사이드로드 · `adb install` 설치본은 Play 와 통신할 이유가 없어 먼저 거른다.
 */
@Singleton
class InAppUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val timers: TimerRepository
) {

    private val manager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(context) }
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /**
     * 새로 연 실행에서 한 번 부른다. 실패는 삼킨다 — 업데이트를 못 다뤘다고 앱이 멈출 일은 아니다.
     */
    @Suppress("TooGenericExceptionCaught") // Play Core 는 네트워크 · 서비스 상태마다 다른 예외를 던진다.
    suspend fun onColdStart(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (!installedFromPlay()) return
        try {
            val info = manager.requestAppUpdateInfo()
            when (info.installStatus()) {
                InstallStatus.DOWNLOADED -> installIfSafe()

                // 받는 중 · 설치 중 — 다시 권하면 같은 걸 또 받으라는 창이 뜬다.
                InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING -> Unit

                else -> {
                    val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                    val now = System.currentTimeMillis()
                    if (!UpdateOfferPolicy.shouldOffer(available, now, prefs.getLong(KEY_LAST_OFFERED, 0L))) return
                    val shown = manager.startUpdateFlowForResult(
                        info,
                        launcher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                    Log.i(
                        TAG,
                        "업데이트 권유 — versionCode ${info.availableVersionCode()} · 창 ${if (shown) "띄움" else "못 띄움"}"
                    )
                    // 창이 실제로 떴을 때만 센다 — 못 띄웠는데 3일을 쉬면 아무도 못 본 채 지나간다.
                    if (shown) prefs.edit().putLong(KEY_LAST_OFFERED, now).apply()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "업데이트 확인 실패 — 이번엔 건너뛴다", e)
        }
    }

    private suspend fun installIfSafe() {
        if (timers.timers.first().isNotEmpty()) {
            Log.i(TAG, "받아 둔 업데이트가 있지만 타이머가 있어 설치를 미룬다")
            return
        }
        Log.i(TAG, "받아 둔 업데이트를 설치한다")
        manager.completeUpdate()
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
