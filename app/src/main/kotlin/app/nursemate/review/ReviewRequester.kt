package app.nursemate.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 스토어 평가 창을 **요청**한다 — NM-561, spec §스토어 리뷰 요청(NM-618).
 *
 * ## 요청할 뿐 띄우는 것은 Play 다
 * Play In-App Review 는 요청을 받을 뿐 창을 띄울지는 Play 가 정하고(할당량 · 비공개), 앱은 떴는지도 별점을 줬는지도 알 수
 * 없다. 그래서 「떴으니 다시 묻지 않는다」 같은 분기는 없다 — 간격은 [ReviewTracker.decide] 가 요청 기준으로 센다.
 * **Play 로 설치된 앱에서만** 창이 뜬다. 사이드로드 · 디버그에서는 요청이 성공해도 아무것도 안 보인다.
 *
 * ## 앱이 앞에 있을 때만 부른다
 * 창은 액티비티 위에 뜬다. 앱 밖(알림 · 전체화면 알람 · 워치)에서 타이머를 끝내면 표시만 해 두고 다음에 앱을 열 때 검사한다.
 *
 * 하지 않는 것: 평가 창 앞에 만족도 질문 · 버튼으로 창 부르기 · 설정의 「리뷰 남기기」(spec).
 */
@Singleton
class ReviewRequester @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tracker: ReviewTracker
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { ReviewManagerFactory.create(context) }

    /** 앞에 떠 있는 메인 액티비티. 붙잡아 두지 않으려고 약하게 든다. */
    private var foreground: WeakReference<Activity>? = null

    /** 앱이 앞으로 왔다 — 쓴 날로 세고, 앱 밖에서 끝낸 타이머가 있었으면 지금 검사한다. */
    fun onForeground(activity: Activity) {
        foreground = WeakReference(activity)
        scope.launch {
            tracker.used(System.currentTimeMillis())
            if (tracker.consumeEndedOutside()) request(ReviewTrigger.TIMER_ENDED)
        }
    }

    fun onBackground(activity: Activity) {
        if (foreground?.get() === activity) foreground = null
    }

    /**
     * 타이머가 시작됐다(폰 · 위젯 · 워치). [startedAtMs] 그날로 센다. 부른 쪽 코루틴 안에서 쓴다 — 위젯 리시버의
     * `goAsync` 가 끝나면 프로세스가 거둬질 수 있다.
     */
    suspend fun timerStarted(startedAtMs: Long) {
        tracker.timerStarted(startedAtMs)
    }

    /**
     * 타이머를 끝까지 울린 뒤 「완료」했다. 앱 안(앱이 앞에 있음)이면 지금, 앱 밖(알림 · 전체화면 알람 · 워치)이면
     * 다음에 앱을 열 때 검사한다.
     *
     * @param outside 워치에서 끝냈다 — 폰 앱이 앞에 있어도 앱 밖이다. 맥락 없이 창을 띄우지 않는다
     */
    suspend fun timerCompleted(outside: Boolean) {
        if (outside || foreground?.get() == null) {
            tracker.timerEndedOutside()
        } else {
            scope.launch { request(ReviewTrigger.TIMER_ENDED) }
        }
    }

    /** 식별 기록 저장에 성공하고 들어온 화면으로 돌아왔다. */
    fun identified(unmodified: Boolean) {
        scope.launch {
            tracker.identified()
            request(ReviewTrigger.IDENTIFIED, unmodified)
        }
    }

    /** 오늘 한도 안내를 봤다. */
    fun limitShown() {
        scope.launch { tracker.limitShown() }
    }

    /**
     * 디버그 전용 — 조건을 건너뛰고 요청한다(`nursemate-debug://review`). 간격 기록은 바꾸지 않는다.
     * `MainActivity` 가 앞으로 온 뒤(onStart) 부른다 — 그 전에는 창을 띄울 액티비티가 없다.
     */
    fun forceForDebug() {
        scope.launch {
            Log.i(TAG, "디버그 강제 요청 | ${tracker.snapshot()}")
            foreground?.get()?.let { launch(it) }
        }
    }

    private suspend fun request(trigger: ReviewTrigger, unmodified: Boolean = false) {
        val bundle = if (foreground?.get() != null) tracker.decide(trigger, unmodified) else null
        Log.i(TAG, "리뷰 검사 $trigger — ${bundle?.let { "묶음 $it" } ?: "요청 안 함"} | ${tracker.snapshot()}")
        // 판정하는 사이 앱을 떠났으면 부르지 않는다 — 간격도 쓰지 않는다.
        val activity = foreground?.get()
        if (bundle == null || activity == null) return
        if (launch(activity)) tracker.requested()
    }

    /** @return 요청까지 갔는가 — 창이 떴는지는 Play 만 안다 */
    @Suppress("TooGenericExceptionCaught") // Play 쪽 무엇이 실패하든 앱 흐름으로 번지지 않게 — 취소만 되던진다
    private suspend fun launch(activity: Activity): Boolean = try {
        val info = manager.requestReview()
        manager.launchReview(activity, info)
        Log.i(TAG, "평가 창 요청 끝 — 떴는지는 Play 만 안다")
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "평가 창 요청 실패 — 다시 시도하지 않는다: ${e.message}")
        false
    }

    private companion object {
        const val TAG = "NM561"
    }
}

/** 컴포저블 · 브로드캐스트처럼 주입이 닿지 않는 자리에서 꺼내 쓴다. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReviewEntryPoint {
    fun reviewRequester(): ReviewRequester
}
