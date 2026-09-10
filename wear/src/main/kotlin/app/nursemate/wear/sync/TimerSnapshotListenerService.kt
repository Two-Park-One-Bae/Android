package app.nursemate.wear.sync

import android.util.Log
import app.nursemate.core.datalayer.DataLayerPaths
import app.nursemate.core.model.TimerState
import app.nursemate.core.model.decodeSnapshot
import app.nursemate.wear.alarm.WearAlarmService
import app.nursemate.wear.alarm.WearTimerNotifier
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 폰이 보낸 스냅샷을 받는다.
 *
 * 앱이 꺼져 있어도 시스템이 이 서비스를 깨워 전달한다 — 그래서 사용자가 워치 앱을 열었을 때
 * 이미 최신 상태다.
 */
@AndroidEntryPoint
class TimerSnapshotListenerService : WearableListenerService() {

    @Inject lateinit var store: WearTimerStore

    @Inject lateinit var notifier: WearTimerNotifier

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { events ->
            events.forEach { event ->
                // ⚠️ **삭제 이벤트는 건너뛴다.** DELETED 의 DataItem 은 값이 비어 있어,
                // 읽으려 들면 빈 스냅샷으로 화면을 지우게 된다.
                if (event.type != DataEvent.TYPE_CHANGED) return@forEach
                if (event.dataItem.uri.path != DataLayerPaths.TIMER_SNAPSHOT) return@forEach

                val json = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap.getString(DataLayerPaths.KEY_SNAPSHOT_JSON)
                val snapshot = json?.let(::decodeSnapshot)
                if (snapshot == null) {
                    Log.w(TAG, "읽을 수 없는 스냅샷을 버렸다")
                    return@forEach
                }
                store.offer(snapshot)

                // 앱이 꺼져 있어도 이 서비스는 깨어난다 — 만료를 알리는 자리가 여기다.
                // 화면이 떠 있을 때만 알리면 손목에서 아무 일도 안 일어난다.
                val timers = store.snapshot.value?.timers.orEmpty()
                val ringing = timers.filter { it.state == TimerState.RINGING }
                if (ringing.isEmpty()) {
                    notifier.sync(timers)
                } else {
                    // 진동을 [완료] 까지 이어 주려면 프로세스가 살아 있어야 한다.
                    // 알림도 그 서비스가 포그라운드로 띄운다.
                    WearAlarmService.start(this, ringing)
                }
            }
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
