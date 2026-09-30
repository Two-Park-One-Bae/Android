package app.nursemate.core.data.pill

import app.nursemate.core.model.Image
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidatePage
import app.nursemate.core.model.PillDetail
import app.nursemate.core.model.Usage
import app.nursemate.core.network.api.PillApi
import app.nursemate.core.network.api.PillAttributeItem
import app.nursemate.core.network.api.PillAttributesRequest
import app.nursemate.core.network.api.PillCandidatesRequest
import app.nursemate.core.network.api.PillErrorReader
import app.nursemate.core.network.error.ApiErrorCode
import app.nursemate.core.network.error.ApiFailure
import app.nursemate.core.network.upload.RawImageUploader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** 속성 추출 결과. [usage] 는 이번 차감이 반영된 값이라 화면의 남은 횟수를 이걸로 갱신한다. */
data class AttributeResult(val items: List<PillAttribute>, val usage: Usage)

/**
 * 일일 식별 한도에 걸렸다.
 *
 * **차감되지 않은 상태**다(spec §카운트 규칙 — 4xx 는 미증가). 서버가 응답에 사용량을
 * 실어 주므로 화면은 리셋 시각까지 안내할 수 있다.
 *
 * @param usage 본문에서 뽑지 못하면 null. 그때도 한도 도달이라는 사실은 유효하다.
 */
class DailyLimitReached(val usage: Usage?) : Exception("일일 식별 한도 도달")

/**
 * 알약 식별 서버 연동.
 *
 * ## 한도는 서버가 판정한다
 * 앱은 [usage] 로 받은 값을 **보여줄 뿐** 자체 카운트나 판정을 두지 않는다.
 * 조회에 실패해도 식별 진입을 막지 않는다 — 최종 판정은 [attributes] 의 429 다
 * (spec §식별 횟수 제한).
 */
@Singleton
class PillRepository @Inject constructor(
    private val pillApi: PillApi,
    private val uploader: RawImageUploader,
    private val errors: PillErrorReader
) {

    suspend fun usage(): Result<Usage> = runCatching { pillApi.usage() }

    /**
     * 크롭 → 색·모양·제형.
     *
     * ⚠️ **크롭이 비었으면 부르지 않는다.** 사진에 알약이 몇 개든 요청 1회가 1회 식별이라,
     * 빈 요청은 아무 소득 없이 한 번 차감시킨다.
     *
     * @return 한도에 걸리면 [DailyLimitReached] 로 실패한다. 그 밖의 실패는 [ApiFailure].
     */
    suspend fun attributes(crops: Map<String, ByteArray>): Result<AttributeResult> {
        require(crops.isNotEmpty()) { "크롭이 없는데 속성 추출을 요청했다" }

        return runCatching {
            val items = crops.map { (pillId, png) ->
                PillAttributeItem(pillId = pillId, croppedImage = Image(PNG, png.toBase64()))
            }
            val response = pillApi.attributes(PillAttributesRequest(items))
            AttributeResult(items = response.items, usage = response.usage)
        }.recoverCatching { throwable ->
            val failure = throwable as? ApiFailure
            if (failure?.code == ApiErrorCode.LIMIT_EXCEEDED) {
                throw DailyLimitReached(errors.limitUsage(failure))
            }
            throw throwable
        }
    }

    /**
     * 수정한 속성으로 후보를 조회한다.
     *
     * 속성이 바뀔 때마다 불린다 — 호출부가 이전 요청을 취소해야 타이핑 중에 응답이
     * 뒤섞이지 않는다(코루틴 취소로 처리한다).
     *
     * 조건이 좁아 후보가 0개인 것은 **오류가 아니다.** 빈 목록으로 온다.
     */
    suspend fun candidates(request: PillCandidatesRequest): Result<PillCandidatePage> =
        runCatching { pillApi.candidates(request) }
            // ⚠️ 실시간 조회라 이전 요청이 나가 있는 채로 취소되는 게 정상 흐름이다.
            // runCatching 이 CancellationException 까지 Result.failure 로 삼키면 코루틴
            // 취소가 "조회 실패"로 둔갑해 호출부가 failed = true 를 세운다 — 다시 던진다.
            .onFailure { if (it is CancellationException) throw it }

    /**
     * 확정한 알약의 세부정보.
     *
     * 404 는 '없음'이지 실패가 아니다 — 호출부가 [ApiFailure.httpStatus] 로 갈라 보도록
     * 실패를 그대로 넘긴다. 여기서 성공/실패를 뭉개면 화면이 오류와 없음을 구분하지 못한다.
     */
    suspend fun detail(pillCode: String): Result<PillDetail> = runCatching { pillApi.pillDetail(pillCode) }

    /**
     * 학습데이터를 올린다. **결과를 기다릴 필요가 없다** — 실패해도 식별 플로우에 영향이 없다(NM-348).
     *
     * ⚠️ 받는 것은 **원본이 아니라 가공본**이다(NM-440) — EXIF 회전 · 중앙 정사각 1:1 ·
     * 최장변 2048px 까지 마치고 JPEG 로 다시 압축한 것, 즉 **모델이 실제로 보는 그림**이다.
     * 원본을 올리면 학습 분포가 추론 경로와 갈리고 전송량만 커진다.
     */
    suspend fun uploadOriginal(jpeg: ByteArray): Boolean = uploader.upload(jpeg)

    private companion object {
        /** 크롭은 마스크를 알파로 담고 있어 **PNG 여야 한다.** JPEG 로 바꾸면 배경이 살아난다. */
        const val PNG = "image/png"
    }
}

private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
