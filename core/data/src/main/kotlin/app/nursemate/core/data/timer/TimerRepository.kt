package app.nursemate.core.data.timer

import app.nursemate.core.model.AlertMode
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 현재 시각. 테스트가 실제 시계에 묶이지 않도록 주입한다. */
fun interface TimerClock {
    fun now(): Long
}

/**
 * 만료 알람을 예약·취소하는 쪽. 구현은 `:app`(AlarmManager)이 한다.
 *
 * `core:data` 가 안드로이드 알람 API 를 직접 알면 워치·테스트에서 이 계층을 못 쓴다.
 * 여기서는 **"언제 무엇을 울릴지"** 만 말하고, 그걸 어떤 수단으로 예약하는지는 모른다.
 */
interface TimerAlarmScheduler {
    /** [timer] 의 `endAt` 에 울리도록 예약한다. 같은 id 로 다시 부르면 **덮어쓴다**. */
    fun schedule(timer: CareTimer)

    /** 예약을 지운다. 이미 없으면 아무 일도 하지 않는다. */
    fun cancel(timerId: String)

    /** 울리고 있는 알람을 멈춘다(완료). */
    fun dismiss(timerId: String)
}

/**
 * 처치 타이머 상태의 주인.
 *
 * ## 저장과 알람을 **항상 함께** 움직인다
 * 타이머 상태가 바뀔 때마다 알람 예약도 같이 손봐야 하는데, 이 둘이 갈라지면
 * "앱에는 멈춰 있는데 알람은 울린다" 같은 상태가 생긴다. 병동에서 처치 시각을 다루는
 * 기능이라 그런 어긋남이 제일 위험하다. 그래서 **모든 변경을 이 클래스 하나로 통과**시키고,
 * 각 함수가 저장·예약을 한 묶음으로 처리한다.
 *
 * ## 시각은 [TimerClock] 으로 주입받는다
 * `System.currentTimeMillis()` 를 안에서 부르면 테스트가 실제 시계에 묶인다. 전이 규칙은
 * [CareTimerTransitions] 가 순수 함수로 갖고 있고, 여기서는 그 결과를 저장·예약에 반영만 한다.
 */
@Singleton
class TimerRepository @Inject constructor(
    private val store: TimerStore,
    private val scheduler: TimerAlarmScheduler,
    private val clock: TimerClock
) {

    val timers: Flow<List<CareTimer>> = store.timers
    val alertMode: Flow<AlertMode> = store.alertMode

    /**
     * 앱이 다시 뜰 때 목록을 현재 시각에 맞춘다.
     *
     * 강제 종료돼 있던 동안 만료한 타이머는 RINGING 으로 올라간다. 그 타이머의 알람은
     * **이미 울렸거나 사용자가 놓친 것**이므로 다시 예약하지 않는다. 아직 안 만료한
     * RUNNING 은 예약을 되살린다 — 기기 재부팅으로 AlarmManager 가 비었을 수 있다.
     */
    suspend fun restore() {
        val now = clock.now()
        val restored = CareTimerTransitions.restore(store.currentTimers(), now)
        store.updateTimers(restored)
        restored.filter { it.state == TimerState.RUNNING }.forEach(scheduler::schedule)
    }

    /** 프리셋 원탭 → 즉시 시작 + 알람 예약. */
    suspend fun start(preset: TimerPreset): CareTimer {
        val timer = CareTimerTransitions.start(preset, UUID.randomUUID().toString(), clock.now())
        store.updateTimers(store.currentTimers() + timer)
        scheduler.schedule(timer)
        return timer
    }

    /** 일시정지 — 알람 예약도 함께 지운다. 안 지우면 멈춰 있는데 울린다. */
    suspend fun pause(timerId: String) = mutate(timerId) { timer ->
        CareTimerTransitions.pause(timer, clock.now()).also {
            if (it.state == TimerState.PAUSED) scheduler.cancel(timerId)
        }
    }

    /** 재개 — 새 만료 시각으로 다시 예약한다. */
    suspend fun resume(timerId: String) = mutate(timerId) { timer ->
        CareTimerTransitions.resume(timer, clock.now()).also {
            if (it.state == TimerState.RUNNING) scheduler.schedule(it)
        }
    }

    /** [+1분] — 만료 시각이 밀리므로 예약을 새 시각으로 덮어쓴다. */
    suspend fun extend(timerId: String) = mutate(timerId) { timer ->
        CareTimerTransitions.extend(timer).also {
            if (it.state == TimerState.RUNNING) scheduler.schedule(it)
        }
    }

    suspend fun setMemo(timerId: String, memo: String?) = mutate(timerId) { timer ->
        timer.copy(memo = memo?.takeIf(String::isNotBlank))
    }

    /** 알람이 울렸다 — 리시버가 부른다. */
    suspend fun markRinging(timerId: String) = mutate(timerId, CareTimerTransitions::ring)

    /**
     * 완료·정지 — **둘 다 목록에서 삭제**다(spec §생성 → 실행).
     *
     * 울리는 중이었다면 그 알람도 함께 끈다.
     */
    suspend fun remove(timerId: String) {
        val current = store.currentTimers()
        val target = current.firstOrNull { it.id == timerId } ?: return
        store.updateTimers(current.filterNot { it.id == timerId })
        if (target.state == TimerState.RINGING) scheduler.dismiss(timerId) else scheduler.cancel(timerId)
    }

    suspend fun setAlertMode(mode: AlertMode) = store.updateAlertMode(mode)

    private suspend fun mutate(timerId: String, transform: (CareTimer) -> CareTimer) {
        val current = store.currentTimers()
        if (current.none { it.id == timerId }) return
        store.updateTimers(current.map { if (it.id == timerId) transform(it) else it })
    }
}

/**
 * 프리셋 관리 — 타이머 실행과 분리한다.
 *
 * 프리셋은 **알람 스케줄러를 전혀 건드리지 않는다**(템플릿일 뿐이고, 실행되는 순간 값이
 * 타이머로 복사된다). 그래서 [TimerRepository] 처럼 "저장과 예약을 함께" 지킬 필요가 없고,
 * 한 클래스에 같이 두면 그 원칙이 어디까지 적용되는지 흐려진다.
 */
@Singleton
class TimerPresetRepository @Inject constructor(private val store: TimerStore) {

    /** 노출 순서 = `sortOrder` 오름차순. 프리셋을 보여주는 모든 표면이 이 흐름을 쓴다. */
    val presets: Flow<List<TimerPreset>> = store.presets.map(CareTimerTransitions::sorted)

    suspend fun upsert(preset: TimerPreset) {
        val current = store.currentPresets()
        val next = if (current.any { it.id == preset.id }) {
            current.map { if (it.id == preset.id) preset else it }
        } else {
            current + preset.copy(sortOrder = current.size)
        }
        store.updatePresets(CareTimerTransitions.sorted(next))
    }

    suspend fun delete(presetId: String) {
        // 지운 뒤 번호를 다시 매긴다 — 안 하면 구멍이 남아 다음 추가가 뒤로 밀린다.
        val next = store.currentPresets().filterNot { it.id == presetId }
        store.updatePresets(CareTimerTransitions.reorder(CareTimerTransitions.sorted(next)))
    }

    /** 드래그 정렬 — 담긴 순서대로 `sortOrder` 를 다시 매겨 저장한다. */
    suspend fun reorder(ordered: List<TimerPreset>) {
        store.updatePresets(CareTimerTransitions.reorder(ordered))
    }
}
