package app.nursemate.review

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * ⚠️ 파일 이름은 백업 제외 규칙(`res/xml/backup_rules.xml` · `data_extraction_rules.xml`)과 같아야 한다 — spec 이
 * 「재설치하면 처음부터 센다」인데, 자동 백업이 되살리면 재설치해도 기록이 남는다.
 */
private val Context.reviewDataStore: DataStore<Preferences> by preferencesDataStore("review_stats")

/**
 * 스토어 리뷰 요청에 쓰는 사용 기록을 센다 — NM-561, spec §스토어 리뷰 요청(NM-618).
 *
 * 기기에만 둔다. 서버로 보내지 않고, 회원과 잇지 않는다.
 *
 * ## 세는 자리
 * | 무엇 | 어디서 |
 * |---|---|
 * | 쓴 날 · 첫 사용 | 앱을 열 때(`MainActivity.onStart`) · 타이머를 시작할 때(그 타이머가 실제로 시작된 시각) |
 * | 타이머 시작 | `TimerAnalyticsReporter.started` — 폰 · 위젯 · 워치가 한곳으로 모인다 |
 * | 식별 | 기록 저장 성공(`PillNavGraph`) |
 * | 한도 안내를 봄 | 한도 안내가 뜰 때(`PillLimitAlert`) |
 *
 * ⚠️ 폰이 연결되지 않은 동안 워치에서 시작부터 끝까지 다 쓴 타이머는 폰이 보지 못해 세지 못한다(2026-10-21 받아들임).
 */
@Singleton
class ReviewTracker @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val store get() = context.reviewDataStore

    /** 앱을 열었다 · 위젯 · 워치로 타이머를 썼다. [atMs] 는 실제로 쓴 시각 — 앱을 며칠 뒤에 열어도 그날로 센다. */
    suspend fun used(atMs: Long) {
        store.edit { p ->
            val day = epochDay(atMs)
            val kept = p[USED_DAYS].orEmpty().mapNotNull(String::toLongOrNull)
                .filter { it > day - ReviewPolicy.KEEP_DAYS } + day
            p[USED_DAYS] = kept.map(Long::toString).toSet()
            val first = p[FIRST_USE_AT]
            if (first == null || atMs < first) p[FIRST_USE_AT] = atMs
        }
    }

    /** 타이머가 시작됐다 — 앱 · 위젯 · 워치 어디서든. */
    suspend fun timerStarted(atMs: Long) {
        used(atMs)
        store.edit { it[TIMER_STARTS] = (it[TIMER_STARTS] ?: 0) + 1 }
    }

    /** 식별 기록 저장에 성공했다. */
    suspend fun identified() {
        store.edit { it[IDENTIFY_COUNT] = (it[IDENTIFY_COUNT] ?: 0) + 1 }
    }

    /** 오늘 한도 안내를 봤다 — 그날은 묶음 1 로 요청하지 않는다. */
    suspend fun limitShown(nowMs: Long = System.currentTimeMillis()) {
        store.edit { it[LIMIT_SHOWN_DAY] = epochDay(nowMs) }
    }

    /** 앱 밖에서 타이머를 끝냈다 — 다음에 앱을 열 때 검사한다. */
    suspend fun timerEndedOutside() {
        store.edit { it[ENDED_OUTSIDE] = true }
    }

    /** 앱 밖에서 끝낸 타이머가 있었으면 true 를 돌려주고 표시를 지운다. */
    suspend fun consumeEndedOutside(): Boolean {
        var was = false
        store.edit {
            was = it[ENDED_OUTSIDE] == true
            it.remove(ENDED_OUTSIDE)
        }
        return was
    }

    /**
     * 지금 요청할 묶음을 고르고, 고르면 **요청한 것으로 적는다**(창이 떴는지는 알 수 없다 — 간격은 요청 기준).
     *
     * @return 요청할 묶음 번호. 요청하지 않으면 null
     */
    suspend fun decide(
        trigger: ReviewTrigger,
        unmodified: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Int? {
        var bundle: Int? = null
        store.edit { p ->
            bundle = ReviewPolicy.bundleFor(p.toStats(), trigger, nowMs, epochDay(nowMs), unmodified)
            if (bundle != null) p[LAST_REQUEST_AT] = nowMs
        }
        Log.i(TAG, "리뷰 검사 $trigger — ${bundle?.let { "묶음 $it 로 요청" } ?: "요청 안 함"} | ${snapshot()}")
        return bundle
    }

    /** 디버그 강제 호출 · 로그용. */
    suspend fun snapshot(): ReviewStats = store.data.first().toStats()

    private fun Preferences.toStats() = ReviewStats(
        identifyCount = this[IDENTIFY_COUNT] ?: 0,
        timerStartCount = this[TIMER_STARTS] ?: 0,
        usedDays = this[USED_DAYS].orEmpty().mapNotNull(String::toLongOrNull).toSet(),
        firstUseAt = this[FIRST_USE_AT],
        lastRequestAt = this[LAST_REQUEST_AT],
        limitShownDay = this[LIMIT_SHOWN_DAY],
        timerEndedOutside = this[ENDED_OUTSIDE] == true
    )

    private companion object {
        const val TAG = "NM561"
        val IDENTIFY_COUNT = intPreferencesKey("identify_count")
        val TIMER_STARTS = intPreferencesKey("timer_starts")
        val USED_DAYS = stringSetPreferencesKey("used_days")
        val FIRST_USE_AT = longPreferencesKey("first_use_at")
        val LAST_REQUEST_AT = longPreferencesKey("last_request_at")
        val LIMIT_SHOWN_DAY = longPreferencesKey("limit_shown_day")
        val ENDED_OUTSIDE = booleanPreferencesKey("timer_ended_outside")
    }
}

/** 기기 시간대 기준 날짜(epoch day). 「오늘」 · 「쓴 날」은 사용자가 사는 날짜로 센다. */
internal fun epochDay(ms: Long): Long =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
