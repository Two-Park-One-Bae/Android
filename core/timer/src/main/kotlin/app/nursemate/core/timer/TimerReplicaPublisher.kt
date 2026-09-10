package app.nursemate.core.timer

import android.util.Log
import app.nursemate.core.datalayer.TimerSyncTransport
import app.nursemate.core.model.TimerReplica
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 이 기기가 아는 타이머를 상대에게 내놓고, 상대가 내놓은 것을 합친다.
 *
 * ## 스냅샷과 무엇이 다른가
 * 옛 스냅샷 경로(`:app` 의 `TimerSnapshotPublisher`)는 **폰이 주인**이라는 전제로 전체를
 * 덮어쓴다. 이건 양쪽이 각자 갖고 있다 맞추는 구조라, 받은 것을 **합쳐서** 자기 것으로 만든다.
 *
 * 폰·워치가 **같은 코드를 쓴다.** 자기가 누구인지만 [ReplicaOrigin] 으로 다르게 받는다.
 *
 * ## 되받는 발행을 끊는 자리
 * 합친 결과가 갖고 있던 것과 같으면 **아무것도 내놓지 않는다.** 그러지 않으면
 * A 발행 → B 합침·발행 → A 합침·발행 … 이 멎지 않는다.
 */
@Singleton
class TimerReplicaPublisher @Inject constructor(
    private val repository: TimerRepository,
    private val transport: TimerSyncTransport,
    @param:ReplicaOrigin private val origin: String
) {

    /** 내 것이 바뀔 때마다 내놓는다. 앱 수명 동안 한 번만 부른다. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            repository.replica.distinctUntilChanged().collect(::publish)
        }
        scope.launch { pull() }
    }

    /**
     * 상대가 써 둔 것을 당겨와 합친다. 앱이 새로 뜰 때 한 번.
     *
     * DataItem 은 앱 수명과 무관하게 남아 있어, 이 앱이 꺼져 있는 동안 상대가 써 둔 것을
     * 여기서 받는다 — 변경 알림은 그때 이미 지나갔다.
     */
    suspend fun pull() {
        val incoming = runCatching { transport.replicasExcept(origin) }.getOrElse {
            Log.w(TAG, "상대 복제본을 못 읽었다", it)
            return
        }
        incoming.forEach { ingest(it) }
    }

    /**
     * 받은 복제본을 합치고, 달라졌으면 내 새 값을 내놓는다.
     *
     * ⚠️ **내가 쓴 것도 변경 알림으로 돌아온다.** DataClient 는 같은 기기가 쓴 항목도
     * `onDataChanged` 로 알려 준다. 합쳐도 결과가 같아 틀리진 않지만, 매 발행마다 쓸데없이
     * 저장소를 한 번 더 훑게 되므로 여기서 거른다.
     */
    suspend fun ingest(incoming: TimerReplica) {
        if (incoming.origin == origin) return
        if (!repository.mergeReplica(incoming)) {
            Log.i(TAG, "복제본을 받았지만 바뀐 게 없다 (${incoming.origin})")
            return
        }
        val next = repository.replica.first()
        Log.i(TAG, "복제본을 합쳤다 (${incoming.origin}) → 기록 ${next.records.size}개")
        publish(next)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun publish(replica: TimerReplica) {
        try {
            transport.publishReplica(replica)
            Log.i(TAG, "복제본 발행 기록=${replica.records.size}개")
        } catch (t: Throwable) {
            // 상대가 없거나 꺼져 있으면 여기로 온다. 이 기기 기능에는 영향이 없어야 한다.
            Log.w(TAG, "복제본 전송 실패", t)
        }
    }

    private companion object {
        const val TAG = "NM445"
    }
}
