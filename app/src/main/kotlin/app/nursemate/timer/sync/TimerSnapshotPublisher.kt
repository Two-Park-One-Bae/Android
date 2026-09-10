package app.nursemate.timer.sync

import android.util.Log
import app.nursemate.core.data.timer.TimerClock
import app.nursemate.core.data.timer.TimerPresetRepository
import app.nursemate.core.data.timer.TimerRepository
import app.nursemate.core.datalayer.TimerSyncTransport
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerSnapshot
import app.nursemate.timer.alarm.TimerPermissions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 폰의 상태가 바뀔 때마다 워치로 전체 스냅샷을 보낸다 — spec §폰↔워치 동기화 계약.
 *
 * ## 부분 갱신을 하지 않는다
 * 바뀐 것만 보내면 워치가 놓친 조각을 영영 모른 채로 남는다. 전체를 보내면 한 번만 닿아도
 * 완전히 복구된다 — 블루투스로 오가는 값이라 유실을 전제해야 한다.
 *
 * ## 알람 사정을 함께 싣는다
 * 워치는 폰이 알람을 걸 수 있는지를 스스로 알 수 없다. `alarmAvailable`·`alarmAuthorized`
 * 로 알려 줘야 워치가 기능을 막을지, 시작할 때 권한 안내를 띄울지 정한다.
 */
@Singleton
class TimerSnapshotPublisher @Inject constructor(
    private val repository: TimerRepository,
    private val presetRepository: TimerPresetRepository,
    private val permissions: TimerPermissions,
    private val transport: TimerSyncTransport,
    private val clock: TimerClock
) {

    /**
     * 상태 변화를 따라가며 계속 보낸다. 앱 수명 동안 한 번만 부른다.
     *
     * `distinctUntilChanged` — 목록이 실제로 안 바뀌었는데 흐름이 다시 방출되는 경우가 있어
     * 그대로 두면 같은 값을 블루투스로 반복해 보낸다.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.timers, presetRepository.presets) { timers, presets -> timers to presets }
                .distinctUntilChanged()
                .collect { (timers, presets) -> send(timers, presets) }
        }
    }

    /**
     * 지금 상태를 한 번 보낸다.
     *
     * 목록이 안 바뀌어도 보내야 하는 때가 있다 — **권한만 바뀐 경우**다. 권한은 흐름이 아니라
     * 그때그때 물어보는 값이라 [start] 의 관찰에 걸리지 않는다.
     */
    suspend fun publishNow() {
        send(repository.timers.first(), presetRepository.presets.first())
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun send(timers: List<CareTimer>, presets: List<TimerPreset>) {
        val snapshot = TimerSnapshot(
            snapshotAt = clock.now(),
            timers = timers,
            presets = presets,
            // 이 폰은 항상 예약할 수 있다(정확 알람 API 가 있는 버전만 지원). 권한이 별개다.
            alarmAvailable = true,
            // 워치가 이 값 하나로 "지금 시작할 수 있는가"를 판단한다 — 위젯의
            // `canStartWithoutApp` 과 같은 기준이라야 세 표면이 어긋나지 않는다.
            alarmAuthorized = permissions.allGranted() && repository.alertModeChosen.first()
        )
        try {
            transport.publish(snapshot)
        } catch (t: Throwable) {
            // 워치가 없거나 꺼져 있으면 여기로 온다. 폰 기능에는 영향이 없어야 한다.
            Log.w(TAG, "스냅샷 전송 실패", t)
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
