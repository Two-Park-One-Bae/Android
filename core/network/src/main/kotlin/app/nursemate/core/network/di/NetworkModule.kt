package app.nursemate.core.network.di

import app.nursemate.core.network.BuildConfig
import app.nursemate.core.network.NetworkConfig
import app.nursemate.core.network.api.UserApi
import app.nursemate.core.network.auth.AppCheckInterceptor
import app.nursemate.core.network.auth.AuthHeaderInterceptor
import app.nursemate.core.network.auth.TokenRefreshAuthenticator
import app.nursemate.core.network.error.ErrorInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * HTTP 골격.
 *
 * 인터셉터 순서가 의미를 갖는다. Auth 가 헤더를 붙이고, Error 는 그 바깥에서 최종 응답을 본다 —
 * Authenticator 의 재시도까지 끝난 결과라야 진짜 실패다.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    /**
     * `ignoreUnknownKeys` 를 켜는 게 핵심이다. 서버가 응답에 필드를 하나 추가하는 순간
     * 구버전 앱이 전부 깨지는 걸 막는다. enum 쪽 대비는 FallbackEnumSerializer 가 맡는다.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        // null 인 필드를 굳이 실어 보내지 않는다.
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authHeaderInterceptor: AuthHeaderInterceptor,
        appCheckInterceptor: AppCheckInterceptor,
        errorInterceptor: ErrorInterceptor,
        authenticator: TokenRefreshAuthenticator
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authHeaderInterceptor)
        .addInterceptor(appCheckInterceptor)
        .addInterceptor(errorInterceptor)
        .authenticator(authenticator)
        .apply {
            if (BuildConfig.DEBUG) {
                // 헤더에 토큰이 실리게 되므로 본문·헤더까지 찍지 않는다.
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // baseUrl 은 반드시 `/` 로 끝나야 한다. API 경로는 상대 경로로 적는다 —
        // "/api/v0/..." 처럼 절대 경로로 쓰면 baseUrl 의 경로 부분이 날아간다.
        .baseUrl(NetworkConfig.BASE_URL.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 10L

    /** 알약 분석은 서버가 외부 AI 를 부르느라 오래 걸린다. 기본 10초로는 모자란다. */
    private const val READ_TIMEOUT_SECONDS = 60L

    /** 크롭을 base64 로 실어 보내는 요청(NM-393)이 있다. 기본 10초로는 셀룰러에서 모자란다. */
    private const val WRITE_TIMEOUT_SECONDS = 60L
}
