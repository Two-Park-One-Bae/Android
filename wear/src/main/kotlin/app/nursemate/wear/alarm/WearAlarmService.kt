package app.nursemate.wear.alarm

import android.app.Service
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
import app.nursemate.core.timer.TimerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
 * 저장소에서 울리는 타이머가 사라지면 끝낸다 — 워치에서 [완료] 를 눌러도, 폰에서 눌러
 * 복제로 넘어와도 결국 같은 저장소를 거치므로 멈추는 길이 하나다.
 */
@AndroidEntryPoint
class WearAlarmService : Service() {

    @Inject lateinit var repository: TimerRepository

    @Inject lateinit var notifier: WearTimerNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watching: Job? = null
    private var vibrating = false
    private var screenOnRegistered = false

    /**
     * 화면이 켜지는 순간 알람 화면을 **다시** 시도한다.
     *
     * 만료 시점에는 화면이 꺼져 있어 백그라운드 액티비티 시작이 막힌다(사정은 [showAlarmScreen]).
     * 그런데 손목을 들어 화면이 켜지면 그때는 시스템이 사용자를 마주한 상태라, 같은 시도가
     * 통할 여지가 생긴다. 알림도 함께 다시 올려 `fullScreenIntent` 를 재발사한다.
     *
     * 이게 없으면 사용자는 손목을 들어 **시계 화면**을 보고, 아래 작은 표시를 눌러야 만료에
     * 닿는다. 울리는 알람을 끄는 데 세 동작이 든다.
     */
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch {
                val ringing = repository.timers.first().filter { it.state == TimerState.RINGING }
                val lead = ringing.firstOrNull() ?: return@launch
                showAlarmScreen(lead)
                notifier.realert(ringing)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ringing = runBlocking { repository.timers.first() }.filter { it.state == TimerState.RINGING }
        if (ringing.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val lead = ringing.first()
        startForeground(WearTimerNotifier.FOREGROUND_ID, notifier.foregroundNotification(lead))
        showAlarmScreen(lead)
        startVibrating()
        watchScreen()
        watch()
        return START_STICKY
    }

    /**
     * 손목을 덮는 알람 화면을 띄워 본다 — **성공을 전제하지 않는다.**
     *
     * ⚠️ **포그라운드 서비스라는 자격만으로는 부족하다.** 여기 적혀 있던 「포그라운드 자격으로
     * 시작하는 액티비티는 막히지 않는다」는 틀렸다. 화면이 꺼진 실기기에서 이 호출이 그대로
     * 막혔다 — `callingUidProcState: FOREGROUND_SERVICE` 인데 `BAL_BLOCK` 이다.
     *
     * 화면이 켜져 있으면 통한다(`BAL_ALLOW_VISIBLE_WINDOW`). 그 경우 알림보다 먼저 떠서
     * 빠르므로 남겨 둔다.
     *
     * **화면이 꺼져 있을 때 실제로 띄우는 것은 Wear SysUI 다** — 알림을 알리기로 결정하면
     * 자기 자격으로 `fullScreenIntent` 를 발사한다. 조건은 [WearTimerNotifier] 에 적었다.
     */
    private fun showAlarmScreen(timer: CareTimer) {
        runCatching { startActivity(WearAlarmActivity.intent(this, timer.id, timer.alarmTitle)) }
            .onFailure { Log.w(TAG, "알람 화면을 띄우지 못했다 (${timer.id})", it) }
    }

    private fun watchScreen() {
        if (screenOnRegistered) return
        // `ACTION_SCREEN_ON` 은 매니페스트로 못 받는다 — 살아 있는 동안만 등록해 둔다.
        ContextCompat.registerReceiver(
            this,
            screenOn,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenOnRegistered = true
    }

    /** 울리는 것이 없어지는 순간 스스로 끝낸다. */
    private fun watch() {
        if (watching?.isActive == true) return
        watching = scope.launch {
            repository.timers.collect { timers ->
                val ringing = timers.filter { it.state == TimerState.RINGING }
                // ⚠️ **끝낼 때도 한 번 쓸고 나간다.** 평소에는 [완료] 가 스케줄러를 거치며
                // 알림을 걷지만, [WearTimerNotifier.realert] 는 지웠다 다시 올리는 두 단계라
                // 그 사이에 [완료] 가 들어오면 순서가 뒤집혀 완료된 타이머의 알림이 남는다.
                // 여기서 쓸면 어느 순서로 들어와도 남지 않는다.
                notifier.sync(timers)
                if (ringing.isEmpty()) stopSelf()
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
        if (screenOnRegistered) {
            runCatching { unregisterReceiver(screenOn) }
            screenOnRegistered = false
        }
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
