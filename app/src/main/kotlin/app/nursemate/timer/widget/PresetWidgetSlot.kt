package app.nursemate.timer.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import app.nursemate.core.timer.TimerPresetRepository
import app.nursemate.core.timer.TimerRepository
import app.nursemate.timer.alarm.TimerPermissions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

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
    fun slotStore(): PresetWidgetSlotStore
}

internal fun Context.timerWidgetEntryPoint(): PresetWidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, PresetWidgetEntryPoint::class.java)

/**
 * 앱을 열지 않고 곧바로 시작해도 되는가 — 권한 둘 **그리고 울림 방식 최초 선택**.
 *
 * ⚠️ **울림 방식까지 보는 이유가 있다.** Android 12·12L(API 31·32)에서는
 * `SCHEDULE_EXACT_ALARM` 이 기본 허용이고 `POST_NOTIFICATIONS` 는 런타임 권한이 아니라,
 * 권한 관문이 **한 번도 걸리지 않는다.** 그 기기에서 위젯이 곧바로
 * [TimerRepository.start] 를 부르면, 울림 방식 최초 선택이 [TimerStartGate] 에만 있는 탓에
 * 사용자가 한 번도 고르지 않은 채 기본값(소리)으로 울린다 — spec §알람 권한 흐름을 건너뛴다.
 * minSdk 26 이라 대상 기기가 실제로 있다.
 */
internal suspend fun PresetWidgetEntryPoint.canStartWithoutApp(): Boolean =
    permissions().allGranted() && timerRepository().alertModeChosen.first()
