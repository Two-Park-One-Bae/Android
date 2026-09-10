package app.nursemate.wear.sync

import android.util.Log
import app.nursemate.core.datalayer.DataLayerPaths
import app.nursemate.core.model.decodeReplica
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * 폰이 내놓은 복제본을 받는다.
 *
 * ## 지금은 받았다는 것만 적는다
 * 워치가 자기 타이머를 갖는 것은 다음 단계다. 화면·알람은 아직 스냅샷을 보고 돌아간다.
 * 여기서 미리 붙여 두는 이유는 **전달 경로가 실제로 뚫렸는지를 기기에서 확인**하기
 * 위해서다 — 저장·알람까지 한꺼번에 바꾸면 안 될 때 어디가 문제인지 가릴 수 없다.
 */
class TimerReplicaListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { events ->
            events.forEach { event ->
                if (event.type != DataEvent.TYPE_CHANGED) return@forEach
                if (event.dataItem.uri.path != DataLayerPaths.TIMER_REPLICA) return@forEach

                val json = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap.getString(DataLayerPaths.KEY_REPLICA_JSON)
                val replica = json?.let(::decodeReplica) ?: run {
                    Log.w(TAG, "읽을 수 없는 복제본을 버렸다")
                    return@forEach
                }
                val live = replica.records.count { !it.isRemoved }
                val gone = replica.records.size - live
                Log.i(TAG, "복제본 수신 (${replica.origin}) 살아있음=$live 자리표=$gone")
            }
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
