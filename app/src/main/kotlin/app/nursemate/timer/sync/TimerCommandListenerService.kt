package app.nursemate.timer.sync

import android.util.Log
import app.nursemate.core.data.timer.TimerPresetRepository
import app.nursemate.core.data.timer.TimerRepository
import app.nursemate.core.datalayer.DataLayerPaths
import app.nursemate.core.model.TimerCommand
import app.nursemate.core.model.decodeCommand
import app.nursemate.timer.alarm.TimerPermissions
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 워치가 보낸 명령을 폰이 처리한다 — spec §명령.
 *
 * ## 상태를 바꾸는 건 폰뿐이다
 * 워치는 명령만 보내고, **결과는 다음 스냅샷으로 돌아오는 것**을 본다. 그래서 여기서
 * [TimerRepository] 를 그대로 통과시키면 알람 예약·울림 방식·저장이 한 묶음으로 지켜지고,
 * 바뀐 목록이 [TimerSnapshotPublisher] 를 통해 자동으로 워치에 돌아간다.
 *
 * ## `runBlocking` 이 맞는 자리다
 * `onMessageReceived` 는 이미 백그라운드 스레드에서 불리고, **돌아오는 순간 서비스가
 * 죽을 수 있다.** 코루틴을 띄워 보내면 처리 도중에 끊긴다.
 */
@AndroidEntryPoint
class TimerCommandListenerService : WearableListenerService() {

    @Inject lateinit var repository: TimerRepository

    @Inject lateinit var presetRepository: TimerPresetRepository

    @Inject lateinit var permissions: TimerPermissions

    @Suppress("TooGenericExceptionCaught")
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != DataLayerPaths.TIMER_COMMAND) return
        val command = decodeCommand(String(event.data)) ?: run {
            Log.w(TAG, "읽을 수 없는 명령을 버렸다")
            return
        }
        runBlocking {
            try {
                apply(command)
            } catch (t: Throwable) {
                Log.e(TAG, "명령 처리 실패 ($command)", t)
            }
        }
    }

    private suspend fun apply(command: TimerCommand) = when (command) {
        is TimerCommand.Start -> start(command.presetId)
        is TimerCommand.Pause -> repository.pause(command.timerId)
        is TimerCommand.Resume -> repository.resume(command.timerId)
        is TimerCommand.Remove -> repository.remove(command.timerId)
    }

    /**
     * ⚠️ **권한이 없으면 시작하지 않는다.** 시작시켜 봐야 예약이 조용히 실패해 "울릴 줄
     * 알았는데 안 울린" 상태가 된다(spec §알람 권한). 워치는 스냅샷의 `alarmAuthorized` 를
     * 보고 안내를 띄운다 — 여기서 굳이 되돌려 보내지 않아도 다음 스냅샷이 알려 준다.
     */
    private suspend fun start(presetId: String) {
        if (!permissions.allGranted()) {
            Log.w(TAG, "권한이 없어 워치 시작 요청을 거절했다 ($presetId)")
            return
        }
        val preset = presetRepository.presets.first().firstOrNull { it.id == presetId }
        if (preset == null) {
            // 워치가 들고 있던 프리셋이 폰에서 지워진 경우다. 다음 스냅샷이 목록을 맞춘다.
            Log.w(TAG, "없는 프리셋으로 시작 요청이 왔다 ($presetId)")
            return
        }
        repository.start(preset)
    }

    private companion object {
        const val TAG = "NM445"
    }
}
