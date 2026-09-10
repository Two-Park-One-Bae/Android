package app.nursemate.core.datalayer

import android.content.Context
import android.net.Uri
import android.util.Log
import app.nursemate.core.model.TimerCommand
import app.nursemate.core.model.TimerSnapshot
import app.nursemate.core.model.decodeSnapshot
import app.nursemate.core.model.encodeCommand
import app.nursemate.core.model.encodeSnapshot
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * 폰↔워치를 오가는 실제 배선. 무엇을 주고받는지는 [TimerSnapshot]·[TimerCommand] 가 정한다.
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
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

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
    suspend fun publish(snapshot: TimerSnapshot) {
        val request = PutDataMapRequest.create(DataLayerPaths.TIMER_SNAPSHOT).apply {
            dataMap.putString(DataLayerPaths.KEY_SNAPSHOT_JSON, encodeSnapshot(snapshot))
        }
        dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
    }

    /**
     * 지금 기기에 남아 있는 마지막 스냅샷.
     *
     * 워치 앱이 새로 뜰 때 쓴다 — DataItem 은 앱 수명과 무관하게 남아 있어, 폰이 꺼져 있어도
     * 마지막으로 본 상태를 그릴 수 있다(spec: 연결이 끊겨도 화면이 있어야 한다).
     */
    suspend fun latestSnapshot(): TimerSnapshot? {
        val items = dataClient.getDataItems(snapshotUri()).await()
        return items.use { buffer ->
            buffer.asSequence()
                .mapNotNull { DataMapItem.fromDataItem(it).dataMap.getString(DataLayerPaths.KEY_SNAPSHOT_JSON) }
                .mapNotNull(::decodeSnapshot)
                .maxByOrNull { it.snapshotAt }
        }
    }

    /**
     * 워치 → 폰. 연결된 노드 전부에 보낸다.
     *
     * 노드를 하나로 특정하지 않는다 — 워치가 여럿일 수도 있고, 어느 쪽이 폰인지 가리려면
     * 능력(capability) 선언이 하나 더 필요한데 명령은 폰만 처리하므로 그냥 뿌려도 안전하다.
     *
     * @return 한 곳이라도 받았으면 true. 전부 실패하면 false — 호출자가 화면에 알린다.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun send(command: TimerCommand): Boolean {
        val payload = encodeCommand(command).toByteArray()
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrElse {
            Log.w(TAG, "연결된 노드를 못 읽었다", it)
            return false
        }
        var delivered = false
        nodes.forEach { node ->
            try {
                messageClient.sendMessage(node.id, DataLayerPaths.TIMER_COMMAND, payload).await()
                delivered = true
            } catch (t: Throwable) {
                // 상대가 꺼져 있으면 여기로 온다. 다른 노드는 계속 시도한다.
                Log.w(TAG, "명령 전달 실패 (${node.displayName})", t)
            }
        }
        return delivered
    }

    /**
     * 스냅샷 DataItem 을 가리키는 URI.
     *
     * ⚠️ **authority 를 `*` 로 둔다.** 비워 두면 이 기기가 쓴 것만 찾는데, 워치가 읽고 싶은
     * 것은 **폰이 쓴** 항목이다. 와일드카드가 "붙어 있는 모든 노드"를 뜻한다.
     */
    private fun snapshotUri(): Uri = Uri.Builder()
        .scheme(PutDataRequest.WEAR_URI_SCHEME)
        .authority(ALL_NODES)
        .path(DataLayerPaths.TIMER_SNAPSHOT)
        .build()

    private companion object {
        const val TAG = "NM445"
        const val ALL_NODES = "*"
    }
}
