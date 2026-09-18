package app.nursemate.telemetry

import app.nursemate.core.data.auth.AuthRepository
import app.nursemate.core.data.auth.AuthSession
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 크래시·지표에 회원 식별자를 붙인다.
 *
 * 정본은 spec `feature/auth/README.md` §지표·크래시 식별자 (NM-461).
 * **Analytics·Crashlytics 사용자 ID = Firebase UID** 로 고정한다 — 서버 로그·크래시·지표가
 * 한 값으로 이어져야 "이 사용자가 겪은 일"을 가로질러 볼 수 있다.
 *
 * ## 로그아웃에 반드시 따라붙는다
 * 끊지 않으면 다음 사용자의 크래시가 이전 사용자 ID 로 올라간다. 기기를 넘겨 쓰는 경우가
 * 드물더라도 그건 **잘못된 귀속**이라 지표를 믿을 수 없게 만든다.
 *
 * ## 남는 경계
 * 해제는 이후 결합만 끊는다 — **이미 올라간 리포트는 남는다.** 지우려면 Firebase 지원에
 * 문의해야 한다. 탈퇴 시 삭제 범위와 어긋나는 지점이라 spec §탈퇴와 나란히 읽어야 한다.
 *
 * @see AuthSession 세션은 앱 전역에서 하나만 흐른다.
 */
@Singleton
class TelemetryIdentity @Inject constructor(
    private val authRepository: AuthRepository,
    private val crashlytics: FirebaseCrashlytics,
    private val analytics: FirebaseAnalytics
) {

    /**
     * 세션을 따라가며 식별자를 맞춘다.
     *
     * [AuthSession.Unknown] 은 건너뛴다 — 복원 전 상태라 "로그아웃"이 아니다.
     * 여기서 해제하면 앱을 켤 때마다 잠깐 결합이 끊겼다 붙어, 그 사이 크래시가 익명으로 남는다.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            authRepository.session
                .map { session -> (session as? AuthSession.SignedIn)?.uid }
                .distinctUntilChanged()
                .collect(::apply)
        }
    }

    private fun apply(uid: String?) {
        crashlytics.setUserId(uid.orEmpty())
        analytics.setUserId(uid)
    }
}
