package app.nursemate.telemetry

import app.nursemate.telemetry.AnalyticsEvent.Companion.analysisBucket
import app.nursemate.telemetry.AnalyticsEvent.Companion.candidateIndexLabel
import app.nursemate.telemetry.AnalyticsEvent.Companion.dwellBucket
import app.nursemate.telemetry.AnalyticsEvent.Companion.editCountBucket
import app.nursemate.telemetry.AnalyticsEvent.Companion.pillIndexLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 이벤트 택소노미를 **런타임 전에** 잡는다.
 *
 * Firebase Analytics 는 규격을 어긴 이벤트·파라미터를 **조용히 버린다.** 예외도 없고
 * 반환값도 없다 — 콘솔에서 며칠 뒤 "그 축이 안 보인다"로만 드러난다.
 * 이름 오타·40자 초과·예약 접두사·숫자 타입 실수가 전부 그렇다. 그래서 여기서 막는다.
 */
class AnalyticsEventTest {

    /**
     * 카탈로그 전수. **새 이벤트를 추가하면 여기에도 넣는다** — 아래 규격 테스트가
     * 이 목록만 훑기 때문이다.
     */
    private val catalog: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.ButtonTap(target = "share", screen = "pill_final"),
        AnalyticsEvent.Share(method = "copy_text", contentType = "pill_result"),
        AnalyticsEvent.PermissionResult(permission = "camera", result = "granted", gate = "pill"),
        AnalyticsEvent.PillIdentifyStarted,
        AnalyticsEvent.PillIdentifyResult(outcome = "success", pillCount = 3, analysisMs = 12_900),
        AnalyticsEvent.PillAttrEdit(attribute = "color", pillIndex = 1),
        AnalyticsEvent.PillConfirm(
            pillIndex = 1,
            candidateIndex = 2,
            editCount = 3,
            editedAttrs = "color,shape",
            dwellMs = 45_000
        ),
        AnalyticsEvent.PillFlowExit(
            pillIndex = 2,
            editingAttribute = "imprint",
            enteredValues = "AB1",
            editCount = 1
        ),
        AnalyticsEvent.PillLimitReached,
        AnalyticsEvent.PillIdentifyComplete(
            detectedCount = 3,
            confirmedCount = 3,
            deletedCount = 0,
            manualAddedCount = 1
        ),
        AnalyticsEvent.PillIdentifySessionExit(
            detectedCount = 3,
            confirmedCount = 1,
            unconfirmedCount = 2,
            deletedCount = 0,
            manualAddedCount = 0,
            elapsedSec = 90
        ),
        AnalyticsEvent.TimerStart(
            source = "phone",
            presetLabel = "수혈 바이탈",
            category = "처치",
            durationSec = 900
        ),
        AnalyticsEvent.TimerComplete(category = "처치", durationSec = 900),
        AnalyticsEvent.TimerCancel(category = "처치", elapsedSec = 120, remainingSec = 780),
        AnalyticsEvent.PresetCreate(label = "항생제", category = "투약", durationSec = 1_800),
        AnalyticsEvent.PresetEdit(label = "항생제", category = "투약", durationSec = 1_800),
        AnalyticsEvent.PresetDelete(label = "항생제", category = "투약", durationSec = 1_800)
    )

    // ─────────────────────────── GA4 규격 ───────────────────────────

    @Test
    fun `이벤트 이름은 snake_case 40자 이내다`() {
        val snakeCase = Regex("^[a-z][a-z0-9_]*$")
        catalog.forEach { event ->
            assertTrue("${event.name} 이 snake_case 가 아니다", snakeCase.matches(event.name))
            assertTrue("${event.name} 이 40자를 넘는다", event.name.length <= MAX_NAME)
        }
    }

    @Test
    fun `이벤트 이름에 예약 접두사를 쓰지 않는다`() {
        // firebase_·google_·ga_ 로 시작하면 SDK 가 거부한다.
        catalog.forEach { event ->
            RESERVED_PREFIXES.forEach { prefix ->
                assertTrue("${event.name} 이 예약 접두사 $prefix 로 시작한다", !event.name.startsWith(prefix))
            }
        }
    }

    @Test
    fun `이벤트 이름이 겹치지 않는다`() {
        val names = catalog.map { it.name }
        assertEquals("같은 이름을 쓰는 이벤트가 있다: $names", names.size, names.distinct().size)
    }

    @Test
    fun `파라미터 이름은 snake_case 40자 이내고 이벤트당 25개를 넘지 않는다`() {
        val snakeCase = Regex("^[a-z][a-z0-9_]*$")
        catalog.forEach { event ->
            assertTrue("${event.name} 파라미터가 25개를 넘는다", event.params.size <= MAX_PARAMS)
            event.params.keys.forEach { key ->
                assertTrue("${event.name}.$key 가 snake_case 가 아니다", snakeCase.matches(key))
                assertTrue("${event.name}.$key 가 40자를 넘는다", key.length <= MAX_NAME)
            }
        }
    }

    @Test
    fun `파라미터 값은 GA4 가 받는 타입만 쓴다`() {
        // ⚠️ Int 를 넣으면 bundleOf 가 putInt 로 담고 SDK 는 정수 축을 Long 으로만 읽어
        //    리포트에서 (not set) 이 된다. 카탈로그가 애초에 Long 으로 넘겨야 한다.
        catalog.forEach { event ->
            event.params.forEach { (key, value) ->
                assertTrue(
                    "${event.name}.$key 가 ${value::class.simpleName} 다 — String·Long·Double 만 된다",
                    value is String || value is Long || value is Double
                )
            }
        }
    }

    @Test
    fun `문자열 파라미터 값이 100자를 넘지 않는다`() {
        catalog.forEach { event ->
            event.params.forEach { (key, value) ->
                if (value is String) {
                    assertTrue("${event.name}.$key 가 100자를 넘는다", value.length <= MAX_STRING_VALUE)
                }
            }
        }
    }

    // ─────────────────────── iOS 와 같은 이름인가 ───────────────────────

    @Test
    fun `이벤트 이름이 iOS 카탈로그와 같다`() {
        // iOS/Projects/Core/Sources/Analytics/AnalyticsEvent.swift 의 name 과 한 글자도 다르면 안 된다.
        // 한 GA4 속성에 두 플랫폼이 함께 쌓여, 갈리면 리포트가 두 줄로 쪼개진다.
        val ios = setOf(
            "button_tap", "share", "permission_result",
            "pill_identify_started", "pill_identify_result", "pill_attr_edit",
            "pill_confirm", "pill_flow_exit", "pill_limit_reached",
            "pill_identify_complete", "pill_identify_session_exit",
            "timer_start", "timer_complete", "timer_cancel",
            "preset_create", "preset_edit", "preset_delete"
        )
        assertEquals(ios, catalog.map { it.name }.toSet())
    }

    @Test
    fun `타이머 경과와 식별 세션 경과는 다른 키를 쓴다`() {
        // 같은 키를 두 뜻으로 쓰면 커스텀 측정기준 하나에 섞여 어느 쪽 값인지 구분할 수 없다.
        val cancel = AnalyticsEvent.TimerCancel("처치", elapsedSec = 10, remainingSec = 20).params
        val exit = AnalyticsEvent.PillIdentifySessionExit(1, 0, 1, 0, 0, elapsedSec = 10).params
        assertTrue("elapsed_sec" in cancel)
        assertTrue("session_elapsed_sec" in exit)
        assertTrue("식별 세션이 elapsed_sec 를 쓰고 있다", "elapsed_sec" !in exit)
    }

    // ─────────────────────────── 라벨·버킷 ───────────────────────────

    @Test
    fun `순번 라벨은 제로 패딩이라 사전순이 곧 숫자순이다`() {
        assertEquals("01", pillIndexLabel(1))
        assertEquals("09", pillIndexLabel(9))
        assertEquals("10", pillIndexLabel(10))
        // 패딩이 없으면 "10" < "2" 라 리포트 축이 뒤집힌다.
        assertTrue(pillIndexLabel(2) < pillIndexLabel(10))
    }

    @Test
    fun `상한 숫자는 제 라벨을 갖고 그 위만 묶인다`() {
        // 라벨 숫자가 곧 "여기부터 묶임"이다 — 20+ 로 쓰면 20 이 어느 쪽인지 알 수 없다.
        assertEquals("20", pillIndexLabel(20))
        assertEquals("21+", pillIndexLabel(21))
        assertEquals("21+", pillIndexLabel(999))
        assertEquals("15", candidateIndexLabel(15))
        assertEquals("16+", candidateIndexLabel(16))
    }

    @Test
    fun `1 미만 순번은 unknown 이다`() {
        // 0 기반 인덱스를 그대로 넘기는 실수를 정상값에 뭉개지 않는다.
        assertEquals("unknown", pillIndexLabel(0))
        assertEquals("unknown", pillIndexLabel(-1))
        assertEquals("unknown", candidateIndexLabel(0))
    }

    @Test
    fun `수정 횟수 버킷 경계`() {
        assertEquals("unknown", editCountBucket(-1))
        assertEquals("0회", editCountBucket(0))
        assertEquals("1회", editCountBucket(1))
        assertEquals("2-3회", editCountBucket(2))
        assertEquals("2-3회", editCountBucket(3))
        assertEquals("4-5회", editCountBucket(4))
        assertEquals("4-5회", editCountBucket(5))
        assertEquals("6회+", editCountBucket(6))
    }

    @Test
    fun `체류시간 버킷 경계는 위쪽이 열려 있다`() {
        assertEquals("unknown", dwellBucket(-1))
        assertEquals("00:00-00:10", dwellBucket(0))
        assertEquals("00:00-00:10", dwellBucket(9_999))
        assertEquals("00:10-00:30", dwellBucket(10_000))
        assertEquals("00:30-01:00", dwellBucket(30_000))
        assertEquals("04:30-05:00", dwellBucket(299_999))
        assertEquals("05:00-10:00", dwellBucket(300_000))
        assertEquals("10:00+", dwellBucket(600_000))
    }

    @Test
    fun `분석 대기 버킷 경계`() {
        assertEquals("unknown", analysisBucket(-1))
        assertEquals("00:00-00:03", analysisBucket(0))
        assertEquals("00:03-00:05", analysisBucket(3_000))
        assertEquals("00:05-00:10", analysisBucket(5_000))
        // 시뮬레이터 실측 1건(알약 25개, 12.9초)이 여기 들어간다.
        assertEquals("00:10-00:15", analysisBucket(12_900))
        assertEquals("01:00+", analysisBucket(60_000))
    }

    @Test
    fun `mm ss 라벨은 사전순이 곧 시간순이다`() {
        // 초와 분을 섞으면(30초-1분 · 1-2분) 정렬이 구간 순서와 어긋난다.
        val ordered = listOf(0L, 10_000L, 30_000L, 60_000L, 300_000L, 600_000L).map(::dwellBucket)
        assertEquals(ordered, ordered.sorted())
    }

    private companion object {
        const val MAX_NAME = 40
        const val MAX_PARAMS = 25
        const val MAX_STRING_VALUE = 100
        val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")
    }
}
