package app.nursemate.core.timer

import app.nursemate.core.model.AlertMode
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.CareTimerTransitions
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerReplica
import app.nursemate.core.model.TimerState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /**
     * 앱이 새로 뜰 때, 남아 있는 만료 알림을 모두 걷어낸다.
     *
     * 알림은 시스템이 들고 있어 **저장소와 수명이 다르다.** 앱 데이터가 지워지거나 재설치되면
     * 타이머만 사라지고 알림은 남는데, 그것들은 스와이프가 막혀 있어(`setOngoing`) 사용자가
     * 스스로 지울 방법이 없다.
     *
     * ⚠️ **어느 알림이 고아인지 골라낼 수 없다.** `getActiveNotifications()` 는 **지금
     * 프로세스가 띄운 것만** 돌려주므로, 재시작 뒤에는 빈 목록이 온다. 그래서 살릴 것을
     * 가리지 않고 전부 걷는다 — 앱이 막 떴다면 울리는 중인 알람은 있을 수 없고, 놓친 만료는
     * 화면의 만료 카드가 알려 준다.
     */
    fun dismissAllAlarms()
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

    /** 복원이 겹치지 않게 막는다 — 앱 시작과 `BOOT_COMPLETED` 가 함께 올 수 있다. */
    private val restoreLock = Mutex()

    /** 이 프로세스에서 복원이 한 번이라도 돌았는가. 고아 알림 청소를 한 번으로 묶는다. */
    private var restoredOnce = false

    val timers: Flow<List<CareTimer>> = store.timers

    /** 상대에게 보낼 복제본. 동기화 계층만 본다. */
    val replica: Flow<TimerReplica> = store.replica

    val alertMode: Flow<AlertMode> = store.alertMode

    /** 울림 방식을 아직 한 번도 고르지 않았다면 첫 시작 시트를 띄운다(spec §알람 권한). */
    val alertModeChosen: Flow<Boolean> = store.alertModeChosen

    /**
     * 앱이 다시 뜰 때 목록을 현재 시각에 맞춘다.
     *
     * 강제 종료돼 있던 동안 만료한 타이머는 RINGING 으로 올라간다. 그 타이머의 알람은
     * **이미 울렸거나 사용자가 놓친 것**이므로 다시 예약하지 않는다. 아직 안 만료한
     * RUNNING 은 예약을 되살린다 — 기기 재부팅으로 AlarmManager 가 비었을 수 있다.
     */
    suspend fun restore() = restoreLock.withLock {
        val firstOfProcess = !restoredOnce
        restoredOnce = true
        val now = clock.now()
        var restored = emptyList<CareTimer>()
        store.mutateTimers { current ->
            CareTimerTransitions.restore(current, now).also { restored = it }
        }
        restored.filter { it.state == TimerState.RUNNING }.forEach(scheduler::schedule)

        // ⚠️ **남은 타이머가 없을 때만 걷는다.** 이건 `cancelAll()` 이라 채널을 가리지 않는다.
        // 앱이 죽어 있는 동안 알람이 울렸다면 리시버가 방금 띄운 알림까지 지워 버린다 —
        // 두 코루틴이 나란히 도는 터라 순서가 실행마다 달라, 간헐적으로만 사라졌다.
        //
        // 고아 알림은 앱 데이터가 비워졌을 때 남는데, 그때는 목록도 비어 있다. 그 경우로
        // 좁히면 방금 울린 알림을 건드리지 않으면서 고아는 여전히 걷어낸다.
        // ⚠️ **한 프로세스에서 한 번만 걷는다.** 설치 직후 시스템이 `BOOT_COMPLETED` 를
        // 함께 배달해(부팅한 지 7시간이 지난 기기에서도 그랬다) 복원이 **동시에 두 번**
        // 돈다. 그때마다 걷으면 방금 띄운 알림을 지울 창이 두 배가 된다.
        if (firstOfProcess && restored.isEmpty()) scheduler.dismissAllAlarms()
    }

    /** 프리셋 원탭 → 즉시 시작 + 알람 예약. */
    suspend fun start(preset: TimerPreset): CareTimer {
        val timer = CareTimerTransitions.start(preset, UUID.randomUUID().toString(), clock.now())
        store.mutateTimers { it + timer }
        scheduler.schedule(timer)
        return timer
    }

    /** 일시정지 — 알람 예약도 함께 지운다. 안 지우면 멈춰 있는데 울린다. */
    suspend fun pause(timerId: String) {
        val updated = mutate(timerId) { CareTimerTransitions.pause(it, clock.now()) }
        if (updated?.state == TimerState.PAUSED) scheduler.cancel(timerId)
    }

    /** 재개 — 새 만료 시각으로 다시 예약한다. */
    suspend fun resume(timerId: String) {
        val updated = mutate(timerId) { CareTimerTransitions.resume(it, clock.now()) }
        if (updated?.state == TimerState.RUNNING) scheduler.schedule(updated)
    }

    /** [+1분] — 만료 시각이 밀리므로 예약을 새 시각으로 덮어쓴다. */
    suspend fun extend(timerId: String) {
        val updated = mutate(timerId) { CareTimerTransitions.extend(it) }
        if (updated?.state == TimerState.RUNNING) scheduler.schedule(updated)
    }

    suspend fun setMemo(timerId: String, memo: String?) {
        mutate(timerId) { it.copy(memo = memo?.takeIf(String::isNotBlank)) }
    }

    /**
     * 알람이 울렸다 — 리시버가 부른다.
     *
     * ⚠️ **정말 만료했는지 다시 확인한다.** 만료 직전에 [+1분] 을 누르면 `endAt` 이 미래로
     * 밀리는데, 이미 큐에 들어간 옛 알람은 그대로 발화한다. 확인 없이 올리면 1분 연장했는데도
     * 곧바로 울리고 카드가 만료로 바뀐다.
     */
    suspend fun markRinging(timerId: String) {
        mutate(timerId) { if (it.isExpiredAt(clock.now())) CareTimerTransitions.ring(it) else it }
    }

    /**
     * 완료·정지 — **둘 다 목록에서 삭제**다(spec §생성 → 실행). 예약·알림도 함께 걷는다.
     *
     * ⚠️ **타이머가 이미 없어도 알림은 지운다.** 예전에는 목록에서 못 찾으면 곧바로 돌아섰는데,
     * 그러면 알림만 남아 고아가 된다 — 화면에서 먼저 지웠거나 앱 데이터가 비워진 경우다.
     * 그 알림은 스와이프가 막혀 있어 사용자가 손쓸 방법이 없다.
     */
    suspend fun remove(timerId: String) {
        var target: CareTimer? = null
        store.mutateTimers { current ->
            target = current.firstOrNull { it.id == timerId }
            if (target == null) current else current.filterNot { it.id == timerId }
        }
        if (target?.state == TimerState.RINGING) scheduler.dismiss(timerId) else scheduler.cancel(timerId)
    }

    /**
     * 상대가 보낸 복제본을 합치고, 달라진 만큼 알람 예약을 다시 맞춘다.
     *
     * 저장은 [TimerStore.mergeReplica] 가 원자적으로 하고, 예약은 그 **뒤에** 손본다 —
     * 저장 블록 안에서 부수효과를 일으키지 않는다는 규칙은 여기서도 같다.
     *
     * @return 무언가 바뀌었으면 true. 그때만 다시 발행해야 서로 되받는 발행이 멎는다.
     */
    suspend fun mergeReplica(incoming: TimerReplica): Boolean {
        val before = store.currentTimers().associateBy { it.id }
        if (!store.mergeReplica(incoming)) return false
        reschedule(before, store.currentTimers().associateBy { it.id })
        return true
    }

    /**
     * 합친 결과에 맞춰 예약을 현실과 맞춘다.
     *
     * ⚠️ **사라진 타이머가 울리던 중이었으면 `dismiss` 다.** 상대가 끊긴 동안 [완료] 를
     * 누른 경우가 여기로 오는데, `cancel` 만 하면 **이미 떠 있는 알림이 안 지워진다.**
     */
    private fun reschedule(before: Map<String, CareTimer>, after: Map<String, CareTimer>) {
        (before.keys - after.keys).forEach { id ->
            if (before[id]?.state == TimerState.RINGING) scheduler.dismiss(id) else scheduler.cancel(id)
        }
        after.values.forEach { timer ->
            if (before[timer.id] == timer) return@forEach
            when (timer.state) {
                TimerState.RUNNING -> scheduler.schedule(timer)

                TimerState.PAUSED -> scheduler.cancel(timer.id)

                // 상대가 먼저 만료를 알렸다.
                //
                // ⚠️ **예약을 취소하면 안 된다.** 이 기기의 알람도 곧 제 시각에 울리는데,
                // 상대 소식이 몇 백 밀리초 먼저 왔다는 이유로 취소하면 **이 기기가 조용해진다.**
                // 실기기에서 그대로 겪었다 — 워치 복제본이 23:08:23.255 에 닿았고 폰 알람은
                // 23:08:23.578 이었는데, 취소돼서 폰만 안 울렸다.
                //
                // 처음 보는 타이머라면 이 기기엔 예약이 없으니 새로 건다. `endAt` 이 이미
                // 지났으므로 곧바로 울린다.
                TimerState.RINGING -> if (before[timer.id] == null) scheduler.schedule(timer)
            }
        }
    }

    suspend fun setAlertMode(mode: AlertMode) = store.updateAlertMode(mode)

    /**
     * 타이머 하나를 원자적으로 고치고 **바뀐 값을 돌려준다.**
     *
     * ⚠️ [transform] 안에서 알람을 예약하거나 지우지 않는다. 그 블록은 저장소의 갱신
     * 안에서 도는데, 부수효과를 섞으면 저장이 실패했을 때 예약만 남는다. 호출자가 돌려받은
     * 값을 보고 밖에서 예약을 다룬다.
     *
     * @return 바뀐 타이머. 그런 id 가 없으면 null.
     */
    private suspend fun mutate(timerId: String, transform: (CareTimer) -> CareTimer): CareTimer? {
        var updated: CareTimer? = null
        store.mutateTimers { current ->
            val target = current.firstOrNull { it.id == timerId } ?: return@mutateTimers current
            val next = transform(target)
            updated = next
            current.map { if (it.id == timerId) next else it }
        }
        return updated
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
