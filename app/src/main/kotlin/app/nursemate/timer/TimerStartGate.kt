package app.nursemate.timer

import app.nursemate.core.data.timer.TimerRepository
import app.nursemate.core.model.AlertMode
import app.nursemate.core.model.TimerPreset
import app.nursemate.timer.alarm.TimerPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 타이머 시작 전에 거쳐야 하는 관문 — 권한, 그리고 울림 방식 최초 선택.
 *
 * ## 리스트와 분리한 이유
 * 리스트 화면 상태(틱·메모·시트)와 성격이 다르다. 이건 **한 번 지나면 다시 안 나타나는**
 * 절차라 수명도 규칙도 따로 논다. 한 클래스에 두면 "시작"이 뭘 하는 함수인지 흐려진다.
 *
 * @param permissions 화면이 OS 권한 팝업·설정 이동을 직접 밟아야 해서 함께 내보낸다.
 *                    ApplicationContext 만 쥔 `@Singleton` 이라 화면에 들려도 누수가 없다.
 */
class TimerStartGate(
    private val repository: TimerRepository,
    val permissions: TimerPermissions,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<TimerGate>(TimerGate.None)
    val state: StateFlow<TimerGate> = _state.asStateFlow()

    /** 관문이 열리면 시작할 프리셋. 권한·울림 방식을 받는 동안 들고 있는다. */
    private var pending: TimerPreset? = null

    /**
     * 이미 한 번 요청해 본 권한.
     *
     * 여기 있는데도 여전히 없으면 사용자가 거부한 것이다. 이걸 안 나누면 **정상 흐름을
     * 거부로 오해한다** — Android 는 받을 권한이 둘이라, 알림을 허용한 직후에도 정확 알람이
     * 남아 다시 권한 관문에 걸린다.
     */
    private val asked = mutableSetOf<PermissionStep>()

    /**
     * 프리셋을 누르면 곧바로 시작한다(정본 C3 부제). 다만 **처음 한 번은** 막힌다.
     *
     * spec §알람 권한이 "권한이 없으면 시작할 수 없다"고 못박았다. 권한 없이 시작시키면
     * 예약이 조용히 실패해 "울릴 줄 알았는데 안 울린" 상태가 된다 — 그게 더 위험하다.
     */
    fun start(preset: TimerPreset) {
        pending = preset
        advance()
    }

    /**
     * 남은 관문이 있으면 시트를 띄우고, 다 지났으면 시작한다.
     *
     * 권한 요청과 설정 왕복이 끝날 때마다 화면이 이걸 다시 부른다 — 어디까지 왔는지를
     * 상태로 들고 있지 않고 **그때그때 실제 권한을 확인해** 정한다. 사용자가 설정에서
     * 권한을 도로 끌 수도 있어서다.
     */
    fun advance() {
        val preset = pending ?: return
        val step = nextStep()
        if (step != null) {
            _state.value = TimerGate.Permission(step = step, denied = step in asked)
            return
        }
        scope.launch {
            if (!repository.alertModeChosen.first()) {
                _state.value = TimerGate.AlertMode
                return@launch
            }
            pending = null
            _state.value = TimerGate.None
            repository.start(preset)
        }
    }

    /** 울림 방식 시트에서 [이대로 시작하기]. 저장하면 '고른 적 있음'이 되어 다시 뜨지 않는다. */
    fun confirmAlertMode(mode: AlertMode) {
        scope.launch {
            repository.setAlertMode(mode)
            advance()
        }
    }

    /** 화면이 권한을 요청하기 직전에 부른다. 다음 판정에서 거부인지 가리는 근거가 된다. */
    fun markAsked(step: PermissionStep) {
        asked += step
    }

    /** 관문을 닫고 시작을 포기한다 — 「나중에 할게요」·「닫기」. */
    fun dismiss() {
        pending = null
        _state.value = TimerGate.None
    }

    /** 아직 못 받은 권한 하나. 알림을 먼저 받는다 — 앱을 안 떠나고 끝나서다. */
    private fun nextStep(): PermissionStep? = when {
        !permissions.canPostNotifications() -> PermissionStep.NOTIFICATION
        !permissions.canScheduleExact() -> PermissionStep.EXACT_ALARM
        else -> null
    }
}
