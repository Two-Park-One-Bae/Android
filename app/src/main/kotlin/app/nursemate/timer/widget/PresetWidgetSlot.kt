package app.nursemate.timer.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.nursemate.core.data.timer.TimerPresetRepository
import app.nursemate.core.data.timer.TimerRepository
import app.nursemate.timer.alarm.TimerPermissions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 위젯 하나가 기억하는 것 — **어느 프리셋을 담고 있는가**.
 *
 * spec §위젯이 "사용자가 슬롯에 프리셋 하나를 지정해 둔다"고 했다. 앱이 순서 상위 N개를
 * 자동으로 채우지 않는 이유는 위젯을 몇 개 놓든 **각자 다른 프리셋**을 담아야 해서다
 * (같은 걸 자동으로 채우면 4개를 놓아도 전부 같은 타이머가 된다).
 *
 * 값은 Glance 가 위젯 id 별로 따로 들고 있는 Preferences 에 넣는다 — 위젯을 지우면 함께 지워진다.
 */
internal object PresetWidgetSlot {

    /** 이 위젯이 담은 프리셋 id. 없으면 아직 지정 전이다. */
    val PRESET_ID = stringPreferencesKey("preset_id")

    /**
     * 이 위젯으로 마지막에 시작한 시각 — 잠깐 「시작됨」을 보여 주는 데만 쓴다.
     *
     * spec §위젯: "탭하면 **시작됐다는 것이 눈에 보여야 한다**(같은 걸 두 번 눌러 타이머가
     * 둘 생기는 일을 막는다)". 위젯은 눌러도 화면이 안 바뀌어서, 표시가 없으면 안 눌린 줄 알고
     * 한 번 더 누른다.
     *
     * ⚠️ **시각을 저장하고 그릴 때 비교한다.** 「시작됨」을 켜고 끄는 두 번의 갱신 사이에
     * 프로세스가 죽으면 켠 상태로 굳는데, 시각을 들고 있으면 다음 갱신에서 저절로 풀린다.
     */
    val STARTED_AT = longPreferencesKey("started_at")
}

/**
 * 위젯에서 앱 계층을 꺼내는 통로.
 *
 * `GlanceAppWidget` 과 `ActionCallback` 은 시스템이 만드는 객체라 Hilt 가 주입할 자리가 없다.
 * 리시버에 `@AndroidEntryPoint` 를 달아도 그 안에서 만들어지는 이 둘에는 닿지 않는다.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PresetWidgetEntryPoint {
    fun presetRepository(): TimerPresetRepository
    fun timerRepository(): TimerRepository
    fun permissions(): TimerPermissions
}

internal fun Context.timerWidgetEntryPoint(): PresetWidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, PresetWidgetEntryPoint::class.java)
