package app.nursemate.timer.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.widgetSlotDataStore: DataStore<Preferences> by preferencesDataStore("widget_slots")

/**
 * 위젯이 담은 프리셋 — **어느 위젯이 어느 프리셋을 가리키는가**.
 *
 * ## 왜 Glance 상태가 아니라 여기인가
 * 원래는 `updateAppWidgetState` 로 Glance 가 위젯 id 별로 들고 있는 Preferences 에 넣었다.
 * 그런데 **그 변경이 컴포지션에 전달되지 않는다** — 지정을 연속으로 두 번 하면 두 번째가
 * 화면에 붙지 않았다. 실기기 로그로 확인했다: 두 번째 `updateAll` 뒤에 컴포지션이 아예
 * 다시 돌지 않는다(그리는 로그가 안 찍힌다).
 *
 * 프리셋 목록은 같은 자리에서 잘 흐른다 — 그쪽은 **우리 DataStore 를 Flow 로 구독**하기
 * 때문이다. 슬롯도 같은 길로 옮겨 확실히 반응하게 한다.
 *
 * 위젯을 지우면 [clear] 로 함께 지운다(Glance 상태와 달리 저절로 사라지지 않는다).
 */
@Singleton
class PresetWidgetSlotStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** 위젯 id → 프리셋 id. 없는 위젯은 아직 지정 전이다. */
    val slots: Flow<Map<Int, String>> = context.widgetSlotDataStore.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { (key, value) ->
            val id = key.name.removePrefix(PREFIX).toIntOrNull()
            if (key.name.startsWith(PREFIX) && id != null && value is String) id to value else null
        }.toMap()
    }

    suspend fun assign(appWidgetId: Int, presetId: String) {
        context.widgetSlotDataStore.edit { it[key(appWidgetId)] = presetId }
    }

    suspend fun clear(appWidgetId: Int) {
        context.widgetSlotDataStore.edit { it.remove(key(appWidgetId)) }
    }

    private fun key(appWidgetId: Int) = stringPreferencesKey("$PREFIX$appWidgetId")

    private companion object {
        const val PREFIX = "slot_"
    }
}
