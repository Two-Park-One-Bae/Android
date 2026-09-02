package app.nursemate

import android.app.Application
import app.nursemate.appcheck.appCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NurseMateApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // App Check provider 는 **FirebaseApp 초기화 직후 한 번**만 설치한다.
        // 늦게 설치하면 그 사이에 나간 요청이 토큰 없이 간다.
        //
        // 어떤 provider 인지는 빌드 타입이 정한다 — appCheckProviderFactory() 가
        // src/debug 와 src/release 에 각각 있다.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
