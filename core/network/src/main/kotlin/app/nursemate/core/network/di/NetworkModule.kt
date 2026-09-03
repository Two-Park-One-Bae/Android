package app.nursemate.core.network.di

import app.nursemate.core.network.BuildConfig
import app.nursemate.core.network.NetworkConfig
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
 * 여기에는 **인증이 없다.** 토큰을 싣는 인터셉터와 401 재시도는 인증 기능에 딸린 것이라
 * NM-407 이 이 모듈에 얹는다. 이 모듈은 어떤 API 도 알지 못한다 — API 인터페이스는
 * 각 기능이 자기 것을 제공한다.
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
    fun provideOkHttpClient(errorInterceptor: ErrorInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(errorInterceptor)
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

    private const val CONNECT_TIMEOUT_SECONDS = 10L

    /** 알약 분석은 서버가 외부 AI 를 부르느라 오래 걸린다. 기본 10초로는 모자란다. */
    private const val READ_TIMEOUT_SECONDS = 60L

    /** 크롭을 base64 로 실어 보내는 요청(NM-393)이 있다. 기본 10초로는 셀룰러에서 모자란다. */
    private const val WRITE_TIMEOUT_SECONDS = 60L
}
