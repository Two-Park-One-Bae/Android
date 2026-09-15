package app.nursemate.telemetry

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object TelemetryModule {

    /**
     * `AuthModule` 과 같은 이유로 `getInstance()` 를 쓴다 — `FirebaseApp` 은 `firebase-common` 의
     * 초기화 Provider 가 앱 시작 시 자동으로 만든다.
     *
     * 수집 on/off 를 따로 걸지 않는다 — SDK 기본값(켜짐)을 그대로 쓴다. debug 는 Firebase
     * 프로젝트가 dev 라 운영 지표와 섞이지 않고, 크래시는 개발 중에 더 필요하다.
     */
    @Provides
    @Singleton
    fun provideCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    @Provides
    @Singleton
    fun provideAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)
}
