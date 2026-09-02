package app.nursemate.auth

import app.nursemate.core.data.auth.AuthError
import com.google.firebase.auth.FirebaseAuthException

/**
 * 애플 로그인 예외를 화면이 분기할 수 있는 형태로 바꾼다.
 *
 * 구글·카카오와 달리 예외 **타입**으로는 취소를 가려낼 수 없다. Firebase가 웹 플로우의 모든 실패를
 * `FirebaseAuthException` 하나로 싸서 던지므로 [FirebaseAuthException.getErrorCode] 문자열을 본다.
 */
fun Throwable.toAppleAuthError(): AuthError = when {
    // 사용자가 Custom Tab을 닫았다. 오류 문구를 띄우지 않는다.
    this is FirebaseAuthException && errorCode == ERROR_WEB_CONTEXT_CANCELED -> AuthError.Cancelled

    else -> AuthError.Unknown(this)
}

private const val ERROR_WEB_CONTEXT_CANCELED = "ERROR_WEB_CONTEXT_CANCELED"
