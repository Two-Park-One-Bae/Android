package app.nursemate.core.network.auth

import app.nursemate.core.network.NetworkConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 인증 헤더를 붙여도 되는 호스트.
 *
 * ## 경로만 보면 토큰이 새어 나간다
 * 인증 인터셉터가 경로만 보고 헤더를 붙이면, **우리 서버가 아닌 곳으로 나가는 요청에도**
 * Firebase ID 토큰이 실린다. 실제로 그럴 일이 있다 — 학습데이터 원본 업로드는 S3 presigned
 * URL 로 PUT 하는데(NM-348), 그 경로(`/raw/2026/09/03/{uuid}.jpg`)는 공개 경로 목록에
 * 없으므로 그대로 두면 토큰이 AWS 로 간다.
 *
 * 상수가 아니라 주입값인 건 테스트에서 갈아 끼우기 위해서다 — 상수로 두면
 * MockWebServer 호스트와 영영 달라 인터셉터를 검사할 수 없다.
 */
class ApiHost(private val value: String?) {
    fun matches(url: HttpUrl): Boolean = value != null && url.host == value

    companion object {
        /** 빌드에 주입된 Base URL 의 호스트. */
        val Default = ApiHost(NetworkConfig.BASE_URL.toHttpUrlOrNull()?.host)
    }
}
