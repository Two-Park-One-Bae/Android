package app.nursemate.core.data.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * 인증 세션.
 *
 * 스펙 `spec/feature/auth/README.md` §진입 라우팅이 세션 유무로 화면을 가르므로,
 * "아직 모른다"([Unknown])와 "없다"([SignedOut])를 반드시 구분한다.
 * 둘을 합치면 앱 시작 직후 복원이 끝나기 전에 로그인 화면이 한 번 번쩍인다.
 */
sealed interface AuthSession {
    /** 복원 전. 스플래시를 유지한다. */
    data object Unknown : AuthSession

    data object SignedOut : AuthSession

    /**
     * @param uid Firebase UID. 서버의 `userId`와 같은 값이다(api/domains/auth.md 회원).
     * @param providerId `google.com` · `apple.com` · `custom`(카카오). 없을 수도 있어 nullable.
     */
    data class SignedIn(val uid: String, val providerId: String?) : AuthSession
}

/**
 * 로그인 실패 사유 — 화면이 문구를 고르는 기준.
 *
 * ⚠️ **사유를 합치면 사용자가 할 수 있는 일이 사라진다.** 예전에는 [Cancelled]·[NoCredential] 말고
 * 전부 [Unknown] 이라 401 과 503 이 같은 칸에 떨어졌고, 화면은 늘 「잠시 후 다시 시도해 주세요」만
 * 말했다. 카카오가 일시 장애일 때 구글·애플로는 멀쩡히 로그인되는데 그 길을 안내할 수 없었다.
 */
sealed interface AuthError {
    /** 사용자가 계정 선택을 닫았다. 오류 문구를 띄우지 않는다. */
    data object Cancelled : AuthError

    /** 기기에 쓸 수 있는 구글 계정이 없다. 계정 추가를 안내한다. */
    data object NoCredential : AuthError

    /**
     * 401 `KAKAO_TOKEN_INVALID` — 서버가 카카오 액세스 토큰을 거절했다.
     *
     * 만료·위조이거나 **우리 카카오 앱에서 발급된 토큰이 아니다**(앱 ID 불일치).
     * 스펙의 클라이언트 대응이 「카카오 재로그인 후 재시도」다(`api/domains/errors.md`).
     */
    data object KakaoTokenInvalid : AuthError

    /**
     * 503 `SERVICE_UNAVAILABLE` — 카카오·Firebase 일시 장애.
     *
     * **세션 문제가 아니다.** 다른 공급자로는 로그인되므로 화면이 그 길을 함께 안내한다.
     */
    data object ServiceUnavailable : AuthError

    /** 그 밖(네트워크·Play 서비스·Firebase). @param cause 로그용, 사용자에게 그대로 보여주지 않는다. */
    data class Unknown(val cause: Throwable) : AuthError
}

interface AuthRepository {
    /** 앱 전역에서 하나만 흐른다. 시작값은 [AuthSession.Unknown]. */
    val session: StateFlow<AuthSession>

    /**
     * 구글 ID 토큰으로 Firebase에 로그인한다.
     *
     * 토큰을 **얻는 일**은 UI 레이어(Credential Manager)가 하고, 여기서는 받은 토큰을 쓰기만 한다 —
     * 자격 증명 선택 UI가 Activity를 요구해서 데이터 레이어에 둘 수 없다.
     */
    suspend fun signInWithGoogle(idToken: String): Result<Unit>

    /**
     * 서버가 발급한 Firebase Custom Token 으로 로그인한다(카카오 경로).
     *
     * 카카오는 Firebase 네이티브 공급자가 아니라, 서버가 카카오 토큰을 검증하고 Custom Token 을
     * 만들어 준다. 결과물은 구글·애플과 같은 Firebase ID 토큰이라 **이후 흐름이 동일하다.**
     */
    suspend fun signInWithCustomToken(customToken: String): Result<Unit>

    /**
     * 애플로 로그인한다.
     *
     * ## 여기만 [Activity]를 받는다
     * 구글·카카오는 UI 레이어가 토큰을 얻어 오고 이 레이어는 받아 쓰기만 한다. 애플은 그럴 수 없다 —
     * 안드로이드에는 애플 SDK가 없어서 **Firebase가 직접 웹 플로우 창을 띄우고**(Custom Tab),
     * 토큰은 우리 손을 거치지 않은 채 세션이 성립한다. 창을 띄우려면 Activity가 있어야 한다.
     *
     * 대안은 앱 레이어가 `FirebaseAuth`를 직접 부르는 것인데, 그러면 세션을 바꾸는 곳이 둘로
     * 갈린다. 데이터 레이어에 Activity가 들어오는 쪽이 덜 나쁘다고 봤다.
     *
     * ## 애플 쪽 설정이 있어야 동작한다
     * Firebase 콘솔의 Apple 공급자에 **Service ID `app.nursemate.signin`** 과 키가 등록돼 있어야 하고,
     * 그 Service ID에 `https://<프로젝트>.firebaseapp.com/__/auth/handler`가 리턴 URL로 등록돼
     * 있어야 한다. Apple은 https 리디렉션만 받으므로 이 중계 페이지를 뺄 수 없다.
     *
     * @param activity 웹 플로우를 띄울 Activity
     */
    suspend fun signInWithApple(activity: Activity): Result<Unit>

    /**
     * 애플 로그인 도중 프로세스가 죽었을 때 SDK에 남아 있는 결과를 이어받는다.
     *
     * 브라우저에 다녀오는 동안 우리 앱은 백그라운드라 메모리 회수 대상이다. 그 사이 죽으면
     * 사용자는 애플 인증을 마쳤는데 앱은 로그인 화면으로 돌아온다. 그대로 두면 방금 끝낸
     * 로그인을 또 하게 되므로, 화면이 뜰 때 남은 결과가 있는지 본다.
     *
     * @return 이어받을 게 없으면 null
     */
    suspend fun resumeAppleSignIn(): Result<Unit>?

    /**
     * `Authorization: Bearer`에 실을 Firebase ID 토큰.
     *
     * @param forceRefresh 서버가 401을 준 뒤의 **단 한 번**의 재시도에만 true를 쓴다
     *                     (spec §토큰·세션). 평소에는 SDK가 만료 전 알아서 갱신한다.
     * @return 로그인 상태가 아니면 null
     */
    suspend fun idToken(forceRefresh: Boolean = false): String?

    suspend fun signOut()
}
