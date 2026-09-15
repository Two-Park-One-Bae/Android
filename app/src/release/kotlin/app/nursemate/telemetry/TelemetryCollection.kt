package app.nursemate.telemetry

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * 배포 빌드는 크래시와 지표를 보낸다.
 *
 * SDK 기본값이 이미 "켜짐"이지만 **명시적으로 건다.** 기본값에 기대면 매니페스트 메타데이터나
 * 다른 라이브러리가 끄고 있어도 알아차릴 수 없고, debug 쪽 동명 함수와 나란히 놓여야
 * 빌드 타입마다 무엇이 다른지가 한눈에 읽힌다.
 *
 * ⚠️ 이 파일은 **release 소스셋에만** 있다. debug 쪽 동명 함수와 짝을 이룬다.
 */
fun enableTelemetryCollection(crashlytics: FirebaseCrashlytics, analytics: FirebaseAnalytics) {
    crashlytics.isCrashlyticsCollectionEnabled = true
    analytics.setAnalyticsCollectionEnabled(true)
}
