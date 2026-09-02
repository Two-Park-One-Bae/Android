package app.nursemate.core.data.auth

import android.util.Log
import app.nursemate.core.network.auth.AppCheckTokenProvider
import com.google.firebase.appcheck.FirebaseAppCheck
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Firebase App Check 토큰 어댑터.
 *
 * `getAppCheckToken(false)` 는 유효한 토큰이 있으면 캐시된 값을 준다 — 매 요청마다
 * Play Integrity 를 부르지 않는다. 강제 갱신은 쓰지 않는다(할당량을 태운다).
 *
 * provider 설치는 `:app` 의 Application 이 빌드 타입별로 한다.
 */
@Singleton
class FirebaseAppCheckTokenProvider @Inject constructor(private val appCheck: FirebaseAppCheck) :
    AppCheckTokenProvider {

    override suspend fun token(): String? = runCatching {
        appCheck.getAppCheckToken(false).await().token
    }.onFailure {
        // 실패해도 요청은 나간다. 서버가 401 APP_CHECK_FAILED 로 알려 준다.
        Log.w(TAG, "App Check 토큰 발급 실패", it)
    }.getOrNull()

    private companion object {
        const val TAG = "NM392"
    }
}
