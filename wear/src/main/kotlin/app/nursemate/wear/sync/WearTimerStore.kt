package app.nursemate.wear.sync

import android.util.Log
import app.nursemate.core.datalayer.TimerSyncTransport
import app.nursemate.core.model.TimerCommand
import app.nursemate.core.model.TimerSnapshot
import app.nursemate.core.model.newerOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 워치가 아는 마지막 상태.
 *
 * ## 워치는 상태를 만들지 않는다
 * 폰이 보낸 스냅샷을 그대로 들고 있고, 사용자가 뭘 누르면 **명령을 보내고 기다린다**
 * (spec §명령). 그래서 여기에 "낙관적 갱신"이 없다 — 워치가 미리 바꿔 두면 폰이 거절했을 때
 * (권한 없음·프리셋 삭제) 두 화면이 갈라진다.
 *
 * ## 리스너와 화면이 같은 것을 봐야 한다
 * 스냅샷은 앱이 꺼져 있어도 서비스로 도착한다. 그 서비스와 화면이 다른 인스턴스를 보면
 * 앱을 열었을 때 옛 값이 보인다. `@Singleton` 인 이유다.
 */
@Singleton
class WearTimerStore @Inject constructor(private val transport: TimerSyncTransport) {

    private val _snapshot = MutableStateFlow<TimerSnapshot?>(null)

    /** null = 아직 한 번도 못 받았다. 화면은 이걸 "연결 안 됨"과 구분해서 다뤄야 한다. */
    val snapshot: StateFlow<TimerSnapshot?> = _snapshot.asStateFlow()

    /**
     * 새로 받은 스냅샷을 반영한다.
     *
     * ⚠️ **무조건 덮지 않는다.** 전달 순서가 뒤집혀 옛 스냅샷이 늦게 도착할 수 있어
     * `snapshotAt` 이 최신인 쪽만 채택한다(spec §충돌·삭제).
     */
    fun offer(incoming: TimerSnapshot) {
        _snapshot.update { current -> newerOf(current, incoming) }
    }

    /**
     * 기기에 남아 있는 마지막 스냅샷을 읽어 온다. 앱이 새로 뜰 때 한 번 부른다.
     *
     * DataItem 은 앱 수명과 무관하게 남아 있어, **폰이 꺼져 있어도** 마지막으로 본 상태를
     * 그릴 수 있다.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun restore() {
        try {
            transport.latestSnapshot()?.let(::offer)
        } catch (t: Throwable) {
            Log.w(TAG, "마지막 스냅샷을 못 읽었다", t)
        }
    }

    /**
     * 폰에 명령을 보낸다. 상태는 바꾸지 않는다 — 결과는 다음 스냅샷으로 돌아온다.
     *
     * @return 전달됐으면 true. false 면 폰이 꺼져 있거나 연결이 끊긴 것이라 화면이 알려야 한다.
     */
    suspend fun send(command: TimerCommand): Boolean = transport.send(command)

    private companion object {
        const val TAG = "NM445"
    }
}
