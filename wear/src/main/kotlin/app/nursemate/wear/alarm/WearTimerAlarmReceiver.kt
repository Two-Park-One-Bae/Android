package app.nursemate.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerState
import app.nursemate.core.timer.TimerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 워치가 건 만료 알람이 울렸다.
 *
 * 상태를 올리는 것과 손목을 깨우는 것을 **여기서 한 번에** 한다 — 폰의
 * `TimerAlarmReceiver` 와 같은 자리다.
 *
 * ## 저장이 끝난 뒤에 서비스를 띄운다
 * [WearAlarmService] 는 저장소에서 울리는 것을 읽어 판단한다. 순서가 뒤집히면 **빈 목록을
 * 보고 스스로 꺼진다.** 그래서 [TimerRepository.markRinging] 을 기다린 다음 부른다 —
 * 같은 코루틴 안에서 차례로 하면 된다.
 *
 * ## 포그라운드 서비스는 시간 창 안에서 띄운다
 * [WearAlarmService] 는 `specialUse` 포그라운드 서비스라 백그라운드에서 시작하려면 허용이
 * 필요하다. 알람으로 깨어난 경우 시스템이 **배달 시점 기준 10초**를 열어 준다(실기기 로그:
 * `tempAllowListReason: ALARM_MANAGER_WHILE_IDLE, duration:10000`). `onReceive` 가 언제
 * 반환하는지와 무관하므로 `goAsync` 로 넘겨도 되지만, **그 10초를 넘기면 안 된다.**
 * 여기서 하는 일은 DataStore 갱신 한 번이라 여유가 있다.
 */
@AndroidEntryPoint
class WearTimerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TimerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // goAsync() 를 쓰면 무슨 일이 있어도 finish() 에 도달해야 한다 — 안 그러면 시스템이
    // 리시버를 붙잡고 있다가 ANR 로 죽인다. 그래서 여기서는 넓게 잡는 게 맞다.
    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        val pending = goAsync()

        scope.launch {
            try {
                repository.markRinging(timerId)
                val ringing = repository.timers.first().filter { it.state == TimerState.RINGING }
                if (ringing.isEmpty()) {
                    // 이미 완료됐거나 아직 안 끝났다 — 상대가 먼저 지웠을 수 있다.
                    Log.i(TAG, "만료 알람이 울렸지만 울릴 것이 없다 ($timerId)")
                } else {
                    // ⚠️ **서비스보다 먼저 띄운다.** 백그라운드 액티비티 시작은 부르는 시점의
                    // 프로세스 상태를 본다. `WearAlarmService.start` 뒤에 부르면 상태가
                    // `FOREGROUND_SERVICE` 가 되는데, 그 자격으로는 막힌다(실기기 확인).
                    // 여기서는 아직 알람 브로드캐스트를 처리하는 중이다.
                    showAlarmScreen(context, ringing.first())
                    WearAlarmService.start(context, ringing)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "만료 처리 실패 ($timerId)", t)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 손목을 덮는 알람 화면을 띄워 본다.
     *
     * 성공을 전제하지 않는다 — 막히면 알림이 남는다. 그래도 **여기가 가장 유리한 자리**다.
     */
    private fun showAlarmScreen(context: Context, timer: CareTimer) {
        runCatching { context.startActivity(WearAlarmActivity.intent(context, timer.id, timer.alarmTitle)) }
            .onFailure { Log.w(TAG, "알람 화면을 띄우지 못했다 (${timer.id})", it) }
    }

    companion object {
        const val ACTION_FIRE = "app.nursemate.wear.action.ALARM_FIRE"
        const val EXTRA_TIMER_ID = "timerId"
        private const val TAG = "NM445"
    }
}
