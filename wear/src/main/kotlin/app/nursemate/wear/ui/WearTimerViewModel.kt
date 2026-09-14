package app.nursemate.wear.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerState
import app.nursemate.core.timer.TimerRepository
import app.nursemate.wear.sync.WearPresetStore
import app.nursemate.wear.tile.TileInstallation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 워치 화면의 상태.
 *
 * ## 워치가 자기 타이머를 갖는다
 * 예전에는 폰에 명령을 보내고 **다음 스냅샷을 기다렸다.** 그래서 연결이 끊기면 눌러도
 * 아무 일이 없었고, 만료도 폰이 알려 줘야만 알았다. 지금은 [TimerRepository] 를 직접
 * 부른다 — 저장·알람 예약이 그 자리에서 끝나고, 복제가 폰에 알린다.
 *
 * 그래서 **왕복을 기다리는 표시가 없다.** 누르면 바로 목록에 생긴다.
 *
 * ## 프리셋은 여전히 폰 것이다
 * 프리셋 편집은 폰 전용이라(spec §워치) 워치는 스냅샷으로 받아 읽기만 한다.
 */
@HiltViewModel
class WearTimerViewModel @Inject constructor(
    private val repository: TimerRepository,
    private val store: WearPresetStore,
    private val tiles: TileInstallation
) : ViewModel() {

    val presets: StateFlow<List<TimerPreset>> = store.snapshot
        .map { it?.presets.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * 진행 중인 타이머. **null 은 "아직 안 읽었다"** 이고 빈 목록과 다르다.
     *
     * 둘을 섞으면 화면이 뜨자마자 "없으니 나가자"로 읽는다 — 알람 화면이 0.46초 만에
     * 스스로 닫히는 것으로 드러났다(실기기).
     */
    val timers: StateFlow<List<CareTimer>?> = repository.timers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** 남은 시간 계산의 기준. 폰과 같은 이유로 1초마다 화면만 다시 그린다. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** 시작하면 활성 페이지로 돌아갈 신호. 이제는 누른 즉시 울린다. */
    private val _startConfirmed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val startConfirmed: SharedFlow<Unit> = _startConfirmed.asSharedFlow()

    /**
     * 타일 추가를 권하는 안내가 떠 있다. null 이면 안 떠 있다.
     *
     * **워치에서 처음 시작한 직후**에만 한 번 뜬다 — 방금 앱을 열어 시작해 본 그 순간이
     * "다음엔 안 열어도 된다"가 가장 잘 와닿는 자리다.
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
    }

    /** 화면에 보여 줄 순서 — 폰과 같은 규칙(만료 먼저). */
    fun ordered(timers: List<CareTimer>, now: Long): List<CareTimer> =
        CareTimerTransitions.projectedAndOrdered(timers, now)

    /** 기기에 남아 있는 마지막 스냅샷(프리셋)을 다시 읽는다. 화면이 열릴 때마다 부른다. */
    fun refresh() {
        viewModelScope.launch { store.restore() }
    }

    /**
     * 프리셋을 시작한다 — **폰에 묻지 않는다.**
     *
     * 폰 권한을 확인하던 관문이 없어졌다. 워치는 자기 알람을 자기 권한으로 걸기 때문에
     * (`USE_EXACT_ALARM`, 자동 허용) 폰 사정과 무관하게 시작할 수 있다.
     */
    fun start(preset: TimerPreset) {
        viewModelScope.launch {
            repository.start(preset)
            _startConfirmed.tryEmit(Unit)
            offerTileAfterStart()
        }
    }

    fun complete(timer: CareTimer) = launchIo { repository.remove(timer.id) }

    /** W2 [일시정지/재개]. 어느 쪽인지는 현재 상태가 정한다 — 화면이 판단하지 않는다. */
    fun pauseOrResume(timer: CareTimer) = launchIo {
        if (timer.state == TimerState.PAUSED) repository.resume(timer.id) else repository.pause(timer.id)
    }

    /** W2 [정지] — 완료와 마찬가지로 삭제다(spec §생성 → 실행). */
    fun stop(timer: CareTimer) = launchIo { repository.remove(timer.id) }

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
     * 시작 직후 잠깐 두고 타일 안내를 띄운다.
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

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val TICK_MS = 1000L
        const val STOP_TIMEOUT_MS = 5000L

        /** 시작 → 안내까지 두는 시간. 페이지 전환이 끝나고 타이머가 눈에 들어올 만큼만. */
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
