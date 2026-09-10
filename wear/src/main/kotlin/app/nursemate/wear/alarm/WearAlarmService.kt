package app.nursemate.wear.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TIMER_SUSTAINED_VIBRATION
import app.nursemate.core.model.TimerState
import app.nursemate.wear.sync.WearTimerStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 만료가 울리는 동안 도는 포그라운드 서비스 — spec §워치 "울림 방식과 무관하게 항상 햅틱".
 *
 * ## 왜 서비스까지 필요한가
 * **Wear 는 알림 채널의 진동 패턴을 무시한다.** 채널에 800·400 을 50번 넣어 60초짜리 패턴을
 * 줘도 시스템이 자기 햅틱을 **한 번** 재생하고 만다(실기기 확인). 폰에서는 같은 방법이
 * 통했다 — 플랫폼 차이다.
 *
 * spec 이 "[완료] 를 누를 때까지 지속"을 요구하므로, 진동을 우리가 직접 몰아야 한다.
 * [Vibrator] 를 반복 파형으로 돌리려면 프로세스가 살아 있어야 하고, 그래서 포그라운드
 * 서비스가 된다. 마침 Wear 품질요건 **WO-V4** 가 타이머에 진행 중 표시를 요구해서
 * ([androidx.wear.ongoing.OngoingActivity]) 같은 서비스가 그 몫도 한다.
 *
 * ## 스스로 멈춘다
 * 스냅샷에서 울리는 타이머가 사라지면 끝낸다 — 폰에서 [완료] 를 눌러도, 워치에서 눌러도
 * 결국 스냅샷으로 돌아오므로 멈추는 길이 하나다.
 */
@AndroidEntryPoint
class WearAlarmService : Service() {

    @Inject lateinit var store: WearTimerStore

    @Inject lateinit var notifier: WearTimerNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watching: Job? = null
    private var vibrating = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ringing = store.snapshot.value?.timers.orEmpty().filter { it.state == TimerState.RINGING }
        if (ringing.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(WearTimerNotifier.FOREGROUND_ID, notifier.foregroundNotification(ringing.first()))
        startVibrating()
        watch()
        return START_STICKY
    }

    /** 울리는 것이 없어지는 순간 스스로 끝낸다. */
    private fun watch() {
        if (watching?.isActive == true) return
        watching = scope.launch {
            store.snapshot.collect { snapshot ->
                val ringing = snapshot?.timers.orEmpty().filter { it.state == TimerState.RINGING }
                if (ringing.isEmpty()) {
                    stopSelf()
                } else {
                    notifier.sync(snapshot?.timers.orEmpty())
                }
            }
        }
    }

    /**
     * ⚠️ **반복 인덱스를 0 으로 준다.** `createWaveform(pattern, repeat)` 의 `repeat` 이
     * -1 이면 한 번만 재생한다 — 채널 패턴과 똑같은 결과가 된다. 0 이면 처음부터 무한히 돈다.
     */
    private fun startVibrating() {
        if (vibrating) return
        val vibrator = vibrator() ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(TIMER_SUSTAINED_VIBRATION, 0))
        vibrating = true
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService<Vibrator>()
    }

    override fun onDestroy() {
        watching?.cancel()
        // 진동을 반드시 끈다 — 서비스가 죽어도 파형은 계속 돈다.
        runCatching { vibrator()?.cancel() }
            .onFailure { Log.w(TAG, "진동을 멈추지 못했다", it) }
        vibrating = false
        super.onDestroy()
    }

    companion object {
        /** 울리는 타이머가 생겼을 때 부른다. 이미 돌고 있으면 아무 일도 안 한다. */
        fun start(context: Context, ringing: List<CareTimer>) {
            if (ringing.isEmpty()) return
            ContextCompat.startForegroundService(context, Intent(context, WearAlarmService::class.java))
        }

        private const val TAG = "NM444"
    }
}
