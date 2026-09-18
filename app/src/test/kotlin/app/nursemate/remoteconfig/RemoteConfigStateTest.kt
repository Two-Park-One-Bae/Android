package app.nursemate.remoteconfig

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * semver 비교 — 강제 업데이트가 걸리고 풀리는 유일한 기준이다.
 *
 * 문자열 비교로 하면 `"1.10.0" < "1.2.0"` 이 나와 **마이너를 두 자리로 올리는 순간
 * 강제 업데이트가 거꾸로** 걸린다. 그 경우를 앞에 둔다.
 */
class RemoteConfigStateTest {

    private fun lower(a: String, b: String) = RemoteConfigState.isLower(a, b)

    @Test
    fun `자릿수가 다른 마이너를 문자열로 비교하지 않는다`() {
        assertTrue(lower("1.2.0", "1.10.0"))
        assertFalse(lower("1.10.0", "1.2.0"))
    }

    @Test
    fun `같은 버전은 막지 않는다`() {
        assertFalse(lower("1.0.0", "1.0.0"))
    }

    @Test
    fun `높은 버전은 막지 않는다`() {
        assertFalse(lower("1.0.1", "1.0.0"))
        assertFalse(lower("2.0.0", "1.9.9"))
    }

    @Test
    fun `낮은 버전은 막는다`() {
        assertTrue(lower("0.2.5", "1.0.0"))
        assertTrue(lower("1.0.0", "1.0.1"))
    }

    @Test
    fun `자리 수가 달라도 짧은 쪽을 0 으로 채워 비교한다`() {
        assertFalse(lower("1.0", "1.0.0"))
        assertTrue(lower("1.0", "1.0.1"))
        assertFalse(lower("1.0.0", "1.0"))
    }

    @Test
    fun `debug 접미사가 붙어도 숫자만 본다`() {
        // debug 빌드의 versionName 은 `0.2.5-debug` 다. 접미사 때문에 파싱이 깨지면
        // 개발 중에 게이트가 엉뚱하게 걸린다.
        assertTrue(lower("0.2.5-debug", "1.0.0"))
        assertFalse(lower("1.0.0-debug", "1.0.0"))
    }

    @Test
    fun `아무 값도 못 받은 초기 상태는 아무도 막지 않는다`() {
        // Firebase 가 흔들리거나 첫 실행이라 값이 없을 때 전원을 가두면 안 된다.
        // 원격 제어는 「막을 이유가 확인됐을 때만」 막는다.
        assertFalse(RemoteConfigState.OPEN.forceUpdateRequired)
        assertFalse(RemoteConfigState.OPEN.maintenanceMode)
    }

    @Test
    fun `값이 비어 있으면 막지 않는다`() {
        // 원격 값을 못 받았을 때 빈 문자열이 올 수 있다. 그때 전원을 가두면 안 된다.
        assertFalse(lower("1.0.0", ""))
    }
}
