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
     * 수집 on/off 는 여기서 정하지 않는다. [TelemetryCollection] 이 빌드 타입을 보고 한 번에 건다 —
     * 두 군데서 켜고 끄면 어느 쪽이 이겼는지 읽어 낼 수 없다.
     */
    @Provides
    @Singleton
    fun provideCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    @Provides
    @Singleton
    fun provideAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)
}
