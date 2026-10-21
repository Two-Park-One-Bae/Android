package app.nursemate.core.data.pill

import android.util.Log
import app.nursemate.core.network.api.PillApi
import app.nursemate.core.network.api.PillLabelRequest
import app.nursemate.core.network.api.PillSegmentationSaveRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * 학습 라벨을 서버로 보낸다 — 「선택」한 품목과 「완료」 때 지운 번호(NM-571, spec `api/domains/label.md`).
 *
 * ## 베스트 에포트다
 * 실패해도 식별 흐름을 막지 않고, 화면에 알리지 않고, **다시 보내지 않는다**(계약). 그래서 결과를 돌려주지 않고 로그만
 * 남긴다. 크롭 · 위치는 속성 추출 요청에 실려 이미 갔다([PillRepository.attributes]).
 *
 * 회원과 잇지 않는 익명 데이터다. 로그에도 `imageId` 와 회원 식별자를 같은 줄에 남기지 않는다(여기서는 회원을 모른다).
 */
@Singleton
class PillLabelSender @Inject constructor(private val pillApi: PillApi) {

    suspend fun label(imageId: String, pillIndex: Int, request: PillLabelRequest) {
        send("라벨 $pillIndex") { pillApi.label(imageId, pillIndex, request) }
    }

    suspend fun deleted(imageId: String, indexes: List<Int>) {
        send("지운 번호 $indexes") { pillApi.segmentation(imageId, PillSegmentationSaveRequest(indexes)) }
    }

    @Suppress("TooGenericExceptionCaught") // 무엇이 실패하든 식별 흐름으로 번지지 않게 — 취소만 되던진다
    private suspend fun send(what: String, call: suspend () -> Unit) {
        try {
            call()
            Log.i(TAG, "$what 보냄")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what 못 보냄 — 다시 보내지 않는다: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "NM571"
    }
}
