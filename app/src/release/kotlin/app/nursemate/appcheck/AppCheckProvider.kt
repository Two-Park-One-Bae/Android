package app.nursemate.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * 배포 빌드용 App Check provider.
 *
 * Play Integrity 가 앱 서명·패키지·기기 상태를 판정해 Google 이 서명한 토큰을 준다.
 * Firebase 가 그걸 자기 토큰으로 교환하고, 서버가 JWKS 로 검증한다
 * (백엔드 `FirebaseAppCheckVerifier`).
 *
 * ⚠️ 이 파일은 **release 소스셋에만** 있다. debug 쪽 동명 함수와 짝을 이룬다.
 */
fun appCheckProviderFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
