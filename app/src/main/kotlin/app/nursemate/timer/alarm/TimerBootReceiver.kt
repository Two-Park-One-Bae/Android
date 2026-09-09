package app.nursemate.timer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.nursemate.core.data.timer.TimerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 재부팅 후 알람을 되살린다.
 *
 * `AlarmManager` 의 예약은 **기기를 끄면 전부 사라진다.** 되살리지 않으면 밤새 켜 둔 타이머가
 * 조용히 죽어 "울릴 줄 알았는데 안 울린" 상태가 된다 — spec 이 가장 위험하다고 본 경우다.
 *
 * [TimerRepository.restore] 가 판단을 다 갖고 있다. 이미 만료한 것은 RINGING 으로 올리고
 * 재예약하지 않으며, 아직 안 끝난 것만 다시 건다.
 */
@AndroidEntryPoint
class TimerBootReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TimerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // goAsync() 를 쓰면 무슨 일이 있어도 finish() 에 도달해야 한다 — 안 그러면 시스템이
    // 리시버를 붙잡고 있다가 ANR 로 죽인다. 그래서 여기서는 넓게 잡는 게 맞다.
    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        scope.launch {
            try {
                repository.restore()
            } catch (t: Throwable) {
                Log.e("TimerAlarm", "부팅 후 알람 복원 실패", t)
            } finally {
                pending.finish()
            }
        }
    }
}
