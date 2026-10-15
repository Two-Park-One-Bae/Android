package app.nursemate.pill

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.nursemate.BuildConfig
import app.nursemate.core.vision.QnnDeliveryState
import app.nursemate.telemetry.AnalyticsEvent
import app.nursemate.telemetry.AppAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 퀄컴 기기가 QNN 모듈을 받았는지 **앱 버전마다 한 번** 보고한다 — NM-603.
 *
 * ## 왜 버전마다 한 번인가
 * 전달 상태는 설치 때 정해지고 앱이 떠 있는 동안 바뀌지 않는다. 매 실행 보내면 많이 쓰는
 * 사용자가 비율을 끌고 간다 — 세려는 것은 **설치본**이다. 업데이트 때 기기그룹 목록이 바뀌었을
 * 수 있으니 버전이 바뀌면 다시 보낸다.
 *
 * ## 왜 퀄컴만, Play 설치만인가
 * - QNN 은 Hexagon 에만 있다. 엑시노스·텐서가 `none` 인 것은 정상이라 분모를 흐린다.
 * - Android Studio·`adb install` 은 feature module 을 **전부** 깐다. 그 설치본은 기기그룹을 안
 *   탔으니 「Play 가 무엇을 내려줬나」의 답이 아니다. 릴리스 스모크 테스트용 로컬 APK 가 운영
 *   Firebase 에 가짜 `core+skel` 을 쌓지 않도록 설치 출처가 Play 인 것만 센다.
 *
 * ⚠️ 보냈다는 표시는 `track` 이 돌아온 뒤에 남긴다. 전송 실패를 알 길이 없어(`AppAnalytics` 가
 *    삼킨다) 실패한 설치본은 그 버전 동안 빠진다. Firebase 는 오프라인이면 쌓아 두었다 보내므로
 *    실제로 빠지는 것은 SDK 예외뿐이다.
 */
@Singleton
class QnnDeliveryReporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val analytics: AppAnalytics
) {
    // 주입은 메인 스레드에서 일어난다 — 디스크는 실제로 볼 때 연다.
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** 디스크를 읽으므로 IO 스레드에서 부른다. */
    fun reportOnce() {
        // SOC_* 는 API 31+ 다. 그 아래 퀄컴 기기는 어차피 기기그룹(system-on-chip)에 안 맞는다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) reportOnceS()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun reportOnceS() {
        val eligible = isQualcomm(Build.SOC_MANUFACTURER) && isFromPlay(installer())
        if (!eligible || prefs.getInt(KEY_LAST_VERSION, 0) == BuildConfig.VERSION_CODE) return

        val state = QnnDeliveryState.of(File(context.applicationInfo.nativeLibraryDir))
        analytics.track(AnalyticsEvent.QnnDelivery(socModel = Build.SOC_MODEL, delivered = state.label))
        prefs.edit().putInt(KEY_LAST_VERSION, BuildConfig.VERSION_CODE).apply()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Suppress("TooGenericExceptionCaught")
    private fun installer(): String? = try {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    } catch (e: Exception) {
        Log.w(TAG, "설치 출처를 못 읽었다 — 보고하지 않는다", e)
        null
    }

    internal companion object {
        const val TAG = "NM603"
        const val PREFS = "qnn_delivery"

        /** 마지막으로 보고한 versionCode 하나만 둔다 — 버전마다 키를 늘리지 않는다. */
        const val KEY_LAST_VERSION = "last_reported_version"

        private const val PLAY_STORE = "com.android.vending"

        /** `Build.SOC_MANUFACTURER` 가 퀄컴이면 `QTI` 다(DeviceProbeTest 가 실기기에서 찍었다). */
        fun isQualcomm(socManufacturer: String): Boolean = socManufacturer == "QTI"

        fun isFromPlay(installer: String?): Boolean = installer == PLAY_STORE
    }
}
