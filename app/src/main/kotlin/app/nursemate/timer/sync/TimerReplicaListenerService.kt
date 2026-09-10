package app.nursemate.timer.sync

import android.util.Log
import app.nursemate.core.datalayer.DataLayerPaths
import app.nursemate.core.model.decodeReplica
import app.nursemate.core.timer.TimerReplicaPublisher
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * 워치가 내놓은 복제본을 받는다.
 *
 * 앱이 꺼져 있어도 시스템이 깨워 전달한다 — 워치에서 시작한 타이머가 폰 알람까지 걸리려면
 * 이 자리에서 받아야 한다.
 *
 * `runBlocking` 인 이유는 [TimerCommandListenerService] 와 같다 — 이 메서드가 돌아오는
 * 순간 서비스가 죽을 수 있어, 코루틴을 띄워 보내면 합치는 도중에 끊긴다.
 */
@AndroidEntryPoint
class TimerReplicaListenerService : WearableListenerService() {

    @Inject lateinit var publisher: TimerReplicaPublisher

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { events ->
            events.forEach { event ->
                // ⚠️ **삭제 이벤트는 건너뛴다.** DELETED 의 DataItem 은 값이 비어 있다.
                if (event.type != DataEvent.TYPE_CHANGED) return@forEach
                if (event.dataItem.uri.path != DataLayerPaths.TIMER_REPLICA) return@forEach

                val json = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap.getString(DataLayerPaths.KEY_REPLICA_JSON)
                val replica = json?.let(::decodeReplica) ?: run {
                    Log.w(TAG, "읽을 수 없는 복제본을 버렸다")
                    return@forEach
                }
                runBlocking { publisher.ingest(replica) }
            }
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
