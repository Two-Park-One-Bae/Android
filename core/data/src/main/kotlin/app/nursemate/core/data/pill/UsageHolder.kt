package app.nursemate.core.data.pill

import android.util.Log
import app.nursemate.core.model.Usage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 오늘 남은 식별 횟수를 앱 전체가 같은 값으로 본다.
 *
 * ## 왜 한곳에 모으나
 * 홈 카드·미리보기·한도 게이트가 같은 숫자를 보여줘야 하는데, 화면마다 조회하면 서로
 * 어긋난다. 식별에 성공하면 응답에 갱신된 사용량이 실려 오므로([update]) 다시 조회할
 * 필요도 없다.
 *
 * ## 모르는 상태(null)와 0을 구분한다
 * 스펙(§한도 도달 플로우)이 **"1 이상 · 미확인"** 이면 진입시키라고 한다 — 조회에 실패했다고
 * 사용자를 막지 않는다. 최종 판정은 식별 요청의 429 다. 둘을 합치면 서버가 잠깐 흔들릴 때
 * 멀쩡한 사용자가 기능을 못 쓴다.
 */
@Singleton
class UsageHolder @Inject constructor(private val pillRepository: PillRepository) {

    private val _usage = MutableStateFlow<Usage?>(null)

    /** null 이면 아직 모른다. */
    val usage = _usage.asStateFlow()

    /** 한도에 걸렸다고 **확인된** 경우에만 true. 모르면 막지 않는다. */
    val blocked: Boolean get() = _usage.value?.exhausted == true

    /**
     * 실패해도 값을 지우지 않는다 — 직전 값이 없는 것보다 낫다.
     *
     * ⚠️ **실패를 반드시 남긴다.** 조회가 실패하면 홈의 「오늘 남은 횟수」 캡션이 통째로
     * 사라지는데(`usage == null` 이면 캡션을 안 그린다), 전에는 `onSuccess` 만 있어서
     * **아무 흔적이 없었다.** 릴리스 빌드는 `HttpLoggingInterceptor` 도 꺼져 있어
     * (`BuildConfig.DEBUG` 가드) 401 인지 서버 오류인지 구분할 방법이 없었다.
     */
    suspend fun refresh() {
        pillRepository.usage()
            .onSuccess { _usage.value = it }
            .onFailure { Log.w(TAG, "남은 식별 횟수를 못 받았다 — 홈 캡션이 비어 보인다", it) }
    }

    /** 식별 응답(200·429)에 실려 온 값으로 갱신한다. */
    fun update(usage: Usage) {
        _usage.value = usage
    }

    /**
     * 계정 스코프 캐시 폐기 — 로그아웃·탈퇴 때 부른다.
     *
     * 병동 공용 기기를 전제하므로 앞사람의 잔여 횟수가 남아 보이면 안 된다
     * (spec `feature/auth` §계정 스코프 캐시 폐기).
     */
    fun clear() {
        _usage.value = null
    }

    private companion object {
        const val TAG = "NM393"
    }
}
