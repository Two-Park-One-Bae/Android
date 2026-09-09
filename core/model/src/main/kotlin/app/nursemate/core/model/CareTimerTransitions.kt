package app.nursemate.core.model

/**
 * 타이머 상태 전이 — 정본 `spec/feature/care-timer/domain-model.md` §상태머신.
 *
 * ```
 * [*] --> RUNNING : 프리셋 원탭 (즉시 시작)
 * RUNNING --> PAUSED : 일시정지
 * PAUSED --> RUNNING : 재개
 * RUNNING --> RINGING : endAt 도달 (자동)
 * RINGING --> [*] : 완료 → 삭제
 * RUNNING/PAUSED --> [*] : 정지(취소) → 삭제
 * ```
 *
 * 순수 함수로 둔다 — 저장·알람 예약 같은 부수효과가 섞이면 이 규칙만 따로 테스트할 수 없다.
 * 시각은 인자로 받는다(`System.currentTimeMillis()` 를 안에서 부르면 테스트가 시계에 묶인다).
 */
object CareTimerTransitions {

    /** 프리셋 원탭 → 즉시 시작. '대기' 상태는 없다. */
    fun start(preset: TimerPreset, id: String, now: Long): CareTimer = CareTimer(
        id = id,
        label = preset.label,
        category = preset.category,
        durationSeconds = preset.durationSeconds,
        endAtEpochMillis = now + preset.durationSeconds * MILLIS_PER_SECOND,
        state = TimerState.RUNNING
    )

    /**
     * 일시정지 — 남은 시간을 고정한다.
     *
     * 울리는 중에는 멈출 수 없다. 멈추는 방법은 [완료]뿐이다(spec §상태머신).
     */
    fun pause(timer: CareTimer, now: Long): CareTimer = if (timer.state != TimerState.RUNNING) {
        timer
    } else {
        timer.copy(
            state = TimerState.PAUSED,
            remainingSeconds = timer.remainingAt(now)
        )
    }

    /** 재개 — 멈춘 시점의 남은 시간으로 만료 시각을 다시 계산한다. */
    fun resume(timer: CareTimer, now: Long): CareTimer = if (timer.state != TimerState.PAUSED) {
        timer
    } else {
        timer.copy(
            state = TimerState.RUNNING,
            endAtEpochMillis = now + (timer.remainingSeconds ?: 0) * MILLIS_PER_SECOND,
            remainingSeconds = null
        )
    }

    /**
     * [+1분] — 카드에서 시간을 늘린다(spec §생성 → 실행).
     *
     * 전체 시간도 함께 늘린다. 안 늘리면 진행률이 100% 를 넘어 링이 깨진다.
     * 일시정지 중이면 [remainingSeconds] 를 늘린다 — 그쪽이 남은 시간의 주인이라서다.
     */
    fun extend(timer: CareTimer, seconds: Int = EXTEND_SECONDS): CareTimer = when (timer.state) {
        TimerState.PAUSED -> timer.copy(
            durationSeconds = timer.durationSeconds + seconds,
            remainingSeconds = (timer.remainingSeconds ?: 0) + seconds
        )

        // 이미 울리는 것은 연장하지 않는다. `endAt` 만 밀면 남은 시간이 양수가 되는데 상태는
        // RINGING 이라, 카드는 만료로 그려지고 예약도 안 되는 어긋난 상태가 된다.
        TimerState.RINGING -> timer

        TimerState.RUNNING -> timer.copy(
            durationSeconds = timer.durationSeconds + seconds,
            endAtEpochMillis = timer.endAtEpochMillis + seconds * MILLIS_PER_SECOND
        )
    }

    /** `endAt` 도달 → 울림. 복원 시에도 이 함수로 판정한다. */
    fun ring(timer: CareTimer): CareTimer = if (timer.state != TimerState.RUNNING) {
        timer
    } else {
        timer.copy(state = TimerState.RINGING)
    }

    /**
     * 앱을 다시 열었을 때 목록을 현재 시각에 맞춘다.
     *
     * 알람이 울렸는지와 무관하다 — 만료 시각을 지났으면 RINGING 으로 올린다
     * (spec: "복원과 알람은 별개다").
     */
    fun restore(timers: List<CareTimer>, now: Long): List<CareTimer> =
        timers.map { if (it.isExpiredAt(now)) ring(it) else it }

    /**
     * 드래그 정렬 후 `sortOrder` 를 다시 매긴다.
     *
     * spec 이 요구하는데 iOS 는 아직 구현하지 않았다(참조 0건) — Android 는 처음부터 넣는다(NM-441).
     * 리스트에 담긴 **순서 그대로** 0부터 다시 부여한다.
     */
    fun reorder(presets: List<TimerPreset>): List<TimerPreset> =
        presets.mapIndexed { index, preset -> preset.copy(sortOrder = index) }

    /** 노출 순서 — `sortOrder` 오름차순. 프리셋을 보여주는 모든 표면이 이걸 쓴다. */
    fun sorted(presets: List<TimerPreset>): List<TimerPreset> = presets.sortedBy { it.sortOrder }

    /**
     * 리스트 노출 순서 — **울리는 것 → 진행 중 → 일시정지**, 각 묶음 안에서는 임박한 순.
     *
     * 정본 `타이머 / C1 리스트` 가 만료 카드를 맨 위에 둔다. 만료는 지금 손을 대야 하는
     * 일이라 스크롤 아래에 있으면 안 된다.
     *
     * ## 일시정지를 진행 중보다 뒤로 보낸다
     * 남은 시간만으로 줄을 세우면 **시간이 흐르는 것만으로 순서가 뒤집힌다.** 일시정지는
     * 남은 시간이 멈춰 있는데 진행 중인 것은 줄어들어, 어느 순간 진행 중인 것이 더 임박해진다.
     *
     * 화면은 매초 다시 그리니 따라가지만, 앱 밖 진행 중 알림은 목록의 모양이 바뀔 때만
     * 다시 그린다(`TimerOngoingNotifier`). 그래서 알림이 옛 순서를 그대로 붙들고,
     * **조작 버튼이 엉뚱한 타이머를 가리키는** 일이 실제로 일어났다.
     *
     * 묶음을 나누면 이 문제가 사라진다 — 진행 중인 것끼리는 `endAt` 순서라 시간이 흘러도
     * 상대 순서가 불변이고, 일시정지끼리도 멈춰 있어 불변이다. 의미로도 맞다: 멈춰 둔 것이
     * 도는 것보다 급할 이유가 없다.
     */
    fun ordered(timers: List<CareTimer>, now: Long): List<CareTimer> = timers.sortedWith(
        compareBy<CareTimer> { timer ->
            when (timer.state) {
                TimerState.RINGING -> 0
                TimerState.RUNNING -> 1
                TimerState.PAUSED -> 2
            }
        }
            // 울리는 것끼리는 **먼저 만료한 것이 위**다. 남은 시간은 전부 0 이라 순서를
            // 가르지 못하는데, 오래 놓친 것이 더 급하다.
            .thenBy { if (it.state == TimerState.RINGING) it.endAtEpochMillis else Long.MAX_VALUE }
            .thenBy { it.remainingAt(now) }
    )

    const val EXTEND_SECONDS: Int = 60
    private const val MILLIS_PER_SECOND = 1000L
}
