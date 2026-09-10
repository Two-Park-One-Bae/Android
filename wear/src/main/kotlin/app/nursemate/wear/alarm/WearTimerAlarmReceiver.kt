package app.nursemate.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.nursemate.core.model.TimerState
import app.nursemate.core.timer.TimerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 워치가 건 만료 알람이 울렸다.
 *
 * 상태를 올리는 것과 손목을 깨우는 것을 **여기서 한 번에** 한다 — 폰의
 * `TimerAlarmReceiver` 와 같은 자리다.
 *
 * ## `goAsync` 가 아니라 `runBlocking` 이다
 * 저장이 끝나야 [WearAlarmService] 가 무엇이 울리는지 볼 수 있다. `goAsync` 로 넘기면
 * 서비스가 먼저 떠서 **빈 목록을 보고 스스로 꺼진다.** 만료 처리는 짧아(DataStore 갱신
 * 한 번) 브로드캐스트 제한 시간 안에 끝난다.
 */
@AndroidEntryPoint
class WearTimerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TimerRepository

    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return

        runBlocking {
            try {
                repository.markRinging(timerId)
                val ringing = repository.timers.first().filter { it.state == TimerState.RINGING }

                if (ringing.isEmpty()) {
                    // 이미 완료됐거나 아직 안 끝났다 — 상대가 먼저 지웠을 수 있다.
                    Log.i(TAG, "만료 알람이 울렸지만 울릴 것이 없다 ($timerId)")
                } else {
                    WearAlarmService.start(context, ringing)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "만료 처리 실패 ($timerId)", t)
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "app.nursemate.wear.action.ALARM_FIRE"
        const val EXTRA_TIMER_ID = "timerId"
        private const val TAG = "NM445"
    }
}
