package app.nursemate.pill

import app.nursemate.telemetry.AnalyticsEvent
import app.nursemate.telemetry.AppAnalytics

/**
 * 알약 식별 **한 세션**의 지표 상태.
 *
 * 이벤트 하나를 보내는 데 필요한 값이 화면 여러 곳에 흩어져 있다 — 수정 횟수는 수정 화면,
 * 확정 순번은 후보 목록, 검출 개수는 결과 화면. 그때그때 호출부에서 모으면 화면마다 지표용
 * 변수가 하나씩 생기고, 그 중 하나만 초기화를 빠뜨려도 **다음 사진의 수치에 앞 사진이 섞인다.**
 * 그래서 세션 값을 여기 한곳에 모으고 [reset] 하나로 비운다.
 *
 * ## 체류시간은 **화면이 실제로 떠 있던 구간만** 더한다
 * 벽시계(진입 시각 ~ 확정 시각)를 쓰면 두 방향으로 틀어진다. iOS 가 실측으로 확인한 값이다
 * (`PillDwellTracker.swift`):
 *
 *  - **과대** — 탭을 옮기거나 앱을 내려도 시계가 간다. 30초 백그라운드가 얹혀 72.8초짜리
 *    작업이 102.8초로 기록됐고 구간이 한 칸 밀렸다.
 *  - **과소** — 화면을 나갔다 다시 들어오면 0부터 다시 갔다. 두 번에 걸쳐 고민하면
 *    마지막 방문만 남았다.
 *
 * 그래서 [enterEdit]/[leaveEdit] 로 구간을 끊어 더하고, 그 합을 알약별로 세션 내내 들고 있는다.
 * 후보 세부정보(⑩)를 보는 시간은 **빠진다** — 그동안 수정 화면은 가려져 있다.
 *
 * ## 수동 추가 알약은 [PillAttrEdit]·[PillFlowExit] 에서 뺀다
 * 그 둘은 **모델이 뽑아 준 값을 사람이 얼마나 고치는가**를 보는 지표다. 사람이 처음부터
 * 직접 넣는 알약을 섞으면 수정률이 실제보다 높게 나온다. 확정([PillConfirm])에는 포함한다 —
 * 그건 "몇 번째 후보를 골랐나"라 수동 추가도 같은 의미를 갖는다. iOS 와 같은 규칙이다.
 */
@Suppress("TooManyFunctions") // 이벤트 택소노미의 한 면이다 — 쪼개면 세션 상태가 두 곳으로 갈린다
class PillAnalyticsSession(
    private val analytics: AppAnalytics,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** 알약별 수정 횟수. */
    private val editCounts = mutableMapOf<String, Int>()

    /** 알약별 수정한 속성 종류. */
    private val editedAttrs = mutableMapOf<String, MutableSet<String>>()

    /** 알약별 누적 체류시간(ms). */
    private val dwell = mutableMapOf<String, Long>()

    /** 지금 화면에 떠 있는 알약과 그 구간의 시작 시각. 가려져 있으면 null. */
    private var openSegment: Pair<String, Long>? = null

    /** 분석(로딩) 진입 시각. 결과가 나오면 비운다 — 재시도가 이 값을 다시 채운다. */
    private var analysisStartedAt: Long? = null

    /** ⑤ 결과 화면이 처음 뜬 시각. 중도이탈 경과시간의 기준이다. */
    private var resultShownAt: Long? = null

    /** 한도 소진을 이 세션에서 이미 보냈는가 — 같은 사실을 두 번 세지 않는다. */
    private var limitReported = false

    // ─────────────────────────── 분석 ───────────────────────────

    /** 분석 시작 — 사진을 확정해 로딩으로 들어간 순간. 성공률의 분모다. */
    fun analysisStarted() {
        analysisStartedAt = clock()
        analytics.track(AnalyticsEvent.PillIdentifyStarted)
    }

    /**
     * 분석이 끝났다.
     *
     * ⚠️ **한 번의 시도에 한 번만 보낸다.** 결과 판정이 `detection`·`attributes` 두 흐름을
     * 함께 보고 돌아서, 상태가 갱신될 때마다 부르면 같은 결과가 여러 번 실린다.
     * [analysisStartedAt] 을 비우는 것으로 그걸 막는다.
     */
    fun analysisFinished(outcome: String, pillCount: Int) {
        val startedAt = analysisStartedAt ?: return
        analysisStartedAt = null
        analytics.track(
            AnalyticsEvent.PillIdentifyResult(
                outcome = outcome,
                pillCount = pillCount,
                analysisMs = clock() - startedAt
            )
        )
    }

    /**
     * 이 시도를 **집계에서 뺀다** — 결과 이벤트 없이 분모만 닫는다.
     *
     * 429(한도 초과)가 여기로 온다. `success`/`empty`/`failure` 어디에도 넣을 수 없고,
     * 그렇다고 열어 두면 다음 시도의 `analysis_ms` 가 이번 대기까지 합쳐 부풀어 오른다.
     */
    fun analysisDiscarded() {
        analysisStartedAt = null
    }

    /**
     * 일일 한도를 **다 썼다** — 마지막 1회를 쓴 요청이 성공으로 돌아온 순간.
     *
     * ⚠️ 막힌 시도마다 찍는 게 아니다. iOS 가 그렇게 했다가 접었다 —
     * 소진 후 재시도하지 않은 사용자가 통째로 빠지고 재시도한 사용자는 중복으로 잡혔다.
     */
    fun limitReached() {
        if (limitReported) return
        limitReported = true
        analytics.track(AnalyticsEvent.PillLimitReached)
    }

    /** ⑤ 결과 화면이 떴다. 중도이탈 경과시간을 여기서부터 잰다. */
    fun resultShown() {
        if (resultShownAt == null) resultShownAt = clock()
    }

    // ─────────────────────────── 수정 ───────────────────────────

    /** 수정 화면이 떴다 — 체류 구간을 연다. */
    fun enterEdit(pillId: String) {
        openSegment = pillId to clock()
    }

    /** 수정 화면이 가려졌다 — 구간을 닫아 누적에 더한다. 탭 전환·앱 백그라운드도 여기로 온다. */
    fun leaveEdit(pillId: String) {
        val (openId, startedAt) = openSegment ?: return
        if (openId != pillId) return
        openSegment = null
        dwell[pillId] = (dwell[pillId] ?: 0L) + (clock() - startedAt)
    }

    /** 속성 1개를 고쳤다. */
    fun attrEdited(pillId: String, pillIndex: Int, attribute: String, manual: Boolean) {
        editCounts[pillId] = (editCounts[pillId] ?: 0) + 1
        editedAttrs.getOrPut(pillId) { mutableSetOf() } += attribute
        if (manual) return
        analytics.track(AnalyticsEvent.PillAttrEdit(attribute = attribute, pillIndex = pillIndex))
    }

    /** 후보를 확정했다. [candidateIndex] 는 **1 기반**이다. */
    fun confirmed(pillId: String, pillIndex: Int, candidateIndex: Int) {
        analytics.track(
            AnalyticsEvent.PillConfirm(
                pillIndex = pillIndex,
                candidateIndex = candidateIndex,
                editCount = editCounts[pillId] ?: 0,
                editedAttrs = editedAttrsOf(pillId),
                dwellMs = dwellOf(pillId)
            )
        )
    }

    /** 확정 없이 수정 화면을 나갔다. */
    fun flowExited(pillId: String, pillIndex: Int, editingAttribute: String, enteredValues: String, manual: Boolean) {
        if (manual) return
        analytics.track(
            AnalyticsEvent.PillFlowExit(
                pillIndex = pillIndex,
                editingAttribute = editingAttribute,
                enteredValues = enteredValues,
                editCount = editCounts[pillId] ?: 0
            )
        )
    }

    // ─────────────────────────── 공통 ───────────────────────────

    /**
     * 주요 버튼 탭. **아무 버튼이나 찍지 않는다** — iOS 가 찍는 것과 같은 자리만 찍는다.
     * 한쪽에만 있는 target 이 생기면 리포트에서 플랫폼 비교가 안 된다.
     */
    fun buttonTapped(target: String, screen: String) {
        analytics.track(AnalyticsEvent.ButtonTap(target = target, screen = screen))
    }

    /**
     * 권한 프롬프트 응답.
     *
     * ⚠️ **처음 뜬 프롬프트의 응답만 보낸다.** 이미 허용·거부된 상태는 프롬프트가 아니라
     * 조회일 뿐이라, 그것까지 세면 화면에 들어올 때마다 `granted` 가 쌓여 거부율이 0 으로 간다.
     */
    fun permissionAnswered(permission: String, granted: Boolean, gate: String) {
        analytics.track(
            AnalyticsEvent.PermissionResult(
                permission = permission,
                result = if (granted) "granted" else "denied",
                gate = gate
            )
        )
    }

    /** 공유 완료. */
    fun shared(method: String, contentType: String) {
        analytics.track(AnalyticsEvent.Share(method = method, contentType = contentType))
    }

    // ─────────────────────────── 세션 종료 ───────────────────────────

    /** ⑨ 완료 — 완주. */
    fun completed(summary: PillSessionSummary) {
        analytics.track(
            AnalyticsEvent.PillIdentifyComplete(
                detectedCount = summary.detectedCount,
                confirmedCount = summary.confirmedCount,
                deletedCount = summary.deletedCount,
                manualAddedCount = summary.manualAddedCount
            )
        )
    }

    /**
     * ⑤ 에서 확정 없이 나갔다.
     *
     * 결과 화면을 본 적이 없으면 보내지 않는다 — 촬영만 하고 나간 것은 식별 세션이 아니다.
     */
    fun sessionExited(summary: PillSessionSummary) {
        val shownAt = resultShownAt ?: return
        analytics.track(
            AnalyticsEvent.PillIdentifySessionExit(
                detectedCount = summary.detectedCount,
                confirmedCount = summary.confirmedCount,
                unconfirmedCount = summary.unconfirmedCount,
                deletedCount = summary.deletedCount,
                manualAddedCount = summary.manualAddedCount,
                elapsedSec = (clock() - shownAt) / MILLIS_PER_SECOND
            )
        )
    }

    /** 새 사진으로 다시 시작한다 — 앞 사진의 수치가 섞이지 않게 전부 비운다. */
    fun reset() {
        editCounts.clear()
        editedAttrs.clear()
        dwell.clear()
        openSegment = null
        analysisStartedAt = null
        resultShownAt = null
        limitReported = false
    }

    /**
     * 확정까지 고친 속성 종류.
     *
     * 빈 문자열 대신 `"none"` 을 보낸다 — GA4 에서 빈 값은 `(not set)` 으로 보여
     * "안 고쳤다"와 "파라미터가 안 왔다"가 구분되지 않는다. 수정 없는 확정이 다수라 중요하다.
     */
    private fun editedAttrsOf(pillId: String): String =
        editedAttrs[pillId]?.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",") ?: "none"

    /** 지금 열려 있는 구간까지 포함한 누적 체류시간. 확정은 화면이 떠 있는 채로 일어난다. */
    private fun dwellOf(pillId: String): Long {
        val open = openSegment
            ?.takeIf { it.first == pillId }
            ?.let { clock() - it.second }
            ?: 0L
        return (dwell[pillId] ?: 0L) + open
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * 세션 종료 이벤트 둘이 함께 쓰는 집계값.
 *
 * 화면이 네 숫자를 따로 세면 한 군데만 규칙이 어긋나도 완주와 이탈의 수치가 서로 안 맞는다.
 * 한 곳에서 만들어 둘 다 그것만 읽는다.
 */
data class PillSessionSummary(
    /** 모델이 찾아낸 개수. 지운 것도 포함한 **원래 검출 수**다. */
    val detectedCount: Int,
    /** 후보를 확정한 개수. */
    val confirmedCount: Int,
    /** 목록에 남았는데 아직 확정 안 한 개수. */
    val unconfirmedCount: Int,
    /** 사용자가 지운 검출 개수 — 오탐 비율을 여기서 본다. */
    val deletedCount: Int,
    /** 사용자가 직접 넣은 개수 — 미검출 비율을 여기서 본다. */
    val manualAddedCount: Int
)

/**
 * 지금 상태에서 세션 요약을 만든다.
 *
 * ⚠️ **`detectedCount` 는 지운 것을 빼지 않는다.** 검출 성능의 분모라 사용자가 지웠다고
 * 줄어들면 `deleted_count / detected_count`(오탐율)가 성립하지 않는다.
 */
fun PillUiState.identificationSummary(): PillSessionSummary {
    val visible = pillIds()
    val confirmed = visible.count { it in selections }
    return PillSessionSummary(
        detectedCount = (detection as? DetectionPhase.Success)?.result?.pills?.size ?: 0,
        confirmedCount = confirmed,
        unconfirmedCount = visible.size - confirmed,
        deletedCount = removedPillIds.size,
        manualAddedCount = manualPillIds.size
    )
}
