package app.nursemate.pill

import android.content.Context
import android.os.Build
import app.nursemate.BuildConfig
import app.nursemate.core.vision.OpenOutcome
import app.nursemate.core.vision.VisionBackend
import app.nursemate.telemetry.AnalyticsEvent
import app.nursemate.telemetry.AppAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 온디바이스 모델이 어느 가속기로 열렸는지 보고한다 — [AnalyticsEvent.OnDeviceBackend] (NM-628).
 *
 * **모델 파일마다 앱 버전당 한 번**이다. 모델은 식별 플로우마다 다시 열릴 수 있는데, 매번 보내면 많이 쓰는
 * 사용자가 비율을 끌고 간다. 기기 하나가 한 표다.
 *
 * GPU 열기 실패가 늘면 그 칩을 ML 측정 목록에 올린다 — 앱 안 자가 점검 대신 **출시 전 기기별 검증**으로
 * 막기로 했고(2026-10-19 결정), 이 이벤트가 어느 기기를 재야 하는지 알려 준다.
 */
@Singleton
class OnDeviceBackendReporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val analytics: AppAnalytics
) {
    // 주입은 메인 스레드에서 일어난다 — 디스크는 실제로 볼 때 연다.
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** [VisionBackend] 에 꽂는다. 모델이 처음 열리기 전이어야 한다 — `Application.onCreate`. */
    fun install() {
        VisionBackend.onModelOpened = ::report
    }

    /** 모델을 여는 스레드에서 불린다 — 가볍게 끝낸다. */
    private fun report(modelFileName: String, outcome: OpenOutcome) {
        val key = KEY_PREFIX + modelFileName
        if (prefs.getInt(key, 0) == BuildConfig.VERSION_CODE) return
        analytics.track(event(modelFileName, outcome, soc()))
        prefs.edit().putInt(key, BuildConfig.VERSION_CODE).apply()
    }

    private fun soc(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE

    internal companion object {
        const val PREFS = "ondevice_backend"
        const val KEY_PREFIX = "reported_"

        fun event(modelFileName: String, outcome: OpenOutcome, socModel: String) = AnalyticsEvent.OnDeviceBackend(
            model = modelFileName,
            backend = if (outcome.onGpu) "gpu" else "cpu",
            cause = outcome.label,
            socModel = socModel
        )
    }
}
