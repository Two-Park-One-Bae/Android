package app.nursemate.core.network

/**
 * 네트워크 상수.
 *
 * BASE_URL 은 `secrets.properties` → BuildConfig 로 주입한다(빌드 타입별로 dev/prod 가 갈린다).
 * 코드에 하드코딩하지 않는다.
 */
object NetworkConfig {
    const val BASE_URL: String = BuildConfig.BASE_URL
    const val API_PREFIX: String = "api/v0"
}
