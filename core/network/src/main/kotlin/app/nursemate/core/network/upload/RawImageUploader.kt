package app.nursemate.core.network.upload

import app.nursemate.core.network.api.PillApi
import app.nursemate.core.network.di.PlainClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 학습데이터용 **원본** 사진을 S3 에 올린다 (NM-348).
 *
 * ## 식별과 완전히 분리돼 있다
 * 서버에 키를 알려주지 않고, 식별 횟수도 차감되지 않는다. **실패해도 식별 플로우에 영향이
 * 없어야 한다** — 그래서 호출부는 결과를 기다리지 않고, 여기서도 예외를 밖으로 내지 않는다.
 *
 * ## 우리 헤더를 붙이지 않는다
 * presigned URL 은 그 자체가 인가라 추가 헤더가 필요 없고, 무엇보다 **Firebase ID 토큰을
 * AWS 로 보내면 안 된다.** 인터셉터가 하나도 없는 [PlainClient] 를 쓴다.
 */
@Singleton
class RawImageUploader @Inject constructor(
    private val pillApi: PillApi,
    @PlainClient private val client: OkHttpClient
) {

    /**
     * @param jpeg 원본 사진 바이트. **JPEG 여야 한다.**
     * @return 성공 여부. 호출부가 분기할 일은 없고 로그·지표용이다.
     */
    suspend fun upload(jpeg: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val url = pillApi.uploadUrl().uploadUrl
            val request = Request.Builder()
                .url(url)
                // ⚠️ 정확히 `image/jpeg` 여야 한다. presigned 서명에 포함된 값이라
                //    `image/jpg` 같은 변형이면 S3 가 403 으로 거부한다.
                .put(jpeg.toRequestBody(JPEG))
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()
    }
}
