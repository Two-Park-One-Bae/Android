package app.nursemate.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * 개발 빌드용 App Check provider.
 *
 * Play Integrity 는 **Play 스토어로 설치된 앱**을 전제한다. debug 는 패키지가
 * `app.nursemate.debug` 라 Play 에 아예 없어서 판정을 통과할 수 없다.
 *
 * 대신 디버그 provider 가 기기마다 고유한 토큰을 만들고, 그 토큰을 Firebase 콘솔에
 * 등록한 기기에서만 App Check 가 통과한다. 토큰은 앱 첫 실행 시 Logcat 에 한 번 찍힌다:
 * ```
 * D/DebugAppCheckProvider: Enter this debug secret into the allow list...
 * ```
 * 재설치하면 값이 바뀌므로 다시 등록해야 한다.
 *
 * ⚠️ 이 파일은 **debug 소스셋에만** 있다. release APK 에는 클래스 자체가 들어가지 않는다.
 */
fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
