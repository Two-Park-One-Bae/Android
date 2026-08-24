package app.nursemate.core.network

/**
 * 네트워크 상수. BASE_URL은 secrets.properties → BuildConfig로 주입된다.
 * Retrofit 클라이언트·인터셉터(X-Device-Id, X-Firebase-AppCheck)·RFC 9457 에러 모델은 P1(NM-392)에서 구현.
 */
object NetworkConfig {
    const val BASE_URL: String = BuildConfig.BASE_URL
    const val API_PREFIX: String = "/api/v0"
}
