package app.nursemate.core.timer

import javax.inject.Qualifier

/**
 * 복제본에서 이 기기를 부르는 이름 — `ORIGIN_PHONE` 또는 `ORIGIN_WATCH`.
 *
 * 폰과 워치가 **같은 저장소 코드를 쓴다.** 자기가 누구인지는 각 앱이 알려 준다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReplicaOrigin
