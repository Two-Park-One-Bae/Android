package app.nursemate.remoteconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 키 이름을 고정한다. **이름 하나가 전 사용자를 잠글 수 있다.**
 *
 * 처음에는 iOS 와 같은 `min_supported_version` 을 읽었다. 그런데 콘솔 조건이 앱별로 갈려 있어
 * 릴리스 앱에 **iOS 기준인 1.1.1 이 내려왔고**, 그대로 1.0.0 을 출시했으면 첫 실행에서
 * 전원이 갇혔다(2026-09-19 기기 확인). 릴리스 캐시가 12시간이라 콘솔을 고쳐도 복구가 늦다.
 */
class RemoteConfigKeyTest {

    @Test
    fun `버전 키는 Android 전용이다`() {
        assertEquals("min_supported_version_android", RemoteConfigService.Key.MIN_SUPPORTED_VERSION)
    }

    @Test
    fun `버전 키는 iOS 와 공유하지 않는다`() {
        // iOS 의 1.1.1 과 Android 의 1.0.0 은 아무 관계가 없는 숫자다.
        // 공유하면 iOS 의 릴리스 판단이 Android 사용자를 잠근다.
        assertNotEquals("min_supported_version", RemoteConfigService.Key.MIN_SUPPORTED_VERSION)
    }

    @Test
    fun `점검 키는 iOS 와 공유한다`() {
        // 점검은 백엔드 사정이라 두 플랫폼이 같이 걸리는 게 맞다.
        assertEquals("maintenance_mode", RemoteConfigService.Key.MAINTENANCE_MODE)
        assertEquals("maintenance_message", RemoteConfigService.Key.MAINTENANCE_MESSAGE)
    }

    @Test
    fun `스토어 주소는 플랫폼별로 따로 둔다`() {
        assertEquals("play_store_url", RemoteConfigService.Key.PLAY_STORE_URL)
    }
}
