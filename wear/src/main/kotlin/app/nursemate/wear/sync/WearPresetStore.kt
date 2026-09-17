package app.nursemate.wear.sync

import android.util.Log
import app.nursemate.core.datalayer.TimerSyncTransport
import app.nursemate.core.model.DEFAULT_TIMER_PRESETS
import app.nursemate.core.model.PresetSnapshot
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.newerOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 화면·타일이 보는 프리셋 목록.
 *
 * ## 폰이 주인이지만, 폰이 없어도 빈손으로 두지 않는다
 * 값은 폰이 보낸 스냅샷에서 온다. 다만 **한 번도 못 받았으면 공용 기본값으로 떨어진다**
 * ([presetsOf]). 프리셋을 누르는 것이 워치에서 타이머를 시작하는 유일한 길이라, 빈 목록은
 * 곧 막다른 골목이다 — 매니페스트의 `standalone` 선언과도 어긋난다. 폰에 앱을 깔지
 * 않았거나 지운 사용자가 실제로 여기 걸렸다.
 *
 * ## 여기 타이머는 없다
 * 워치도 자기 타이머를 갖는다(`core:timer` 의 `TimerRepository`). 프리셋만 폰이 주인이라
 * (편집이 폰 전용 — spec §워치) 받아 읽기만 한다.
 *
 * ## 리스너와 화면이 같은 것을 봐야 한다
 * 프리셋은 앱이 꺼져 있어도 서비스로 도착한다. 그 서비스와 화면이 다른 인스턴스를 보면
 * 앱을 열었을 때 옛 값이 보인다. `@Singleton` 인 이유다.
 */
@Singleton
class WearPresetStore @Inject constructor(private val transport: TimerSyncTransport) {

    private val _snapshot = MutableStateFlow<PresetSnapshot?>(null)

    /** null = 아직 한 번도 못 받았다. */
    val snapshot: StateFlow<PresetSnapshot?> = _snapshot.asStateFlow()

    val presets: List<TimerPreset> get() = presetsOf(_snapshot.value)

    /**
     * 스냅샷을 **화면에 쓸 목록**으로 바꾼다.
     *
     * ⚠️ **한 번도 못 받았으면 공용 기본값으로 떨어진다.** 빈 목록을 그리면 워치는 막다른
     * 골목이 된다 — 프리셋을 누르는 것이 타이머를 시작하는 유일한 길이라, 목록이 비면
     * 「프리셋에서 시작하세요」를 눌러도 아무것도 없다. 매니페스트가 이 앱을
     * `standalone` 으로 선언한 것과도 어긋난다.
     *
     * 폰도 같은 자리에서 같은 값을 쓴다(`TimerStore`: 저장된 것이 없으면 `DEFAULT_TIMER_PRESETS`).
     * 그래서 폰을 아직 한 번도 안 연 사용자도 양쪽에서 **같은 여섯 개**를 본다.
     *
     * **받은 적이 있으면 그대로 따른다** — 빈 목록이어도 그것이 폰의 뜻이다(프리셋 주인은 폰).
     * 그 구분을 위해 [snapshot] 은 「못 받음(null)」과 「받았는데 0개」를 따로 둔다.
     *
     * 흐름으로 보는 쪽(뷰모델)과 값으로 보는 쪽(타일)이 **같은 규칙**을 타도록 한 자리에 둔다.
     */
    fun presetsOf(snapshot: PresetSnapshot?): List<TimerPreset> =
        snapshot?.presets ?: DEFAULT_TIMER_PRESETS

    /**
     * 새로 받은 것을 반영한다.
     *
     * ⚠️ **무조건 덮지 않는다.** 전달 순서가 뒤집혀 옛 것이 늦게 도착할 수 있어
     * `snapshotAt` 이 최신인 쪽만 채택한다.
     */
    fun offer(incoming: PresetSnapshot) {
        _snapshot.update { current ->
            val next = newerOf(current, incoming)
            if (next === incoming) {
                Log.i(TAG, "프리셋 반영 at=${incoming.snapshotAt} ${incoming.presets.size}개")
            } else {
                // 버리는 것도 기록해야 "왜 안 바뀌지"를 쫓을 수 있다.
                Log.i(TAG, "옛 프리셋을 버렸다 at=${incoming.snapshotAt} < ${current?.snapshotAt}")
            }
            next
        }
    }

    /**
     * 기기에 남아 있는 마지막 목록을 읽어 온다. 앱이 새로 뜰 때 한 번 부른다.
     *
     * DataItem 은 앱 수명과 무관하게 남아 있어, **폰이 꺼져 있어도** 마지막으로 본 목록을
     * 그릴 수 있다.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun restore() {
        try {
            // ⚠️ 못 읽은 것도 남긴다. 예전에는 null 이면 조용히 지나가, 목록이 왜 비었는지
            //    로그만 봐서는 알 수 없었다(폰이 안 보낸 것인지 읽기가 실패한 것인지).
            val latest = transport.latestPresets()
            if (latest == null) Log.i(TAG, "받아 둔 프리셋이 없다 — 기본값으로 간다") else offer(latest)
        } catch (t: Throwable) {
            Log.w(TAG, "마지막 프리셋을 못 읽었다", t)
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
