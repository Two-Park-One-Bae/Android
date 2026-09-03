package app.nursemate

import android.app.Application
import app.nursemate.appcheck.appCheckProviderFactory
import app.nursemate.core.network.di.PlainClient
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
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

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient.get() })) }
        .build()

    override fun onCreate() {
        super.onCreate()

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
    }
}
