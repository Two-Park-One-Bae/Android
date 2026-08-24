package app.nursemate.core.data

/**
 * X-Device-Id 헤더용 기기 식별자 저장소.
 * P1(NM-392)에서 구현: 최초 실행 시 UUID 생성 → Preferences DataStore 영속.
 * iOS는 Keychain(재설치 생존)이지만 Android DataStore는 재설치 시 재발급 — spec 문구 중립화 논의(NM-391) 참조.
 */
interface DeviceIdRepository {
    suspend fun deviceId(): String
}
