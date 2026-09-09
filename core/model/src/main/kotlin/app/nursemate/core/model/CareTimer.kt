package app.nursemate.core.model

import kotlinx.serialization.Serializable

// ⚠️ @Serializable 클래스 안에 private companion 을 두지 않는다 — 컴파일러 플러그인이 만드는
// `serializer()` 가 그 자리에 들어가는데, private 이면 다른 모듈에서 접근할 수 없다.
private const val MILLIS_PER_SECOND = 1000L

// 처치 타이머 도메인 — 정본 `spec/feature/care-timer/domain-model.md`.
//
// 폰과 워치가 **플랫폼과 무관하게 똑같이 구현해야 하는 계약**이라 core:model 에 둔다.
// 서버에 보관하지 않는다(오프라인 동작) — 저장은 폰 로컬, 워치는 스냅샷을 받아 그린다.

/** 처치 분류. 알람 제목 `❗ [분류] 라벨` 의 대괄호 안에 그대로 들어간다. */
@Serializable
enum class TimerCategory(val label: String) {
    MEDICATION("투약"),
    TREATMENT("처치"),
    TEST("검사")
}

/**
 * 타이머 상태.
 *
 * **완료·취소는 상태가 아니라 삭제다.** MVP 에 완료 이력이 없어(듀티별 이력 = V1) 끝난
 * 타이머를 보관할 이유가 없다 — 확인하는 순간 레코드를 지운다.
 */
@Serializable
enum class TimerState { RUNNING, PAUSED, RINGING }

/**
 * 실행 중인 타이머 하나.
 *
 * ## 시간은 [endAtEpochMillis] 하나로 관리한다
 * 경과 시간을 재지 않고 만료 시각만 기억하면, 앱이 백그라운드에 있든 종료됐든 **다시 열었을 때
 * `endAt − 현재 시각` 만 계산하면 복원이 끝난다.** 알람이 울렸는지와도 무관하다.
 *
 * - **일시정지**: 남은 시간을 [remainingSeconds] 에 고정하고 멈춘다. 15분 타이머를 10분 남기고 멈추면 `600`.
 * - **재개**: `endAt = 현재 시각 + remaining` 으로 다시 계산하고 [remainingSeconds] 를 비운다.
 *
 * @param id 폰·워치가 같은 타이머를 알아보는 공통 식별자
 * @param label 처치 키워드 — 프리셋에서 복사된다
 * @param category 분류 — 프리셋에서 복사된다
 * @param memo 시작 후에 붙이는 자유 메모
 * @param durationSeconds 전체 시간. 진행률(남은/전체) 표시에 필요하다
 * @param endAtEpochMillis 만료 예정 시각 — 남은 시간 계산의 유일한 기준
 * @param remainingSeconds **일시정지 중일 때만** 값이 있다
 */
@Serializable
data class CareTimer(
    val id: String,
    val label: String,
    val category: TimerCategory,
    val memo: String? = null,
    val durationSeconds: Int,
    val endAtEpochMillis: Long,
    val remainingSeconds: Int? = null,
    val state: TimerState = TimerState.RUNNING
) {
    /** 알람·표면에 쓰는 만료 제목. spec §만료·알람 — `❗ [분류] 라벨`. */
    val alarmTitle: String get() = "❗ [${category.label}] $label"

    /** @return [now] 기준 남은 초. 일시정지 중이면 멈춘 값, 만료했으면 0 */
    fun remainingAt(now: Long): Int = when (state) {
        TimerState.PAUSED -> remainingSeconds ?: 0
        else -> (((endAtEpochMillis - now) / MILLIS_PER_SECOND).toInt()).coerceAtLeast(0)
    }

    /** 진행률 링에 쓰는 0~1 값. */
    fun progressAt(now: Long): Float = if (durationSeconds <= 0) {
        1f
    } else {
        1f - (remainingAt(now).toFloat() / durationSeconds).coerceIn(0f, 1f)
    }

    /** 아직 안 울렸는데 만료 시각을 지났는가 — 복원 시 RINGING 으로 올려야 하는지 판단한다. */
    fun isExpiredAt(now: Long): Boolean = state == TimerState.RUNNING && now >= endAtEpochMillis
}

/**
 * 프리셋 — 타이머의 템플릿.
 *
 * **실행 = 복사**다. 원탭 시 label·category·duration 이 타이머로 복사되므로, 실행 중에 프리셋을
 * 고치거나 지워도 이미 도는 타이머는 영향받지 않는다.
 *
 * @param isDefault 시드 6종 여부. 기본도 수정·삭제할 수 있다
 * @param sortOrder 사용자 정의 노출 순서(오름차순). 프리셋을 보여주는 **모든 표면**이 이 순서를 따른다
 */
@Serializable
data class TimerPreset(
    val id: String,
    val label: String,
    val category: TimerCategory,
    val durationSeconds: Int,
    val isDefault: Boolean = false,
    val sortOrder: Int
)

/**
 * 울림 방식 — **앱 전체에 하나**로 적용되는 전역 설정(타이머별 아님).
 *
 * 워치 동기화 대상이 아니다 — 워치는 이 값과 무관하게 항상 햅틱으로 울린다.
 *
 * ## Android 는 3가지다 (spec 본문은 2가지)
 * spec 이 "'진동' 단독 방식은 두지 않는다"고 쓴 근거는 **iOS 제약 #7** — iOS 시스템 알람은
 * 발화 시 항상 진동하고 앱이 끌 수 없어, 거기서는 '진동'과 '무음'이 동작상 구분되지 않는다.
 *
 * Android 는 진동이 알림 채널 속성이라 소리와 따로 끄고 켠다. 제약이 없는데 접을 이유도
 * 없어 [VIBRATE] 를 둔다(2026-09-09 결정). spec 본문 개정 요청 대상이다.
 */
@Serializable
enum class AlertMode {
    /** 소리 + 진동. 알람 스트림이라 기기 무음 모드를 뚫는다. */
    SOUND,

    /** 진동만. 소리 없이 알린다 — Android 전용. */
    VIBRATE,

    /** 조용한 알림. 소리도 진동도 없고 화면 표시만 남는다. */
    SILENT
}

/** 기본 프리셋 6종 — spec §생성 표. 첫 실행 시 이 순서로 시드한다. */
val DEFAULT_TIMER_PRESETS: List<TimerPreset> = listOf(
    Triple("AST", 15 * 60, TimerCategory.TEST),
    Triple("수혈 바이탈", 15 * 60, TimerCategory.TREATMENT),
    Triple("투약 반응 관찰", 30 * 60, TimerCategory.MEDICATION),
    Triple("해열 재검", 30 * 60, TimerCategory.TEST),
    Triple("바이탈 재측정", 15 * 60, TimerCategory.TEST),
    Triple("체위 변경", 2 * 60 * 60, TimerCategory.TREATMENT)
).mapIndexed { index, (label, seconds, category) ->
    TimerPreset(
        id = "default-$index",
        label = label,
        category = category,
        durationSeconds = seconds,
        isDefault = true,
        sortOrder = index
    )
}
