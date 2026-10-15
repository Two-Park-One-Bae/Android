package app.nursemate.pill

import android.content.Context
import android.os.Build
import app.nursemate.BuildConfig
import app.nursemate.core.vision.QnnDelivery
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
 * ## 왜 퀄컴만인가
 * QNN 은 Hexagon 에만 있다. 엑시노스·텐서가 `none` 인 것은 정상이라 분모를 흐린다.
 */
@Singleton
class QnnDeliveryReporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val analytics: AppAnalytics
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 디스크를 읽으므로 메인 스레드 밖에서 부른다. */
    fun reportOnce() {
        // SOC_* 는 API 31+ 다. 그 아래 퀄컴 기기는 어차피 기기그룹(system-on-chip)에 안 맞는다.
        val qualcomm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isQualcomm(Build.SOC_MANUFACTURER)
        val key = "$KEY_REPORTED_PREFIX${BuildConfig.VERSION_CODE}"
        if (!qualcomm || prefs.getBoolean(key, false)) return

        val delivered = QnnDelivery.of(File(context.applicationInfo.nativeLibraryDir))
        analytics.track(AnalyticsEvent.QnnDelivery(socModel = Build.SOC_MODEL, delivered = delivered.label))
        prefs.edit().putBoolean(key, true).apply()
    }

    internal companion object {
        const val PREFS = "qnn_delivery"
        const val KEY_REPORTED_PREFIX = "reported_v"

        /** `Build.SOC_MANUFACTURER` 가 퀄컴이면 `QTI` 다(DeviceProbeTest 가 실기기에서 찍었다). */
        fun isQualcomm(socManufacturer: String): Boolean = socManufacturer == "QTI"
    }
}
