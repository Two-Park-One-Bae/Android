package app.nursemate.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.nursemate.R
import app.nursemate.core.data.auth.AuthError
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 구글 ID 토큰 획득 — Credential Manager.
 *
 * ## 왜 Credential Manager인가
 * 예전 `GoogleSignInClient`(play-services-auth)는 2025년에 지원이 끝났다. 지금 안드로이드의
 * 구글 로그인 경로는 Credential Manager 하나뿐이다.
 *
 * ## `GetSignInWithGoogleOption`을 쓴다 (`GetGoogleIdOption` 아님)
 * 디자인 정본의 로그인 화면은 **"Google로 계속하기" 버튼을 눌러야** 로그인이 시작된다.
 * `GetGoogleIdOption`은 화면 진입만으로 뜨는 원탭용이라 버튼을 무의미하게 만들고,
 * 저장된 계정이 없으면 `NoCredentialException`으로 조용히 실패한다.
 * `GetSignInWithGoogleOption`은 항상 계정 선택기를 띄우므로 버튼 UX와 맞는다.
 *
 * ## serverClientId는 **웹** 클라이언트 ID다
 * Android OAuth 클라이언트 ID가 아니다 — 여기에 Android 쪽을 넣으면 토큰 발급이 실패한다.
 * `default_web_client_id`는 google-services 플러그인이 `google-services.json`의
 * `client_type: 3`(web) 항목에서 생성한다. 빌드 타입에 따라 dev/prod 값이 자동으로 갈린다.
 */
@Singleton
class GoogleIdTokenProvider @Inject constructor(private val credentialManager: CredentialManager) {

    /**
     * @param activityContext **Activity** 컨텍스트여야 한다. 자격 증명 선택 UI를 그 위에 띄우므로
     *                        application 컨텍스트를 넘기면 실행 중 예외가 난다.
     * @return 성공하면 구글 ID 토큰, 실패하면 [AuthError]
     */
    suspend fun request(activityContext: Context): Result<String> = runCatching {
        val option = GetSignInWithGoogleOption
            .Builder(activityContext.getString(R.string.default_web_client_id))
            .build()

        val response = credentialManager.getCredential(
            context = activityContext,
            request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        )

        val credential = response.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "예상치 못한 자격 증명 타입: ${credential.type}"
        }
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}

/** Credential Manager 예외를 화면이 분기할 수 있는 형태로 바꾼다. */
fun Throwable.toAuthError(): AuthError = when (this) {
    is GetCredentialCancellationException -> AuthError.Cancelled
    is NoCredentialException -> AuthError.NoCredential
    else -> AuthError.Unknown(this)
}
