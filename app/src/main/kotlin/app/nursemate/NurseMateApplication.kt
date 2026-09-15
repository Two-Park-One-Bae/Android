package app.nursemate

import android.app.Application
import android.util.Log
import app.nursemate.appcheck.appCheckProviderFactory
import app.nursemate.core.network.di.PlainClient
import app.nursemate.core.timer.TimerPresetRepository
import app.nursemate.core.timer.TimerReplicaPublisher
import app.nursemate.core.timer.TimerRepository
import app.nursemate.telemetry.TelemetryIdentity
import app.nursemate.telemetry.enableTelemetryCollection
import app.nursemate.timer.alarm.TimerAlarmChannels
import app.nursemate.timer.alarm.TimerOngoingNotifier
import app.nursemate.timer.sync.TimerPresetPublisher
import app.nursemate.timer.widget.PresetWidgetRefresher
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class NurseMateApplication :
    Application(),
    SingletonImageLoader.Factory {

    /**
     * 낱알 이미지(CDN)를 받아 오는 클라이언트.
     *
     * ⚠️ **인터셉터가 없는 클라이언트를 쓴다.** 기본 클라이언트에는 Bearer·App Check 가
     * 붙는데, 이미지는 우리 API 서버가 아니라 `img.nursemate.app` 에서 온다 —
     * 토큰을 그쪽으로 보낼 이유가 없다.
     */
    @Inject
    @PlainClient
    lateinit var imageClient: dagger.Lazy<OkHttpClient>

    @Inject
    lateinit var ongoingNotifier: TimerOngoingNotifier

    @Inject
    lateinit var timerRepository: TimerRepository

    @Inject
    lateinit var presetPublisher: TimerPresetPublisher

    @Inject
    lateinit var replicaPublisher: TimerReplicaPublisher

    @Inject
    lateinit var timerPresets: TimerPresetRepository

    @Inject
    lateinit var telemetryIdentity: TelemetryIdentity

    @Inject
    lateinit var crashlytics: FirebaseCrashlytics

    @Inject
    lateinit var analytics: FirebaseAnalytics

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient.get() })) }
        .build()

    override fun onCreate() {
        super.onCreate()

        // 수집 여부를 **가장 먼저** 정한다. 이 뒤로 일어나는 일(App Check 실패, 카카오 초기화,
        // 타이머 복원)이 전부 크래시 후보라, 늦게 걸면 그 사이의 것이 정책과 다르게 나간다.
        //
        // 빌드 타입이 답을 갖는다 — enableTelemetryCollection() 이 src/debug 와 src/release 에
        // 각각 있다(App Check provider 와 같은 구조).
        enableTelemetryCollection(crashlytics, analytics)

        // 세션을 따라가며 크래시·지표에 회원 식별자를 붙인다 (NM-461 계약).
        telemetryIdentity.start(applicationScope)

        // App Check provider 는 **FirebaseApp 초기화 직후 한 번**만 설치한다.
        // 늦게 설치하면 그 사이에 나간 요청이 토큰 없이 간다.
        //
        // 어떤 provider 인지는 빌드 타입이 정한다 — appCheckProviderFactory() 가
        // src/debug 와 src/release 에 각각 있다.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())

        // 카카오 SDK 는 로그인 호출 전에 초기화돼 있어야 한다.
        // 앱 키는 빌드 타입별로 다르고(dev/prod 플랫폼 키), 매니페스트의 리디렉션 스킴과
        // **같은 값**이어야 카카오톡에서 돌아올 수 있다 — 둘 다 build.gradle.kts 가 채운다.
        //
        // 키가 비어 있으면(secrets.properties 미설정) 초기화를 건너뛴다. 카카오 로그인만
        // 동작하지 않고 앱은 뜬다 — CI 처럼 비밀값이 없는 환경을 위해서다.
        if (BuildConfig.KAKAO_APP_KEY.isNotEmpty()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_APP_KEY)
        }

        // 타이머 알람 채널은 **울리기 전에** 있어야 한다. 알람 시점에 만들면 늦다.
        // 이미 있으면 시스템이 무시하므로 매 실행 호출해도 된다.
        TimerAlarmChannels.ensure(this)

        // 앱이 죽어 있는 동안 만료한 타이머를 현재 시각에 맞추고, 아직 안 끝난 것의 예약을
        // 되살린다. 재부팅은 TimerBootReceiver 가 따로 받지만, 그 밖의 이유로 예약이
        // 사라졌을 수도 있어(강제 종료·시스템 정리) 시작할 때마다 한 번 맞춘다.
        //
        // ⚠️ **복원이 끝난 뒤에 진행 중 표시를 켠다.** 복원은 남아 있던 알람 알림을 통째로
        // 걷어내는데(`cancelAll`), 그때 진행 중 표시까지 함께 지워진다. 먼저 켜면 알림을
        // 그렸다가 곧바로 지워져, 알림을 눌러 앱에 들어온 사용자 눈앞에서 표시가 사라진다.
        //
        // ⚠️ 실패를 삼키지 않는다. 예전에는 예외가 나도 조용히 죽어, 알람이 예약되지 않는
        // 것도 알림이 안 걷히는 것도 로그 한 줄 없이 지나갔다 — 원인 찾기가 훨씬 오래 걸렸다.
        @Suppress("TooGenericExceptionCaught")
        applicationScope.launch {
            runCatching { timerRepository.restore() }
                .onFailure { Log.e(TIMER_TAG, "타이머 복원 실패", it) }
                .onSuccess { Log.i(TIMER_TAG, "타이머 복원 완료") }

            // 복원이 실패해도 진행 중 표시는 켠다 — 저장된 타이머가 있으면 보여 줘야 한다.
            ongoingNotifier.start(applicationScope)

            // 워치에 프리셋을 계속 흘려보낸다(NM-445).
            presetPublisher.start(applicationScope)

            // 워치와 서로 맞춘다(NM-445). 스냅샷과 나란히 돈다 — 워치 화면이 아직
            // 스냅샷을 보고 있어, 워치가 자기 타이머를 갖게 되면 위쪽을 걷어낸다.
            replicaPublisher.start(applicationScope)
        }

        // 앱 안에서 프리셋을 고치면 위젯도 따라 바뀌어야 한다. 지정 화면은 자기가 직접
        // 그리지만(그쪽이 더 빠르다), 이름·시간 수정이나 삭제는 여기로만 들어온다.
        applicationScope.launch {
            timerPresets.presets
                .drop(1)
                .distinctUntilChanged()
                .collect { PresetWidgetRefresher.refreshAll(this@NurseMateApplication) }
        }
    }

    private companion object {
        const val TIMER_TAG = "NM441"
    }
}
