package app.nursemate.wear.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.nursemate.core.timer.TimerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 워치 알림의 [완료] — 폰에 삭제 명령을 보낸다.
 *
 * ## 여기서 알림을 지우지 않는다
 * 워치는 상태를 직접 바꾸지 않는다(spec §명령). 폰이 처리하면 다음 스냅샷에서 타이머가
 * 사라지고, 그때 [WearTimerNotifier.sync] 가 알림을 걷는다. 여기서 먼저 지우면 폰이
 * 거절했을 때(이미 지워졌다든지) 알림만 사라지고 타이머는 남는다.
 */
@AndroidEntryPoint
class WearTimerActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TimerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // goAsync() 를 쓰면 무슨 일이 있어도 finish() 에 도달해야 한다 — 안 그러면 시스템이
    // 리시버를 붙잡고 있다가 ANR 로 죽인다.
    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE) return
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                // 워치가 자기 저장소를 직접 고친다 — 폰에 닿지 않아도 완료는 완료다.
                // 폰에는 복제가 알리고, 끊겨 있으면 다시 붙을 때 전해진다.
                repository.remove(timerId)
            } catch (t: Throwable) {
                Log.e(TAG, "완료 처리 실패 ($timerId)", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun completeIntent(context: Context, timerId: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            WearTimerNotifier.notificationId(timerId),
            Intent(context, WearTimerActionReceiver::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_TIMER_ID, timerId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private const val TAG = "NM444"
        private const val ACTION_COMPLETE = "app.nursemate.wear.ALARM_COMPLETE"
        private const val EXTRA_TIMER_ID = "timer_id"
    }
}
