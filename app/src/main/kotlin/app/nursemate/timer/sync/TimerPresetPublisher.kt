package app.nursemate.timer.sync

import android.util.Log
import app.nursemate.core.datalayer.TimerSyncTransport
import app.nursemate.core.model.PresetSnapshot
import app.nursemate.core.timer.TimerClock
import app.nursemate.core.timer.TimerPresetRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 프리셋이 바뀔 때마다 워치로 보낸다.
 *
 * ## 타이머는 여기로 안 간다
 * 예전에는 이 자리에서 타이머까지 통째로 보냈다. 지금은 양쪽이 각자 갖고 복제로 맞춘다
 * (`TimerReplicaPublisher`). 여기 남은 것은 **주인이 폰 하나뿐인 프리셋**이다 —
 * 편집이 폰 전용이라(spec §워치) 합칠 일이 없어 덮어쓰는 편이 단순하다.
 *
 * ## 알람 사정을 더 싣지 않는다
 * 예전에는 폰의 알람 권한 상태를 함께 보내 워치가 시작을 막게 했었다. 워치가 자기
 * 권한으로 자기 알람을 걸게 되면서 폰 사정을 알 이유가 없어졌다.
 */
@Singleton
class TimerPresetPublisher @Inject constructor(
    private val presetRepository: TimerPresetRepository,
    private val transport: TimerSyncTransport,
    private val clock: TimerClock
) {

    /**
     * 프리셋 변화를 따라가며 계속 보낸다. 앱 수명 동안 한 번만 부른다.
     *
     * `distinctUntilChanged` — 목록이 실제로 안 바뀌었는데 흐름이 다시 방출되는 경우가 있어
     * 그대로 두면 같은 값을 블루투스로 반복해 보낸다.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            presetRepository.presets.distinctUntilChanged().collect(::send)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun send(presets: List<app.nursemate.core.model.TimerPreset>) {
        try {
            transport.publishPresets(PresetSnapshot(snapshotAt = clock.now(), presets = presets))
        } catch (t: Throwable) {
            // 워치가 없거나 꺼져 있으면 여기로 온다. 폰 기능에는 영향이 없어야 한다.
            Log.w(TAG, "프리셋 전송 실패", t)
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
