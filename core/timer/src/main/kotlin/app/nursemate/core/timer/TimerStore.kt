package app.nursemate.core.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.nursemate.core.model.AlertMode
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.DEFAULT_TIMER_PRESETS
import app.nursemate.core.model.TimerPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore("care_timer")

/**
 * 타이머·프리셋·울림 방식의 로컬 저장소.
 *
 * ## 왜 DataStore 에 JSON 블롭인가
 * 서버에 보관하지 않는 오프라인 데이터고(spec §개요), 양이 작다 — 진행 중 타이머 몇 개와
 * 프리셋 6~20개. 게다가 **폰↔워치 동기화 계약이 "전체 목록 스냅샷"**(domain-model §스냅샷)이라
 * 통째로 읽고 쓰는 모양이 그대로 맞는다. 이 규모에 Room 을 얹으면 스키마·마이그레이션만 늘어난다.
 *
 * 쓰기도 드물다 — `endAt` 이 고정이라 화면은 로컬에서 남은 시간을 계산하고, 저장은 상태가
 * 바뀔 때만 일어난다.
 *
 * 인터페이스로 두는 이유는 [TimerRepository] 를 안드로이드 없이 테스트하기 위해서다.
 *
 * ## 읽기에서 관대하게 실패한다
 * 저장된 JSON 이 깨졌거나 모델이 바뀌어 못 읽으면 **빈 목록으로 떨어진다.** 타이머를 잃는 건
 * 아프지만, 앱이 시작조차 못 하는 것보다는 낫다. 프리셋은 그 경우 기본 6종으로 되살아난다.
 */
interface TimerStore {
    val timers: Flow<List<CareTimer>>
    val presets: Flow<List<TimerPreset>>
    val alertMode: Flow<AlertMode>

    /**
     * 울림 방식을 **사용자가 직접 고른 적이 있는가.**
     *
     * 별도 플래그를 두지 않고 저장된 값의 유무로 판단한다 — 플래그와 값이 어긋나
     * "골랐다고 하는데 값이 없는" 상태가 생길 여지를 없앤다.
     */
    val alertModeChosen: Flow<Boolean>

    suspend fun currentTimers(): List<CareTimer>
    suspend fun currentPresets(): List<TimerPreset>
    suspend fun currentAlertMode(): AlertMode

    /**
     * 목록을 **한 번의 원자적 갱신**으로 바꾼다.
     *
     * ⚠️ **읽고 → 고치고 → 쓰기를 따로 하면 안 된다.** 알람 리시버는 자기 코루틴에서 돌아
     * 화면과 완전히 독립이다. 만료가 거의 동시에 오면 리시버 둘이 같은 목록을 읽고 각자
     * 자기 타이머만 울림으로 올려 쓰는데, **나중 쓰기가 앞의 것을 지운다.**
     *
     * 화면은 `now >= endAt` 을 스스로 투영해 만료로 그리므로 눈에는 멀쩡해 보이지만,
     * 저장 상태가 어긋나 헤더 카운트·정렬·다음 복원이 전부 틀어진다.
     *
     * [transform] 은 **순수해야 한다** — 알람 예약 같은 부수효과를 여기서 일으키지 않는다.
     */
    suspend fun mutateTimers(transform: (List<CareTimer>) -> List<CareTimer>)

    suspend fun updateTimers(value: List<CareTimer>)
    suspend fun updatePresets(value: List<TimerPreset>)
    suspend fun updateAlertMode(value: AlertMode)
}

@Singleton
internal class DataStoreTimerStore @Inject constructor(@param:ApplicationContext private val context: Context) :
    TimerStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val timerListSerializer = ListSerializer(CareTimer.serializer())
    private val presetListSerializer = ListSerializer(TimerPreset.serializer())

    override val timers: Flow<List<CareTimer>> = context.timerDataStore.data.map { prefs ->
        decode(prefs[KEY_TIMERS], timerListSerializer, emptyList())
    }

    /**
     * 프리셋 — 저장된 값이 없으면 **기본 6종을 시드**해 내보낸다(spec §생성).
     *
     * 시드는 읽기 시점에 값으로만 채우고 저장하지 않는다. 첫 편집이 일어날 때 [updatePresets]
     * 가 통째로 쓰므로, 손대기 전까지 굳이 디스크를 건드릴 이유가 없다.
     */
    override val presets: Flow<List<TimerPreset>> = context.timerDataStore.data.map { prefs ->
        val stored = prefs[KEY_PRESETS] ?: return@map DEFAULT_TIMER_PRESETS
        decode(stored, presetListSerializer, DEFAULT_TIMER_PRESETS)
    }

    override val alertMode: Flow<AlertMode> = context.timerDataStore.data.map { prefs ->
        prefs[KEY_ALERT_MODE]?.let { runCatching { AlertMode.valueOf(it) }.getOrNull() }
            ?: AlertMode.SOUND
    }

    override val alertModeChosen: Flow<Boolean> =
        context.timerDataStore.data.map { it[KEY_ALERT_MODE] != null }

    override suspend fun currentTimers(): List<CareTimer> = timers.first()

    override suspend fun currentPresets(): List<TimerPreset> = presets.first()

    override suspend fun currentAlertMode(): AlertMode = alertMode.first()

    /** `edit` 한 번 안에서 읽고 쓴다 — DataStore 가 이 블록을 직렬화해 준다. */
    override suspend fun mutateTimers(transform: (List<CareTimer>) -> List<CareTimer>) {
        context.timerDataStore.edit { prefs ->
            val current = decode(prefs[KEY_TIMERS], timerListSerializer, emptyList())
            prefs[KEY_TIMERS] = json.encodeToString(timerListSerializer, transform(current))
        }
    }

    override suspend fun updateTimers(value: List<CareTimer>) {
        context.timerDataStore.edit { it[KEY_TIMERS] = json.encodeToString(timerListSerializer, value) }
    }

    override suspend fun updatePresets(value: List<TimerPreset>) {
        context.timerDataStore.edit { it[KEY_PRESETS] = json.encodeToString(presetListSerializer, value) }
    }

    override suspend fun updateAlertMode(value: AlertMode) {
        context.timerDataStore.edit { it[KEY_ALERT_MODE] = value.name }
    }

    private fun <T> decode(
        raw: String?,
        serializer: kotlinx.serialization.KSerializer<List<T>>,
        fallback: List<T>
    ): List<T> {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(fallback)
    }

    private companion object {
        val KEY_TIMERS = stringPreferencesKey("timers")
        val KEY_PRESETS = stringPreferencesKey("presets")
        val KEY_ALERT_MODE = stringPreferencesKey("alert_mode")
    }
}
