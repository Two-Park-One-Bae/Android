package app.nursemate.wear

import android.app.Application
import android.util.Log
import app.nursemate.core.timer.TimerReplicaPublisher
import app.nursemate.core.timer.TimerRepository
import app.nursemate.wear.alarm.WearTimerNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class NurseMateWearApplication : Application() {

    @Inject lateinit var timerRepository: TimerRepository

    @Inject lateinit var replicaPublisher: TimerReplicaPublisher

    @Inject lateinit var notifier: WearTimerNotifier

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Suppress("TooGenericExceptionCaught")
    override fun onCreate() {
        super.onCreate()

        // 워치도 자기 타이머를 갖는다(NM-445) — 폰과 같은 복원 절차를 밟는다.
        // 꺼져 있던 동안 만료한 것은 RINGING 으로 올라가고, 아직 도는 것은 예약을 되살린다.
        // 재부팅으로 AlarmManager 가 비었을 수 있어 이 복원이 없으면 조용히 안 울린다.
        applicationScope.launch {
            runCatching { timerRepository.restore() }
                .onFailure { Log.e(TAG, "타이머 복원 실패", it) }
                .onSuccess { Log.i(TAG, "타이머 복원 완료") }

            // 복원 뒤에 켠다 — 먼저 켜면 시각이 안 맞춰진 목록을 폰에 내놓는다.
            replicaPublisher.start(applicationScope)
        }

        // 손목의 진행 중·만료 알림을 목록에 맞춘다.
        //
        // 예전에는 스냅샷 리스너가 이 일을 했다. 타이머가 복제로 오가게 되면서 그 자리가
        // 없어졌는데, **알림을 아무도 안 그리면 앱을 열기 전까지 손목에 아무 표시가 없다.**
        // 프로세스는 우리 동작·복제 수신·알람 발화 중 하나로 반드시 깨어나므로 여기서 잇는다.
        applicationScope.launch {
            timerRepository.timers.collect(notifier::sync)
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
