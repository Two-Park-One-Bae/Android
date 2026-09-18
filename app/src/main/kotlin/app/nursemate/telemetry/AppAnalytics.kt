package app.nursemate.telemetry

import android.os.Bundle
import android.util.Log
import app.nursemate.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 분석 전송 파사드.
 *
 * ## 화면이 [FirebaseAnalytics] 를 직접 부르지 않는다
 * `logEvent(name, bundle)` 은 **이름도 파라미터도 문자열**이라, 호출부마다 직접 쓰면 오타가
 * 컴파일에 안 걸리고 리포트에 빈 축으로만 나타난다. 보낼 수 있는 것을 [AnalyticsEvent] 로
 * 닫아 두면 이름·파라미터가 한곳에서만 정해진다.
 *
 * ## 인터페이스인 이유
 * 지표의 **셈법**(한 시도에 결과 하나, 체류시간 누적 …)은 화면을 눌러 보는 것으로 검증할 수
 * 없다. 보낸 것을 그대로 모으는 가짜를 끼울 자리가 필요하다.
 */
interface AppAnalytics {
    fun track(event: AnalyticsEvent)
}

/**
 * Firebase 로 보내는 구현.
 *
 * ## 전송이 실패해도 앱은 멈추지 않는다
 * 지표는 부수적인 일이다. Bundle 변환이나 SDK 호출에서 무엇이 터지든 삼킨다 —
 * 알약을 확정하다 지표 때문에 죽는 쪽이 훨씬 나쁘다.
 *
 * ## DebugView 확인
 * ```
 * adb shell setprop debug.firebase.analytics.app app.nursemate.debug
 * adb shell setprop log.tag.FA VERBOSE
 * ```
 * debug 빌드는 dev Firebase 프로젝트로 들어가 운영 지표를 오염시키지 않는다.
 */
@Singleton
class FirebaseAppAnalytics @Inject constructor(private val analytics: FirebaseAnalytics) : AppAnalytics {

    @Suppress("TooGenericExceptionCaught")
    override fun track(event: AnalyticsEvent) {
        try {
            analytics.logEvent(event.name, event.params.toBundle())
            if (BuildConfig.DEBUG) Log.d(TAG, "${event.name} ${event.params}")
        } catch (t: Throwable) {
            Log.w(TAG, "지표를 못 보냈다 — ${event.name}", t)
        }
    }

    /**
     * GA4 가 받는 타입은 `String`·`Long`·`Double`·`Bundle` 뿐이다.
     *
     * ⚠️ **`Int` 를 `putInt` 로 담으면 안 된다.** SDK 는 정수 축을 `Long` 으로만 읽어
     * 리포트에서 `(not set)` 이 된다. [AnalyticsEvent] 가 이미 `Long` 으로 넘기지만,
     * 여기서도 한 번 더 막아 둔다 — 새 이벤트를 추가하다 놓치는 자리다.
     */
    private fun Map<String, Any>.toBundle(): Bundle = Bundle(size).also { bundle ->
        forEach { (key, value) ->
            when (value) {
                // 문자열은 100자에서 자른다. 넘치면 SDK 가 파라미터를 통째로 버린다.
                is String -> bundle.putString(key, value.take(MAX_STRING_VALUE))

                is Long -> bundle.putLong(key, value)

                is Int -> bundle.putLong(key, value.toLong())

                is Double -> bundle.putDouble(key, value)

                // 여기 오면 GA4 가 못 받는 타입이다. 조용히 버려지느니 로그라도 남긴다.
                else -> Log.w(TAG, "$key 는 GA4 가 못 받는 타입이다 — ${value::class.simpleName}")
            }
        }
    }

    private companion object {
        const val TAG = "NM468"

        /** GA4 문자열 파라미터 값 상한. */
        const val MAX_STRING_VALUE = 100
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppAnalyticsModule {

    @Binds
    @Singleton
    abstract fun bindAppAnalytics(impl: FirebaseAppAnalytics): AppAnalytics
}
