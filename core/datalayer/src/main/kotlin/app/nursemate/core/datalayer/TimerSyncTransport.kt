package app.nursemate.core.datalayer

import android.content.Context
import android.net.Uri
import android.util.Log
import app.nursemate.core.model.PresetSnapshot
import app.nursemate.core.model.TimerReplica
import app.nursemate.core.model.decodePresets
import app.nursemate.core.model.decodeReplica
import app.nursemate.core.model.encodePresets
import app.nursemate.core.model.encodeReplica
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * 폰↔워치를 오가는 실제 배선. 무엇을 주고받는지는 [PresetSnapshot]·[TimerCommand] 가 정한다.
 *
 * ## 상태는 DataClient, 명령은 MessageClient
 * 성격이 달라 채널을 나눈다(공식 문서 기준).
 * - **DataClient** — 값이 기기에 남고, 떨어져 있어도 썼다가 다시 붙을 때 동기화된다.
 *   상태(스냅샷)는 "마지막 값이 무엇인가"가 전부라 여기 맞다.
 * - **MessageClient** — 상대가 꺼져 있으면 `TARGET_NODE_NOT_CONNECTED` 로 **그냥 실패**하고
 *   재시도가 없다. 대신 즉시성이 있다. 명령은 늦게 도착하면 의미가 바뀌므로(도착 시점 기준
 *   처리) 쌓였다 나중에 배달되는 편이 오히려 위험하다 — 실패하는 쪽이 낫다.
 */
class TimerSyncTransport(private val context: Context) {

    private val dataClient by lazy { Wearable.getDataClient(context) }

    /**
     * 폰 → 워치. 전체 상태를 덮어쓴다.
     *
     * ⚠️ **내용이 한 글자도 안 바뀌면 워치는 아무 일도 겪지 않는다.** DataClient 는 같은
     * 값으로 다시 써도 변경으로 치지 않아 `onDataChanged` 가 안 온다. `snapshotAt` 이 매번
     * 달라서 실제로는 늘 통과하지만, 그 필드를 지우면 동기화가 조용히 멈춘다.
     *
     * `setUrgent()` — 이걸 안 붙이면 시스템이 최대 30분까지 미룰 수 있다. 타이머는 그 지연을
     * 감당하지 못한다.
     */
    suspend fun publishPresets(snapshot: PresetSnapshot) {
        val request = PutDataMapRequest.create(DataLayerPaths.PRESET_SNAPSHOT).apply {
            dataMap.putString(DataLayerPaths.KEY_PRESET_JSON, encodePresets(snapshot))
        }
        dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
    }

    /**
     * 지금 기기에 남아 있는 마지막 스냅샷.
     *
     * 워치 앱이 새로 뜰 때 쓴다 — DataItem 은 앱 수명과 무관하게 남아 있어, 폰이 꺼져 있어도
     * 마지막으로 본 상태를 그릴 수 있다(spec: 연결이 끊겨도 화면이 있어야 한다).
     */
    suspend fun latestPresets(): PresetSnapshot? {
        val items = dataClient.getDataItems(pathUri(DataLayerPaths.PRESET_SNAPSHOT)).await()
        return items.use { buffer ->
            buffer.asSequence()
                .mapNotNull { DataMapItem.fromDataItem(it).dataMap.getString(DataLayerPaths.KEY_PRESET_JSON) }
                .mapNotNull(::decodePresets)
                .maxByOrNull { it.snapshotAt }
        }
    }

    /**
     * 이 기기가 아는 것을 내놓는다. 폰·워치 양쪽이 같은 경로에 쓰지만 항목은 기기별로 따로다.
     *
     * ⚠️ **내용이 한 글자도 안 바뀌면 상대는 아무 일도 겪지 않는다.** DataClient 는 같은 값을
     * 다시 써도 변경으로 치지 않아 `onDataChanged` 가 안 온다. 복제본에는 스냅샷의
     * `snapshotAt` 같은 매번 달라지는 필드가 **없다** — 일부러 그렇게 뒀다. 바뀐 게 없으면
     * 조용한 것이 맞고, 그래야 서로 되받는 발행이 멎는다.
     */
    suspend fun publishReplica(replica: TimerReplica) {
        val request = PutDataMapRequest.create(DataLayerPaths.TIMER_REPLICA).apply {
            dataMap.putString(DataLayerPaths.KEY_REPLICA_JSON, encodeReplica(replica))
        }
        dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
    }

    /**
     * 붙어 있는 기기들이 내놓은 복제본 중 **내 것이 아닌 것**.
     *
     * 내 항목을 걸러 내는 이유는 낭비를 줄이려는 것뿐이다 — 합쳐도 결과가 같아(멱등) 틀리진
     * 않는다. 앱이 새로 뜰 때 한 번 당겨오는 데 쓴다. DataItem 은 앱 수명과 무관하게 남아
     * 있어, 끊겨 있던 동안 상대가 써 둔 것을 여기서 받는다.
     */
    suspend fun replicasExcept(myOrigin: String): List<TimerReplica> {
        val items = dataClient.getDataItems(pathUri(DataLayerPaths.TIMER_REPLICA)).await()
        return items.use { buffer ->
            buffer.asSequence()
                .mapNotNull { DataMapItem.fromDataItem(it).dataMap.getString(DataLayerPaths.KEY_REPLICA_JSON) }
                .mapNotNull(::decodeReplica)
                .filter { it.origin != myOrigin }
                .toList()
        }
    }

    /**
     * DataItem 을 가리키는 URI.
     *
     * ⚠️ **authority 를 `*` 로 둔다.** 비워 두면 이 기기가 쓴 것만 찾는데, 워치가 읽고 싶은
     * 것은 **폰이 쓴** 항목이다. 와일드카드가 "붙어 있는 모든 노드"를 뜻한다.
     */
    private fun pathUri(path: String): Uri = Uri.Builder()
        .scheme(PutDataRequest.WEAR_URI_SCHEME)
        .authority(ALL_NODES)
        .path(path)
        .build()

    private companion object {
        const val TAG = "NM445"
        const val ALL_NODES = "*"
    }
}
