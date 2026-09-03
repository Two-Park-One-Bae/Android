package app.nursemate.core.network.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiHostTest {

    private val host = ApiHost("dev.nursemate.app")

    @Test
    fun `우리 서버면 붙인다`() {
        assertTrue(host.matches("https://dev.nursemate.app/api/v0/users/me".toHttpUrl()))
    }

    @Test
    fun `S3 presigned 업로드에는 붙이지 않는다`() {
        // 경로만 보면 공개 목록에 없어 "인증 필요"로 오판한다 — 호스트로 걸러야 한다.
        val presigned =
            "https://nursemate-raw.s3.ap-northeast-2.amazonaws.com/raw/2026/09/03/abc.jpg?X-Amz-Signature=x"
        assertFalse(host.matches(presigned.toHttpUrl()))
    }

    @Test
    fun `호스트를 모르면 아무 데도 붙이지 않는다`() {
        assertFalse(ApiHost(null).matches("https://dev.nursemate.app/api/v0/users/me".toHttpUrl()))
    }
}
