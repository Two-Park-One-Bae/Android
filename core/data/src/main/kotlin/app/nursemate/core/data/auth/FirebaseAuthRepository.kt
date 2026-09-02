package app.nursemate.core.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Auth 기반 세션.
 *
 * ## 세션은 리스너로만 갱신한다
 * `signIn`/`signOut` 뒤에 상태를 직접 써넣지 않고 [FirebaseAuth.AuthStateListener] 하나에 맡긴다.
 * SDK가 토큰 폐기·다른 화면에서의 로그아웃으로도 상태를 바꾸는데, 직접 쓰면 그런 변화를 놓쳐
 * 로그아웃됐는데 홈이 떠 있는 상태가 만들어진다.
 *
 * 리스너는 등록 즉시 현재 값으로 한 번 불린다 — 그래서 [AuthSession.Unknown]은 등록 전에만 보인다.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(private val auth: FirebaseAuth) : AuthRepository {

    private val _session = MutableStateFlow<AuthSession>(AuthSession.Unknown)
    override val session = _session.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _session.value = if (user == null) {
                AuthSession.SignedOut
            } else {
                // providerData 는 [0]이 항상 "firebase" 라 실제 공급자는 그다음에 있다.
                AuthSession.SignedIn(
                    uid = user.uid,
                    providerId = user.providerData.firstOrNull { it.providerId != FIREBASE_PROVIDER }?.providerId
                )
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> = runCatching {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    override suspend fun signInWithCustomToken(customToken: String): Result<Unit> = runCatching {
        auth.signInWithCustomToken(customToken).await()
        Unit
    }

    override suspend fun idToken(forceRefresh: Boolean): String? {
        val user = auth.currentUser ?: return null
        return user.getIdToken(forceRefresh).await().token
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private companion object {
        const val FIREBASE_PROVIDER = "firebase"
    }
}
