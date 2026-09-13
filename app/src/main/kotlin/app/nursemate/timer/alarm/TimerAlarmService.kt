package app.nursemate.timer.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.nursemate.core.model.AlertMode
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
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
 * 만료가 울리는 동안 도는 포그라운드 서비스 — 워치 `WearAlarmService` 와 같은 구조다.
 *
 * ## 왜 알림 채널에 맡기지 않나
 * **알림 채널의 소리·진동은 링어 모드에 걸린다.** 기기가 무음이면 채널에 알람음과 진동
 * 패턴이 제대로 들어 있어도 아무것도 나지 않는다(실기기 확인: 채널이
 * `mVibrationEnabled=true` 인데 `Ringer mode: SILENT` 이면 진동 요청 자체가 없다).
 *
 * spec §만료·알람은 "기기 무음·벨소리 스위치와 무관하게 울린다(시계 알람과 동일)"를
 * 요구한다. 채널로는 못 지키므로 **우리가 직접 낸다** — `USAGE_ALARM` 으로 알람 스트림·
 * 알람 진동을 쓰면 무음을 넘는다. 그러려면 프로세스가 살아 있어야 하고, 그래서 포그라운드
 * 서비스가 된다.
 *
 * ## 하나씩 순서대로
 * 여러 개가 함께 울려도 알림은 **맨 앞 하나**다([TimerAlarmNotification]). 맨 앞이 바뀌면
 * 같은 알림을 갈아 끼운다 — 각자 올리면 배너가 쌓이고 맨 위가 나중에 울린 것이 된다.
 * 「맨 앞」의 뜻은 화면·워치와 같다([CareTimerTransitions.projectedAndOrdered] 의 첫 번째).
 *
 * ## 스스로 멈춘다
 * 저장소에서 울리는 타이머가 사라지면 끝낸다 — 폰에서 [완료] 를 눌러도, 워치에서 눌러
 * 복제로 넘어와도 같은 저장소를 거치므로 멈추는 길이 하나다.
 */
@AndroidEntryPoint
class TimerAlarmService : Service() {

    @Inject lateinit var repository: TimerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watching: Job? = null
    private var player: MediaPlayer? = null
    private var vibrating = false
    private var leadId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ⚠️ **정렬해서 맨 앞을 고른다.** `repository.timers` 는 UUID 순이라 그냥 `first()` 를
        // 쓰면 「가장 오래 놓친 것」이 아니다. 그 값이 알림과 울림의 주인이 되므로 화면·워치와
        // 같은 규칙을 따라야 한다.
        val restore = intent?.getBooleanExtra(EXTRA_RESTORE, false) == true
        val (timers, mode) = runBlocking { repository.timers.first() to repository.alertMode.first() }
        val lead = ringingInOrder(timers).firstOrNull()
        if (lead == null) {
            Log.i(TAG, "울릴 것이 없다 — 시작하지 않는다")
            stopSelf()
            return START_NOT_STICKY
        }
        // ⚠️ **여기서는 [startForeground] 를 건너뛸 수 없다.** `startForegroundService` 한 번에
        // 하나씩 짝이 맞아야 하고, 10초 안에 안 부르면 시스템이 앱을 죽인다. 그래서 「다시
        // 알리지 않는다」는 게시를 거르는 대신 [TimerAlarmNotification] 쪽에서 판정을 끈다.
        post(lead, alertAgain = restore || lead.id != leadId)
        startAlerting(mode)
        watch()
        return START_STICKY
    }

    /** 맨 앞 하나만 알림을 갖는다. 바뀌면 같은 id 를 갈아 끼운다. */
    private fun post(lead: CareTimer, alertAgain: Boolean) {
        Log.i(TAG, "만료 알림을 ${lead.id} 로 맞춘다 (다시 알림=$alertAgain)")
        leadId = lead.id
        startForeground(TimerAlarmNotification.ID, TimerAlarmNotification.build(this, lead, alertAgain))
    }

    /** 울리는 것을 **화면·워치와 같은 규칙**으로 세운다 — 맨 앞이 가장 오래 놓친 것이다. */
    private fun ringingInOrder(timers: List<CareTimer>): List<CareTimer> =
        CareTimerTransitions.projectedAndOrdered(timers, System.currentTimeMillis())
            .filter { it.state == TimerState.RINGING }

    /** 울리는 것이 없어지는 순간 스스로 끝내고, 맨 앞이 바뀌면 알림을 갈아 끼운다. */
    private fun watch() {
        if (watching?.isActive == true) return
        watching = scope.launch {
            repository.timers.collect { timers ->
                val lead = ringingInOrder(timers).firstOrNull()
                when {
                    lead == null -> {
                        Log.i(TAG, "울리는 것이 없어 멈춘다")
                        stopSelf()
                    }

                    // 같은 타이머면 다시 올리지 않는다 — 올리면 헤드업이 다시 뜬다.
                    lead.id != leadId -> post(lead, alertAgain = true)
                }
            }
        }
    }

    /** [AlertMode.SOUND] 는 **소리 + 진동**이다 — 소리만 내면 주머니 속에서 놓친다. */
    private fun startAlerting(mode: AlertMode) {
        when (mode) {
            AlertMode.SOUND -> {
                startSound()
                startVibrating()
            }

            AlertMode.VIBRATE -> startVibrating()

            // 화면 알림만 남는다. 알림 자체는 방식과 무관하게 뜬다(spec §울림 방식).
            AlertMode.SILENT -> Unit
        }
    }

    /**
     * 기기 알람음을 **알람 스트림으로** 반복 재생한다.
     *
     * `USAGE_ALARM` 이라 무음 모드에서도 난다 — 무음이 막는 것은 벨소리·알림 스트림이다.
     */
    private fun startSound() {
        if (player != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@TimerAlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            Log.w(TAG, "알람음을 재생하지 못했다", it)
            player = null
        }
    }

    /**
     * ⚠️ **반복 인덱스를 0 으로 준다.** `-1` 이면 파형을 한 번만 재생한다. [완료] 까지
     * 이어져야 하므로 무한 반복으로 걸고 [onDestroy] 에서 끊는다.
     */
    private fun startVibrating() {
        if (vibrating) return
        val vibrator = vibrator()?.takeIf { it.hasVibrator() } ?: return
        val effect = VibrationEffect.createWaveform(TIMER_SUSTAINED_VIBRATION, 0)
        runCatching {
            // 「알람 용도」로 걸어야 무음 모드를 통과한다. API 33 부터 전용 타입이 생겼고
            // 그 아래에서는 오디오 속성으로 같은 뜻을 전한다.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            vibrating = true
        }.onFailure { Log.w(TAG, "진동을 걸지 못했다", it) }
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }

    override fun onDestroy() {
        watching?.cancel()
        runCatching { player?.stop() }.onFailure { Log.w(TAG, "알람음을 멈추지 못했다", it) }
        runCatching { player?.release() }
        player = null
        // 진동을 반드시 끈다 — 서비스가 죽어도 파형은 계속 돈다.
        runCatching { vibrator()?.cancel() }.onFailure { Log.w(TAG, "진동을 멈추지 못했다", it) }
        vibrating = false
        super.onDestroy()
    }

    companion object {
        /**
         * 울리는 타이머가 생겼을 때 부른다. 이미 돌고 있으면 알림만 갈아 끼운다.
         *
         * @param restore 사용자가 [지우기] 로 지운 알림을 되돌리는 길. 맨 앞이 그대로여도
         *   다시 띄워야 하므로 판정을 살린다.
         */
        fun start(context: Context, restore: Boolean = false) {
            val intent = Intent(context, TimerAlarmService::class.java).putExtra(EXTRA_RESTORE, restore)
            ContextCompat.startForegroundService(context, intent)
        }

        private const val EXTRA_RESTORE = "restore"

        private const val TAG = "TimerAlarm"
    }
}
