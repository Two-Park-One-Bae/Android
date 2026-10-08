package app.nursemate.attribution

import android.app.Application
import android.util.Log
import app.nursemate.BuildConfig
import co.ab180.airbridge.Airbridge
import co.ab180.airbridge.AirbridgeOptionBuilder
import co.ab180.airbridge.common.AirbridgeCategory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 유입 경로 측정 (NM-543).
 *
 * ## [AppAnalytics][app.nursemate.telemetry.AppAnalytics] 와 섞지 않는다
 * 둘은 묻는 것이 다르다. Firebase 는 **앱 안에서 무슨 일이 일어났나**를 재고, 여기는
 * **이 사용자가 어디서 왔나**만 잰다. 한 파사드에 합치면 호출부가 어느 쪽으로 가는지를
 * 이름만으로 알 수 없고, 제품 이벤트가 하나씩 광고 지표로 새어 나간다.
 *
 * 그래서 **보낼 수 있는 것이 가입 하나**다. 늘리려면 iOS(NM-542)와 이름·시점을 맞춰야 한다.
 */
interface AttributionTracker {

    /**
     * 가입했다 — **최초 필수 동의를 마친 그 순간**에만 부른다.
     *
     * 약관 개정 재동의는 가입이 아니다. 같은 사람이 약관이 바뀔 때마다 새로 가입한 것으로
     * 세이면 광고 성과가 부풀려진다. 최초인지 아닌지는 화면이 아는 값이라
     * (`User.needsReconsent`) 호출부가 가른다.
     */
    fun signUp()

    /**
     * 수집을 켠다 — **로그인한 회원이 `OVERSEAS` 에 동의했을 때만**(spec §선택 동의).
     *
     * 초기화는 앱이 뜰 때 하지만 수집은 여기서 시작한다([AirbridgeAttributionTracker.initialize]
     * 가 자동 시작을 꺼 둔다). 동의가 로그인 뒤라 **첫 실행보다 늦게** 불린다.
     */
    fun start()

    /**
     * 수집을 멈춘다 — 로그인 전 · 로그아웃 · 탈퇴 · 철회.
     *
     * 병동 공용 기기에서 **앞사람의 동의로 뒷사람이 측정되면 안 된다.** 그래서 「켜는 조건이
     * 아니면」이 아니라 「아닌 모든 때」 부른다.
     */
    fun stop()
}

/**
 * Airbridge 로 보내는 구현.
 *
 * ## 토큰이 비면 아무것도 하지 않는다
 * debug 빌드와 비밀값이 없는 CI 가 그 경우다([BuildConfig.AIRBRIDGE_APP_TOKEN]).
 * SDK 가 초기화되지 않은 채로 `trackEvent` 를 부르면 이벤트가 버퍼에 쌓였다가 다음 초기화
 * 때 한꺼번에 나갈 수 있어, **부르지 않는 쪽**이 안전하다.
 *
 * ## 전송이 실패해도 앱은 멈추지 않는다
 * 동의를 마치고 홈으로 넘어가는 길목에서 부른다. 측정 때문에 그 길이 막히면 안 된다.
 */
@Singleton
class AirbridgeAttributionTracker @Inject constructor() : AttributionTracker {

    @Suppress("TooGenericExceptionCaught")
    override fun start() {
        if (!enabled) return
        try {
            Airbridge.startTracking()
            Log.i(TAG, "수집 시작 — OVERSEAS 동의")
        } catch (t: Throwable) {
            Log.w(TAG, "수집을 시작하지 못했다", t)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun stop() {
        if (!enabled) return
        try {
            Airbridge.stopTracking()
            Log.i(TAG, "수집 중지")
        } catch (t: Throwable) {
            Log.w(TAG, "수집을 멈추지 못했다", t)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun signUp() {
        if (!enabled) return
        try {
            Airbridge.trackEvent(AirbridgeCategory.SIGN_UP)
            Log.i(TAG, "가입 이벤트 전송")
        } catch (t: Throwable) {
            Log.w(TAG, "가입 이벤트를 못 보냈다", t)
        }
    }

    companion object {
        private const val TAG = "NM543"

        /**
         * Meta 광고 유입을 기기 단위로 잇는 데 쓰는 Facebook 앱 ID.
         *
         * 비밀값이 아니다 — 앱 ID 는 공개 식별자다(암호 해독 키는 대시보드에만 넣는다).
         * 그래서 `secrets.properties` 가 아니라 여기 둔다.
         */
        private const val META_APP_ID = "3544644509030922"

        private val enabled: Boolean get() = BuildConfig.AIRBRIDGE_APP_TOKEN.isNotEmpty()

        /**
         * SDK 를 깨운다 — `Application.onCreate` 에서 **한 번**.
         *
         * **수집은 시작하지 않는다.** 자동 시작을 끄고 [AttributionTracker.start] 를 기다린다
         * (spec §선택 동의). 초기화를 여기서 하는 것은 동의 시점에 SDK 가 이미 서 있어야
         * 설치 리퍼러를 읽을 수 있기 때문이다.
         */
        @Suppress("TooGenericExceptionCaught")
        fun initialize(application: Application) {
            if (!enabled) {
                Log.i(TAG, "SDK 토큰이 없다 — 유입 측정을 건너뛴다")
                return
            }
            try {
                val option = AirbridgeOptionBuilder(
                    BuildConfig.AIRBRIDGE_APP_NAME,
                    BuildConfig.AIRBRIDGE_APP_TOKEN
                )
                    // Meta 광고 유입을 잇는다. 해독 키는 대시보드 쪽 설정이라 앱에 없다.
                    .setMetaInstallReferrer(META_APP_ID)
                    // ⚠️ **우리 딥링크만 센다.** 카카오 로그인이 `kakao{키}://oauth` 로 돌아오는데,
                    //    이것까지 딥링크 유입으로 잡히면 로그인할 때마다 유입이 하나씩 생긴다.
                    .setTrackAirbridgeDeeplinkOnlyEnabled(true)
                    // ⚠️ **초기화만 하고 수집은 시작하지 않는다**(NM-548).
                    //    측정 정보는 국외 수탁사와 Meta 로 나가고, 그 근거가 개인정보 보호법
                    //    제28조의8 제1항 제1호의 **별도 동의**다 — 동의 전에는 아무것도
                    //    전송하지 않는다. 켜는 것은 [start] 뿐이다.
                    .setAutoStartTrackingEnabled(false)
                    .build()
                Airbridge.initializeSDK(application, option)
                Log.i(TAG, "SDK 초기화 — 수집은 동의 뒤에 시작한다")
            } catch (t: Throwable) {
                // 측정이 안 서는 것과 앱이 안 뜨는 것은 비교할 일이 아니다.
                Log.w(TAG, "SDK 초기화 실패 — 유입 측정 없이 간다", t)
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AttributionModule {

    @Binds
    @Singleton
    abstract fun tracker(impl: AirbridgeAttributionTracker): AttributionTracker
}
