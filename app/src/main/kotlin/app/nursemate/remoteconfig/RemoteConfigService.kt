package app.nursemate.remoteconfig

import android.util.Log
import app.nursemate.BuildConfig
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfigSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Remote Config — 원격 제어 둘을 담당한다.
 *
 *  - **강제 업데이트**: 현재 버전이 [Key.MIN_SUPPORTED_VERSION] 미만이면 차단
 *  - **점검 모드**: [Key.MAINTENANCE_MODE] 가 켜지면 차단
 *
 * ## 정본이 없는 화면이다
 * `DESIGN.pen` 86개 프레임에 강제 업데이트·점검 화면이 **없다**(2026-09-19 전수 확인).
 * spec 에도 요구사항이 없다. iOS 가 정본 없이 만든 것을 옮겼다
 * (`iOS/Projects/App/Sources/Application/RemoteConfigGate/`). 정본이 생기면 그쪽이 이긴다.
 *
 * ## 키는 iOS 와 같은 이름을 쓴다 — **버전만 빼고**
 * 한 Firebase 프로젝트를 두 앱이 공유하므로 키가 갈리면 콘솔에서 두 벌을 관리하게 된다.
 * 점검 모드는 백엔드 사정이라 두 플랫폼이 같이 걸리는 게 맞아 그대로 공유한다.
 *
 * ⚠️ **버전은 공유하면 안 된다.** iOS 의 1.1.1 과 Android 의 1.0.0 은 아무 관계가 없는
 * 숫자다. 실제로 공유 키(`min_supported_version`)를 읽었더니 릴리스 앱에 **1.1.1** 이
 * 내려왔다 — 그대로 1.0.0 을 출시했으면 **전 사용자가 첫 실행에서 잠긴다.**
 * 릴리스 캐시가 12시간이라 콘솔을 고쳐도 복구가 그만큼 늦다.
 * 그래서 [Key.MIN_SUPPORTED_VERSION] 은 Android 전용 키를 쓴다.
 * 스토어 주소도 같은 이유로 [Key.PLAY_STORE_URL] 을 따로 둔다.
 *
 * (2026-09-19 기기 확인: 같은 키인데 `app.nursemate.debug` 는 1.0.0, `app.nursemate` 는
 * 1.1.1 이었다 — 콘솔 조건이 앱별로 갈려 있다.)
 */
@Singleton
class RemoteConfigService @Inject constructor() {

    object Key {
        /**
         * 지원하는 최소 **Android** 앱 버전(semver). 현재 버전이 이보다 낮으면 강제 업데이트.
         *
         * ⚠️ iOS 의 `min_supported_version` 을 읽지 않는다. 클래스 주석 참고 —
         * 그 키를 읽으면 iOS 의 릴리스 판단이 Android 사용자를 잠근다.
         *
         * **콘솔에 이 키가 없으면 게이트는 아무도 막지 않는다**(인앱 기본값 `0.0.0`).
         * 막을 이유가 확인되지 않은 상태라 그게 맞다.
         */
        const val MIN_SUPPORTED_VERSION = "min_supported_version_android"

        /** 점검 모드 on/off. */
        const val MAINTENANCE_MODE = "maintenance_mode"

        /** 점검 안내 문구. 비어 있으면 화면이 기본 문구를 쓴다. */
        const val MAINTENANCE_MESSAGE = "maintenance_message"

        /** 업데이트 버튼이 열 Play 스토어 주소(선택). 비어 있으면 `market://` 로 우리 앱을 연다. */
        const val PLAY_STORE_URL = "play_store_url"
    }

    private val config: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    // ⚠️ 여기서 read() 를 부르면 안 된다. 프로퍼티 초기화는 init 블록보다 **먼저** 돌아
    //    setDefaultsAsync 전에 읽게 되고, SDK 가 키마다
    //    `No value of type 'String' exists for parameter key ...` 를 찍는다(기기에서 확인).
    //    결과값은 어차피 비어 있어 아무도 막지 않지만, 그 상태를 **우연이 아니라 명시로** 둔다.
    private val _state = MutableStateFlow(RemoteConfigState.OPEN)

    /** 게이트 화면이 구독한다. 값이 바뀌면 떠 있는 중에도 따라가야 한다. */
    val state: StateFlow<RemoteConfigState> = _state.asStateFlow()

    init {
        config.setConfigSettingsAsync(
            remoteConfigSettings {
                // ⚠️ 이 값이 **원격 제어가 얼마나 늦게 먹히는가**를 그대로 정한다.
                //
                // debug 는 0 — 값을 바꾸고 바로 확인해야 한다.
                // release 는 12시간. Firebase 가 권장하는 프로덕션 값이고, 짧게 잡으면 시간당
                // 서버 쿼터에 걸린다(문서상 짧은 간격은 **개발용**이다).
                //
                // 이 간격보다 빨리 반영되는 경로는 실시간 리스너뿐인데 그건 보장 장치가 아니다
                // (아래 [startListening] 주석). 즉 **점검 모드가 최대 12시간 늦게 걸릴 수 있다.**
                // 그 지연이 문제가 되면 이 값을 낮추는 것보다 **백엔드가 점검 시 503 +
                // MAINTENANCE_MODE 를 내려주고 앱이 그걸 받아 게이트를 띄우는** 편이 낫다 —
                // 즉시 반영되고 추가 요청도 없다. iOS 도 같은 결론이다.
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else FETCH_INTERVAL_SECONDS
            }
        )

        // ⚠️ **인앱 기본값을 반드시 둔다.** 첫 실행이거나 네트워크가 막혔을 때 값이 비면
        //    게이트 판단이 흔들린다. 못 받았을 때는 **아무도 막지 않는 쪽**이 기본이어야 한다.
        config.setDefaultsAsync(
            mapOf(
                // ⚠️ **iOS 와 여기서 갈린다.** iOS 는 "1.0.0" 을 기본값으로 둔다
                // (`RemoteConfigService.swift`). 우리는 "0.0.0" 이다.
                //
                // iOS 값은 자기네 배포 버전이 이미 1.x 라 우연히 안 걸릴 뿐이고,
                // **기본값의 역할은 "못 받았을 때 아무도 막지 않는 것"** 이다. 실제로 이 코드를
                // "1.0.0" 으로 두고 0.2.5 빌드를 띄웠더니 개발 빌드가 그대로 막혔다.
                // 차단 판단은 **콘솔이 내려준 값만** 하게 둔다.
                Key.MIN_SUPPORTED_VERSION to "0.0.0",
                Key.MAINTENANCE_MODE to false,
                Key.MAINTENANCE_MESSAGE to "",
                Key.PLAY_STORE_URL to ""
            )
        )
    }

    /**
     * 서버에서 받아 활성화한다. 실패해도 기존(또는 기본) 값이 남는다.
     *
     * **반영을 책임지는 경로가 이것이다.** 앱 시작과 포그라운드 복귀에서 부른다.
     */
    suspend fun refresh(): Boolean {
        val changed = runCatching { config.fetchAndActivate().await() }
            .onFailure { Log.w(TAG, "Remote Config 를 못 받았다 — 기존 값을 쓴다", it) }
            .getOrDefault(false)
        _state.value = read()
        // ⚠️ **릴리스에서도 남긴다.** 게이트가 걸리면 사용자는 앱을 아예 못 쓰는데, 값을 못 보면
        //    「왜 막혔나」를 기기 없이는 알 수 없다. 실제로 1.0.0 빌드가 막혀서 원인을 찾는 데
        //    이 한 줄이 필요했다(2026-09-19). 개인정보가 아니라 콘솔 설정값이다.
        Log.i(TAG, "원격 값 = ${_state.value} · 현재 ${BuildConfig.VERSION_NAME}")
        return changed
    }

    /**
     * 서버가 값을 게시하면 받아 반영한다.
     *
     * ⚠️ **best-effort 다. 이것만 믿으면 안 된다.** iOS 에서 실기기 콜백이 한 번도 오지 않는
     * 경우를 확인했다(NM-423). SDK 소스에서 확인된 원인 후보:
     *
     *  - 스트림이 일반 fetch 와 **다른 호스트**를 쓴다. 거기만 막혀도 fetch 는 멀쩡해
     *    증상이 「가끔 안 된다」로 보인다.
     *  - **실패가 조용하다.** 상태코드를 로그로만 남기고 리스너에 전파하지 않는다.
     *  - 콜백은 서버 templateVersion 이 **클라이언트 보유 버전보다 클 때만** 돈다. 복귀 시
     *    [refresh] 가 먼저 버전을 올리면 리스너는 조용해진다 — 캐시가 짧을수록 더 그렇다.
     *
     * 그래서 이것은 [refresh] 위에 얹는 빠른 경로일 뿐이다.
     */
    fun startListening() {
        config.addOnConfigUpdateListener(
            object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    Log.i(TAG, "실시간 갱신 — 바뀐 키 ${configUpdate.updatedKeys}")
                    // 여기서는 activate 만 한다. 실시간 경로는 SDK 가 이미 fetch 를 끝내고
                    // activate 만 안 한 상태다. fetchAndActivate 를 부르면 손에 든 값을 두고
                    // 네트워크를 한 번 더 타 반영이 오히려 늦어진다.
                    config.activate().addOnSuccessListener { _state.value = read() }
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    Log.w(TAG, "실시간 수신 실패 — refresh() 가 대신 받는다", error)
                }
            }
        )
    }

    private fun read() = RemoteConfigState(
        minSupportedVersion = config.getString(Key.MIN_SUPPORTED_VERSION),
        maintenanceMode = config.getBoolean(Key.MAINTENANCE_MODE),
        maintenanceMessage = config.getString(Key.MAINTENANCE_MESSAGE),
        playStoreUrl = config.getString(Key.PLAY_STORE_URL)
    )

    private companion object {
        const val TAG = "NM467"
        const val FETCH_INTERVAL_SECONDS = 43_200L // 12시간
    }
}

/** 한 번에 읽은 원격 값. 게이트가 이것만 보고 판단한다. */
data class RemoteConfigState(
    val minSupportedVersion: String,
    val maintenanceMode: Boolean,
    val maintenanceMessage: String,
    val playStoreUrl: String
) {
    /** 현재 앱 버전이 최소 지원 버전보다 낮은가. */
    val forceUpdateRequired: Boolean
        get() = isLower(BuildConfig.VERSION_NAME, minSupportedVersion)

    companion object {
        /**
         * 아직 아무 값도 못 받은 상태. **아무도 막지 않는다.**
         *
         * 원격 제어는 「막을 이유가 확인됐을 때만」 막아야 한다. 값을 못 받은 것은
         * 막을 이유가 아니다 — 그걸 차단으로 해석하면 Firebase 가 흔들릴 때 전원이 갇힌다.
         */
        val OPEN = RemoteConfigState(
            minSupportedVersion = "0.0.0",
            maintenanceMode = false,
            maintenanceMessage = "",
            playStoreUrl = ""
        )

        /**
         * semver 를 **컴포넌트 단위 숫자로** 비교한다.
         *
         * ⚠️ 문자열 비교로 하면 `"1.10.0" < "1.2.0"` 이 나온다. 자릿수가 다른 마이너를
         * 올리는 순간 강제 업데이트가 거꾸로 걸린다.
         *
         * 버전명에 `-debug` 같은 접미사가 붙을 수 있어 숫자가 아닌 조각은 0 으로 본다.
         */
        fun isLower(current: String, required: String): Boolean {
            val a = current.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val b = required.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            repeat(maxOf(a.size, b.size)) { i ->
                val l = a.getOrElse(i) { 0 }
                val r = b.getOrElse(i) { 0 }
                if (l != r) return l < r
            }
            return false
        }
    }
}
