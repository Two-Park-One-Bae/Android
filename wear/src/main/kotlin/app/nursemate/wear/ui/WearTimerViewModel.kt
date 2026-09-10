package app.nursemate.wear.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerCommand
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerSnapshot
import app.nursemate.core.model.TimerState
import app.nursemate.core.model.alarmPermissionMissing
import app.nursemate.wear.sync.WearTimerStore
import app.nursemate.wear.tile.TileInstallation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 워치 화면의 상태.
 *
 * ## 폰이 보낸 것만 보여 준다
 * 시작·정지를 눌러도 여기서 목록을 고치지 않는다. 명령을 보내고 **다음 스냅샷을 기다린다**
 * (spec §명령). 미리 바꿔 두면 폰이 거절했을 때(권한 없음·프리셋 삭제) 두 화면이 갈라진다.
 *
 * 왕복 동안 화면이 멈춰 보이는 구간이 생기는데, spec 이 "그 구간을 어떻게 보여줄지는
 * 플랫폼이 정한다"고 열어 뒀다 — [pending] 으로 눌린 항목을 표시한다.
 */
@HiltViewModel
class WearTimerViewModel @Inject constructor(private val store: WearTimerStore, private val tiles: TileInstallation) :
    ViewModel() {

    val snapshot: StateFlow<TimerSnapshot?> = store.snapshot

    /** 남은 시간 계산의 기준. 폰과 같은 이유로 1초마다 화면만 다시 그린다. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** 명령을 보내고 스냅샷을 기다리는 중인 항목. 같은 것을 두 번 누르지 않게 막는다. */
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /**
     * 시작이 **폰에서 확인됐다** — 활성 페이지로 돌아갈 신호.
     *
     * 누르자마자 넘기지 않는다. 워치는 상태를 직접 바꾸지 않아(spec §명령) 그 순간에는
     * 활성 페이지가 아직 비어 있다 — 빈 화면을 보여 줬다가 채우면 시작이 안 된 것처럼 보인다.
     * 스냅샷이 돌아온 뒤에 넘긴다.
     */
    private val _startConfirmed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val startConfirmed: SharedFlow<Unit> = _startConfirmed.asSharedFlow()

    /** 시작 명령을 보내고 스냅샷을 기다리는 중인가. */
    private var awaitingStart = false

    /** 마지막 명령이 폰에 닿지 못했다. 화면이 안내한다. */
    private val _notice = MutableStateFlow<WearNotice?>(null)
    val notice: StateFlow<WearNotice?> = _notice.asStateFlow()

    /**
     * 타일 추가를 권하는 안내가 떠 있다. null 이면 안 떠 있다.
     *
     * **워치에서 처음 시작한 직후**에만 한 번 뜬다 — 방금 앱을 열어 시작해 본 그 순간이
     * "다음엔 안 열어도 된다"가 가장 잘 와닿는 자리다. 타일에서 시작한 경우는 이 경로를
     * 타지 않아 뜨지 않는다(이미 타일을 쓰고 있으니 권할 이유도 없다).
     */
    private val _tilePrompt = MutableStateFlow<TilePrompt?>(null)
    val tilePrompt: StateFlow<TilePrompt?> = _tilePrompt.asStateFlow()

    init {
        viewModelScope.launch { store.restore() }
        viewModelScope.launch {
            while (isActive) {
                _now.value = System.currentTimeMillis()
                delay(TICK_MS)
            }
        }
        // 스냅샷이 새로 오면 기다리던 명령이 처리된 것이다.
        viewModelScope.launch {
            snapshot.collect {
                if (_pending.value != null && awaitingStart) {
                    awaitingStart = false
                    _startConfirmed.tryEmit(Unit)
                    offerTileAfterStart()
                }
                _pending.value = null
            }
        }
    }

    /** 화면에 보여 줄 순서 — 폰과 같은 규칙(만료 먼저). */
    fun ordered(snapshot: TimerSnapshot, now: Long): List<CareTimer> = CareTimerTransitions.ordered(
        snapshot.timers.map { if (it.isExpiredAt(now)) CareTimerTransitions.ring(it) else it },
        now
    )

    /** 기기에 남아 있는 마지막 스냅샷을 다시 읽는다. 화면이 열릴 때마다 부른다. */
    fun refresh() {
        viewModelScope.launch { store.restore() }
    }

    /**
     * 프리셋을 시작한다.
     *
     * ⚠️ **폰에 알람 권한이 없으면 보내지 않는다.** 폰은 받아도 조용히 거절하는데
     * (`TimerCommandListenerService`), 워치가 그걸 모르면 눌러도 아무 일이 없는 것처럼 보인다.
     * 스냅샷이 실어 온 `alarmAuthorized` 로 미리 가려 **이유를 알려 준다.**
     */
    fun start(preset: TimerPreset) {
        if (snapshot.value?.alarmPermissionMissing == true) {
            _notice.value = WearNotice.PHONE_PERMISSION
            return
        }
        awaitingStart = true
        send(preset.id, TimerCommand.Start(preset.id))
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /**
     * 타일 안내를 닫는다. [추가하기]·[나중에] 어느 쪽이든 **다시 묻지 않는다.**
     *
     * 목록을 열어 줬어도 실제로 붙였는지는 알 수 없지만, 안 붙였다면 그건 사용자의 선택이다.
     */
    fun dismissTilePrompt() {
        _tilePrompt.value = null
        viewModelScope.launch { tiles.markAsked() }
    }

    /**
     * 시작이 확인된 뒤 잠깐 두고 타일 안내를 띄운다.
     *
     * ⚠️ **바로 띄우지 않는다.** 이 순간 화면은 활성 페이지로 넘어가는 중이라, 겹쳐 띄우면
     * 방금 만든 타이머를 못 보고 안내부터 본다 — 무엇에 대한 안내인지 알 수 없게 된다.
     */
    private fun offerTileAfterStart() {
        viewModelScope.launch {
            delay(TILE_PROMPT_DELAY_MS)
            if (tiles.shouldOfferAdd()) {
                _tilePrompt.value = TilePrompt(intent = tiles.addTileIntent())
            }
        }
    }

    fun complete(timer: CareTimer) = send(timer.id, TimerCommand.Remove(timer.id))

    /** W2 [일시정지/재개]. 어느 쪽인지는 현재 상태가 정한다 — 화면이 판단하지 않는다. */
    fun pauseOrResume(timer: CareTimer) = send(
        timer.id,
        if (timer.state == TimerState.PAUSED) TimerCommand.Resume(timer.id) else TimerCommand.Pause(timer.id)
    )

    /** W2 [정지] — 완료와 마찬가지로 삭제다(spec §생성 → 실행). */
    fun stop(timer: CareTimer) = send(timer.id, TimerCommand.Remove(timer.id))

    private fun send(key: String, command: TimerCommand) {
        _pending.value = key
        _notice.value = null
        viewModelScope.launch {
            if (!store.send(command)) {
                // 폰이 꺼져 있거나 연결이 끊겼다. 기다리게 두면 영영 안 풀린다.
                _pending.value = null
                awaitingStart = false
                _notice.value = WearNotice.UNDELIVERED
            }
        }
    }

    private companion object {
        const val TICK_MS = 1000L

        /** 시작 확인 → 안내까지 두는 시간. 페이지 전환이 끝나고 타이머가 눈에 들어올 만큼만. */
        const val TILE_PROMPT_DELAY_MS = 1_500L
    }
}

/**
 * 타일 추가 안내에 필요한 것.
 *
 * @param intent 「타일 추가」 목록을 여는 인텐트. **null 이면 그 화면이 없는 워치라**
 *   버튼 대신 직접 추가하는 방법을 글로 안내한다.
 */
data class TilePrompt(val intent: Intent?)
