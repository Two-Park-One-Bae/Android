package app.nursemate.telemetry

/**
 * 앱 분석 이벤트 택소노미 (Firebase Analytics 단독).
 *
 * ## iOS 와 **문자 단위로 같아야 한다**
 * 한 GA4 속성에 두 플랫폼이 함께 쌓인다. 이름이나 파라미터가 한 글자라도 갈리면 리포트에서
 * 두 줄로 쪼개지고, 그때는 이미 쌓인 데이터라 되돌릴 수가 없다. 정본은
 * `iOS/Projects/Core/Sources/Analytics/AnalyticsEvent.swift` 이고, 여기는 그것을 옮긴 것이다.
 * 바꿔야 할 일이 생기면 **양쪽을 같이** 바꾼다.
 *
 * ## GA4 제약 (값은 [AnalyticsEventTest] 가 강제한다)
 *  - 이벤트 이름 snake_case·40자 이내, `firebase_`·`google_`·`ga_` 로 시작 금지
 *  - 파라미터 이름 40자 이내, 이벤트당 25개 이내
 *  - 문자열 파라미터 값 100자 이내
 *  - 리포트에서 파라미터를 보려면 콘솔에 **커스텀 측정기준**으로 등록해야 한다(이벤트범위 최대 50개)
 *
 * ## 순번·횟수는 숫자와 문자열 **두 벌**로 보낸다
 * GA4 는 파라미터 하나를 측정기준 또는 측정항목 한쪽으로만 등록할 수 있는데 두 쓰임이 다 필요하다.
 *  - `pill_index`·`candidate_index`·`edit_count`(Long) → **측정항목**. 평균을 본다.
 *  - `*_label`·`*_bucket`(String) → **측정기준**. 분포를 보거나 다른 지표를 쪼갠다.
 *
 * 측정기준이 문자열이어야 하는 건 GA4 가 값을 `string_value`/`int_value` 에 따로 담고
 * 측정기준은 `string_value` 만 읽기 때문이다 — 숫자로 보내면 분류 축에서 전부 `(not set)` 이 된다.
 */
sealed interface AnalyticsEvent {

    val name: String
    val params: Map<String, Any>

    // ────────────────────────────── 공통 ──────────────────────────────

    /** 각종 버튼 탭 — [target] 에 버튼 종류(share/back/edit …), [screen] 에 화면명. */
    data class ButtonTap(val target: String, val screen: String) : AnalyticsEvent {
        override val name = "button_tap"
        override val params get() = mapOf("target" to target, "screen" to screen)
    }

    /** 공유 완료. GA4 권장 이벤트라 이름·파라미터를 그 규격에 맞춘다. */
    data class Share(val method: String, val contentType: String) : AnalyticsEvent {
        override val name = "share"
        override val params get() = mapOf("method" to method, "content_type" to contentType)
    }

    /**
     * 권한 프롬프트 응답.
     *
     * @param permission `camera`/`alarm`
     * @param result `granted`/`denied`
     * @param gate 어느 진입점에서 물었나 — 거부율을 화면별로 본다
     */
    data class PermissionResult(val permission: String, val result: String, val gate: String) : AnalyticsEvent {
        override val name = "permission_result"
        override val params get() = mapOf("permission" to permission, "result" to result, "gate" to gate)
    }

    // ───────────────────────────── 알약 인식 ─────────────────────────────

    /**
     * 분석 시작 — 사진을 확정해 분석에 들어간 순간. **식별 성공률의 분모.**
     *
     * 로딩 화면엔 나갈 방법이 없어(뒤로가기가 없다) 대기 중 이탈은 앱 강제 종료뿐인데,
     * 그 순간엔 전송이 보장되지 않아 이탈 시점에 직접 찍을 수가 없다.
     * 대신 분모를 남겨 **`started` − `result` 로 빼서** 센다.
     */
    data object PillIdentifyStarted : AnalyticsEvent {
        override val name = "pill_identify_started"
        override val params get() = emptyMap<String, Any>()
    }

    /**
     * 분석 시도의 최종 결과.
     *
     * **이건 분모가 아니다.** 결과가 나와야 찍히므로 로딩 중 이탈한 시도는 여기 없다.
     * 성공률을 낼 땐 [PillIdentifyStarted] 를 분모로 쓸 것.
     *
     * @param outcome `success`/`empty`/`failure` (한도 소진은 [PillLimitReached] 로 따로 센다)
     * @param analysisMs 로딩 진입부터 결과까지, 즉 **사용자가 기다린 시간**
     *   (온디바이스 검출 + 서버 왕복 포함). 실패에도 붙여 "얼마나 빨리 실패하는가"를 본다.
     */
    data class PillIdentifyResult(val outcome: String, val pillCount: Int, val analysisMs: Long) : AnalyticsEvent {
        override val name = "pill_identify_result"
        override val params get() = mapOf(
            "outcome" to outcome,
            "pill_count" to pillCount.toLong(),
            "analysis_ms" to analysisMs,
            "analysis_bucket" to analysisBucket(analysisMs)
        )
    }

    /** 속성 수정 1회. */
    data class PillAttrEdit(val attribute: String, val pillIndex: Int) : AnalyticsEvent {
        override val name = "pill_attr_edit"
        override val params get() = mapOf(
            "attribute" to attribute,
            "pill_index" to pillIndex.toLong(),
            "pill_index_label" to pillIndexLabel(pillIndex)
        )
    }

    /**
     * 알약 1개 확정(후보 선택).
     *
     * @param candidateIndex **1 기반**으로 넘긴다 — 리포트에서 "1번째 후보"로 읽히게.
     * @param dwellMs 수정 화면이 실제로 떠 있던 시간의 **누적**. 백그라운드·탭 전환은 빠지고,
     *   같은 알약을 여러 번 열면 모든 방문이 합산된다.
     */
    data class PillConfirm(
        val pillIndex: Int,
        val candidateIndex: Int,
        val editCount: Int,
        val editedAttrs: String,
        val dwellMs: Long
    ) : AnalyticsEvent {
        override val name = "pill_confirm"
        override val params get() = mapOf(
            "pill_index" to pillIndex.toLong(),
            "pill_index_label" to pillIndexLabel(pillIndex),
            "candidate_index" to candidateIndex.toLong(),
            "candidate_index_label" to candidateIndexLabel(candidateIndex),
            "edit_count" to editCount.toLong(),
            "edit_count_bucket" to editCountBucket(editCount),
            "edited_attrs" to editedAttrs,
            "dwell_ms" to dwellMs,
            "dwell_bucket" to dwellBucket(dwellMs)
        )
    }

    /** 확정 없이 알약 수정 화면을 이탈 — 어떤 알약·어떤 속성을 입력 중이었나. */
    data class PillFlowExit(
        val pillIndex: Int,
        val editingAttribute: String,
        val enteredValues: String,
        val editCount: Int
    ) : AnalyticsEvent {
        override val name = "pill_flow_exit"
        override val params get() = mapOf(
            "pill_index" to pillIndex.toLong(),
            "pill_index_label" to pillIndexLabel(pillIndex),
            "editing_attribute" to editingAttribute,
            "entered_values" to enteredValues,
            "edit_count" to editCount.toLong(),
            "edit_count_bucket" to editCountBucket(editCount)
        )
    }

    /**
     * 사용 한도 **소진** — 마지막 1회를 쓴 요청이 성공으로 돌아온 순간 1회.
     *
     * 파라미터가 없다. 발화 조건이 "잔여 0 도달" 하나뿐이라 어떤 값을 붙여도 상수가 되고,
     * 상수 축은 이벤트 수를 세는 것과 같은 정보를 되풀이할 뿐이다.
     *
     * ⚠️ **막힌 시도마다 찍는 게 아니다.** iOS 가 그렇게 했다가 접었다 —
     * 소진 후 재시도하지 않은 사용자가 통째로 빠지고 재시도한 사용자는 중복으로 잡혔다.
     */
    data object PillLimitReached : AnalyticsEvent {
        override val name = "pill_limit_reached"
        override val params get() = emptyMap<String, Any>()
    }

    /** 알약 식별 완주 — ⑨ 완료 버튼. (완료 수 / 시작 수). 완주 시 미확정 = 0. */
    data class PillIdentifyComplete(
        val detectedCount: Int,
        val confirmedCount: Int,
        val deletedCount: Int,
        val manualAddedCount: Int
    ) : AnalyticsEvent {
        override val name = "pill_identify_complete"
        override val params get() = mapOf(
            "detected_count" to detectedCount.toLong(),
            "confirmed_count" to confirmedCount.toLong(),
            "deleted_count" to deletedCount.toLong(),
            "manual_added_count" to manualAddedCount.toLong()
        )
    }

    /** 알약 식별 중도이탈 — ⑤ 인식결과에서 확정 없이 나감. */
    data class PillIdentifySessionExit(
        val detectedCount: Int,
        val confirmedCount: Int,
        val unconfirmedCount: Int,
        val deletedCount: Int,
        val manualAddedCount: Int,
        val elapsedSec: Long
    ) : AnalyticsEvent {
        override val name = "pill_identify_session_exit"

        // ⚠️ 경과 시간 키가 `session_elapsed_sec` 다. `timer_cancel.elapsed_sec`(타이머 경과)와
        //    겹치면 GA4 커스텀 측정기준 하나를 두 뜻으로 쓰게 된다.
        override val params get() = mapOf(
            "detected_count" to detectedCount.toLong(),
            "confirmed_count" to confirmedCount.toLong(),
            "unconfirmed_count" to unconfirmedCount.toLong(),
            "deleted_count" to deletedCount.toLong(),
            "manual_added_count" to manualAddedCount.toLong(),
            "session_elapsed_sec" to elapsedSec
        )
    }

    // ────────────────────────────── 타이머 ──────────────────────────────

    /** 타이머 시작 — [source] 는 `phone`/`widget`/`watch`. */
    data class TimerStart(val source: String, val presetLabel: String, val category: String, val durationSec: Int) :
        AnalyticsEvent {
        override val name = "timer_start"
        override val params get() = mapOf(
            "source" to source,
            "preset_label" to presetLabel,
            "category" to category,
            "duration_sec" to durationSec.toLong()
        )
    }

    /** 타이머 완료 — 끝까지 울린 뒤 [완료]. (완료율 = complete / start) */
    data class TimerComplete(val category: String, val durationSec: Int) : AnalyticsEvent {
        override val name = "timer_complete"
        override val params get() = mapOf("category" to category, "duration_sec" to durationSec.toLong())
    }

    /** 타이머 중도 취소 — 울리기 전 정지. 경과·잔여로 언제 껐는지 본다. */
    data class TimerCancel(val category: String, val elapsedSec: Int, val remainingSec: Int) : AnalyticsEvent {
        override val name = "timer_cancel"
        override val params get() = mapOf(
            "category" to category,
            "elapsed_sec" to elapsedSec.toLong(),
            "remaining_sec" to remainingSec.toLong()
        )
    }

    /** 프리셋 생성. */
    data class PresetCreate(val label: String, val category: String, val durationSec: Int) : AnalyticsEvent {
        override val name = "preset_create"
        override val params get() = presetParams(label, category, durationSec)
    }

    /** 프리셋 수정. */
    data class PresetEdit(val label: String, val category: String, val durationSec: Int) : AnalyticsEvent {
        override val name = "preset_edit"
        override val params get() = presetParams(label, category, durationSec)
    }

    /** 프리셋 삭제. */
    data class PresetDelete(val label: String, val category: String, val durationSec: Int) : AnalyticsEvent {
        override val name = "preset_delete"
        override val params get() = presetParams(label, category, durationSec)
    }

    companion object {

        private fun presetParams(label: String, category: String, durationSec: Int) = mapOf(
            "preset_label" to label,
            "category" to category,
            "duration_sec" to durationSec.toLong()
        )

        /*
         라벨 규칙:
          - **두 자리 제로 패딩** — 문자열은 사전순 정렬이라("10" < "2") 패딩해야 축이 숫자 순서로 선다
          - **상한 초과는 한 칸에**("21+"/"16+") — 카디널리티를 닫아 (other) 버킷을 막는다.
            라벨 숫자가 곧 "여기부터 묶임"이라 20·15 는 제 라벨을 갖는다
          - **1 미만은 "unknown"** — 계측 오류를 정상값에 뭉개지 않는다
         */

        /**
         * 1 기반 순번을 제로 패딩 문자열로.
         *
         * [lastLabeled] 까지는 개별 라벨(`01`·`02` …), **그 위**는 `"<lastLabeled+1>+"` 한 칸에 모은다.
         * 오버플로 라벨이 `lastLabeled+1` 인 건 경계를 값 그대로 읽히게 하려는 것이다 —
         * `20+` 로 쓰면 20 이 어느 쪽인지 리포트만 보고는 알 수 없다.
         */
        private fun indexLabel(value: Int, lastLabeled: Int): String = when {
            value < 1 -> "unknown"
            value > lastLabeled -> "${lastLabeled + 1}+"
            else -> value.toString().padStart(2, '0')
        }

        /** 알약 순번 — 수동 추가로 늘어날 수 있어 20 까지 개별, 21 부터 묶는다. */
        fun pillIndexLabel(value: Int): String = indexLabel(value, lastLabeled = 20)

        /** 후보 순번 — 호출부에서 1 기반으로 맞춰 넘긴다(목록 인덱스는 0 기반). */
        fun candidateIndexLabel(value: Int): String = indexLabel(value, lastLabeled = 15)

        /** 수정 횟수 분포용 구간. 라벨이 전부 숫자로 시작해 사전순 정렬이 곧 구간 순서가 된다. */
        fun editCountBucket(count: Int): String = when {
            count < 0 -> "unknown"
            count == 0 -> "0회"
            count == 1 -> "1회"
            count <= 3 -> "2-3회"
            count <= 5 -> "4-5회"
            else -> "6회+"
        }

        /**
         * 알약 1개를 확정하기까지 걸린 시간의 분포용 구간(`pill_confirm.dwell_bucket`).
         *
         * `dwell_ms` 는 숫자라 **측정항목**으로만 등록된다 — 평균은 보이지만 분류 축으로 쓰면
         * 전부 `(not set)` 이 된다. 분포를 보려면 문자열 구간이 필요하다.
         *
         * 30초 안쪽은 10초로 한 번 더 갈라 빠른 확정을 구분하고, 30초부터 5분까지는 30초 단위,
         * 그 위는 두 칸으로 닫아 카디널리티를 묶는다 — 개선 대상은 오래 걸린 쪽이다.
         *
         * 라벨이 `mm:ss` 인 이유 — 초와 분을 섞으면(`30초-1분`·`1-2분`) 사전순 정렬이 구간
         * 순서와 어긋난다. 자리수가 고정된 `mm:ss` 는 사전순이 곧 시간순이다.
         */
        fun dwellBucket(ms: Long): String = bucketOf(ms, DWELL_BUCKETS, overflow = "10:00+")

        /**
         * 분석 대기시간 구간(`pill_identify_result.analysis_bucket`).
         *
         * 짧은 쪽을 촘촘히 나눈 건 **대기 이탈이 앞쪽에서 갈리기 때문**이다. 3초와 5초의 차이는
         * 체감이 크지만 40초와 50초의 차이는 이미 "너무 느림" 한 덩어리다.
         */
        fun analysisBucket(ms: Long): String = bucketOf(ms, ANALYSIS_BUCKETS, overflow = "01:00+")

        /**
         * 경계 목록에서 [value] 가 들어갈 칸을 고른다. 경계는 **위쪽이 열린** 구간이다 —
         * `10_000 to "00:00-00:10"` 은 "10초 **미만**"이다.
         *
         * 음수는 계측 오류라 정상값에 섞지 않는다.
         */
        private fun bucketOf(value: Long, buckets: List<Pair<Long, String>>, overflow: String): String = when {
            value < 0 -> "unknown"
            else -> buckets.firstOrNull { value < it.first }?.second ?: overflow
        }

        private val DWELL_BUCKETS = listOf(
            10_000L to "00:00-00:10",
            30_000L to "00:10-00:30",
            60_000L to "00:30-01:00",
            90_000L to "01:00-01:30",
            120_000L to "01:30-02:00",
            150_000L to "02:00-02:30",
            180_000L to "02:30-03:00",
            210_000L to "03:00-03:30",
            240_000L to "03:30-04:00",
            270_000L to "04:00-04:30",
            300_000L to "04:30-05:00",
            600_000L to "05:00-10:00"
        )

        private val ANALYSIS_BUCKETS = listOf(
            3_000L to "00:00-00:03",
            5_000L to "00:03-00:05",
            10_000L to "00:05-00:10",
            15_000L to "00:10-00:15",
            20_000L to "00:15-00:20",
            30_000L to "00:20-00:30",
            60_000L to "00:30-01:00"
        )
    }
}
