package app.nursemate.wear.sync

import android.util.Log
import app.nursemate.core.datalayer.TimerSyncTransport
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
 * 폰이 보낸 프리셋 목록.
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

    val presets: List<TimerPreset> get() = _snapshot.value?.presets.orEmpty()

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
            transport.latestPresets()?.let(::offer)
        } catch (t: Throwable) {
            Log.w(TAG, "마지막 프리셋을 못 읽었다", t)
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
