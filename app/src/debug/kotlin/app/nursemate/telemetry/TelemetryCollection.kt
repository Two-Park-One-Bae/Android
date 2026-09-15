package app.nursemate.telemetry

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * 개발 빌드는 **아무것도 보내지 않는다.**
 *
 * 팀·테스터의 사용이 프로덕션 지표를 오염시키면 안 된다
 * (spec `feature/auth/README.md` §내부 사용자 지표 제외).
 *
 * ## 속성으로 거르지 않고 전송 자체를 막는다
 * `is_internal` 같은 속성을 달아 두고 조회할 때 걸러내는 방법도 있지만, 그러면 **지표의
 * 분모가 달라진다** — 전체 사용자 수·크래시 없는 사용자 비율이 내부 사용까지 포함한 값이 된다.
 * iOS 도 `setAnalyticsCollectionEnabled` 로 같은 선택을 했다.
 *
 * 크래시가 필요하면 debug 는 logcat 이 있다. Crashlytics 를 볼 이유가 없다.
 *
 * ⚠️ 이 파일은 **debug 소스셋에만** 있다. release 쪽 동명 함수와 짝을 이룬다.
 */
fun enableTelemetryCollection(crashlytics: FirebaseCrashlytics, analytics: FirebaseAnalytics) {
    crashlytics.isCrashlyticsCollectionEnabled = false
    analytics.setAnalyticsCollectionEnabled(false)
}
