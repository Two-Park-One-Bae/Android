package app.nursemate.core.data.auth

import app.nursemate.core.network.auth.AppCheckTokenProvider
import app.nursemate.core.network.auth.BearerTokenProvider
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindBearerTokenProvider(impl: FirebaseBearerTokenProvider): BearerTokenProvider

    @Binds
    @Singleton
    abstract fun bindAppCheckTokenProvider(impl: FirebaseAppCheckTokenProvider): AppCheckTokenProvider

    companion object {
        /**
         * `FirebaseApp`은 `firebase-common`의 초기화 Provider가 앱 시작 시 자동으로 만든다
         * (설정은 :app 의 `google-services.json`). 여기서 `initializeApp`을 부르지 않는다.
         *
         * `Firebase.auth`(KTX 확장) 대신 `getInstance()`를 쓴다 — BOM 32.5부터 KTX가 본 모듈로
         * 합쳐지면서 `com.google.firebase.ktx.*` 경로가 폐기됐고, 이쪽이 버전을 타지 않는다.
         */
        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        /** provider 설치는 :app 의 Application 이 빌드 타입별로 한다. 여기서는 인스턴스만 꺼낸다. */
        @Provides
        @Singleton
        fun provideFirebaseAppCheck(): FirebaseAppCheck = FirebaseAppCheck.getInstance()
    }
}
