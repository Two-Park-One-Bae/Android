package app.nursemate.timer.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.formatDuration

/**
 * 지정 프리셋 위젯 — 정본 `타이머 / 위젯 — 잠금화면 시안 비교`.
 *
 * ## 폰에서 놓이는 자리는 **홈 화면뿐**이다
 * 정본은 잠금화면을 그렸지만 Android 의 잠금화면 위젯은 태블릿 전용이다(근거는
 * `res/xml/nm_preset_widget_info.xml`). spec 본문이 "홈 화면 위젯 = MVP 제외"라고 적은 것과
 * 정반대로, Android 폰에서 가능한 표면은 홈뿐이다. 둘 다 개정 요청 대상이다.
 *
 * ## 색은 정본을 그대로 옮기지 않았다
 * 정본의 반투명 흰색은 iOS 잠금화면이 위젯을 **모노크롬으로 강제 렌더링**하는 걸 전제한
 * 값이다. Android 는 앱이 준 색을 그대로 그리므로 벽지 위에서도 읽히도록 흰 카드에 앱 색을
 * 얹었다. 배치(키워드 위 · 아이콘+시간 아래)와 굵기 대비는 정본 그대로다.
 *
 * ## 관문 판정을 그릴 때 한다
 * 탭 동작 자체를 갈아 끼운다 — 관문을 다 지났으면 콜백으로 바로 시작하고, 아니면 앱을 연다
 * (spec §위젯: "알람 권한이 없으면 앱이 열리고 알람 권한 안내로 진입").
 * 콜백 안에서 액티비티를 띄우지 않는 이유는 그게 **백그라운드 실행**이라 Android 10+ 에서
 * 막히기 때문이다. 런처가 보내는 `PendingIntent` 로 여는 길만 확실하다.
 */
class PresetWidget : GlanceAppWidget() {

    /** 정본이 폭에 따라 글자 크기를 달리 잡아(3개=13/17 · 4개=12/16) 실제 크기를 받아야 한다. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = context.timerWidgetEntryPoint()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val ready = entry.canStartWithoutApp()

        // 프리셋은 앱에서 언제든 바뀐다(이름·시간 수정, 삭제). 값을 복사해 두지 않고 id 로
        // 매번 다시 찾아야 위젯이 지워진 프리셋을 계속 들고 있지 않는다.
        val presets = entry.presetRepository().presets

        provideContent {
            val slotId = currentState(PresetWidgetSlot.PRESET_ID)
            // 방금 시작했는가. 되돌리는 갱신을 놓쳐도 시각을 비교하므로 계속 켜져 있지 않는다.
            val startedAt = currentState(PresetWidgetSlot.STARTED_AT) ?: 0L
            val justStarted = System.currentTimeMillis() - startedAt < FEEDBACK_MS
            // Glance 컴포지션은 위젯이 살아 있는 동안 계속 돈다 — 앱에서 프리셋을 고치면
            // 위젯도 따라 바뀐다.
            //
            // ⚠️ 목록을 통째로 받아 여기서 고른다. `presets.map { ... }` 처럼 컴포지션 안에서
            // 흐름을 새로 만들면 다시 그릴 때마다 구독이 끊겼다 붙어 값이 초기값으로 돌아간다.
            val list by presets.collectAsState(initial = emptyList())
            val preset = list.firstOrNull { it.id == slotId }

            GlanceTheme {
                PresetButton(
                    preset = preset,
                    justStarted = justStarted,
                    action = tapAction(context, appWidgetId, preset, ready)
                )
            }
        }
    }
}

/**
 * 탭했을 때 무엇을 할지.
 *
 * - 슬롯이 비었으면 → 지정 화면. spec 은 "설정 방법을 안내"라고 했지만 안내로 끝내면
 *   위젯 편집을 다시 찾아 들어가야 한다 — 잠금화면에 놓은 위젯은 그 길이 멀다.
 * - 관문이 남았으면(권한·울림 방식) → 앱(타이머 탭). 관문이 뜨고, 다 지나면 그 프리셋이
 *   그대로 시작된다 — 위젯으로 되돌아갈 필요가 없다.
 * - 둘 다 지났으면 → 앱을 열지 않고 바로 시작.
 */
private fun tapAction(context: Context, appWidgetId: Int, preset: TimerPreset?, ready: Boolean): Action = when {
    preset == null -> actionStartActivity(PresetWidgetConfigActivity.intent(context, appWidgetId))
    !ready -> actionStartActivity(PresetWidgetLaunch.startInApp(context, preset.id))
    else -> actionRunCallback<StartPresetAction>(startPresetParameters(preset.id))
}

/** 위젯이 보여 주는 세 가지 상태. 분기를 한 곳에 모아 두면 색·아이콘이 서로 어긋나지 않는다. */
private enum class SlotState { EMPTY, STARTED, READY }

private val SlotState.icon: Int
    get() = when (this) {
        SlotState.EMPTY -> DsR.drawable.nm_ic_plus
        SlotState.STARTED -> DsR.drawable.nm_ic_check
        SlotState.READY -> DsR.drawable.nm_ic_timer
    }

/** 아이콘·값에 함께 쓰는 강조색. */
private val SlotState.accent: Color
    get() = when (this) {
        SlotState.EMPTY -> NmColor.Neutral.C400
        SlotState.STARTED -> NmColor.Success.C600
        SlotState.READY -> NmColor.Primary.C600
    }

private val SlotState.valueColor: Color
    get() = when (this) {
        SlotState.EMPTY -> NmColor.Neutral.C500
        SlotState.STARTED -> NmColor.Success.C600
        SlotState.READY -> NmColor.Neutral.C900
    }

@Composable
private fun PresetButton(preset: TimerPreset?, justStarted: Boolean, action: Action) {
    // 사용자가 셀 하나로 줄여 놓을 수 있다. 좁으면 정본이 4개 나열에서 쓴 한 단계 작은 값으로.
    val narrow = LocalSize.current.width < NARROW_WIDTH
    val state = when {
        preset == null -> SlotState.EMPTY
        justStarted -> SlotState.STARTED
        else -> SlotState.READY
    }
    val duration = when (state) {
        SlotState.EMPTY -> EMPTY_VALUE
        SlotState.STARTED -> STARTED_VALUE
        SlotState.READY -> formatDuration(checkNotNull(preset).durationSeconds)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(
                ImageProvider(
                    if (state == SlotState.EMPTY) R.drawable.nm_widget_card_empty else R.drawable.nm_widget_card
                )
            )
            // 정본 버튼의 padding: [11, 8].
            .padding(horizontal = 8.dp, vertical = 11.dp)
            .clickable(action),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = preset?.label ?: EMPTY_LABEL,
            style = TextStyle(
                color = ColorProvider(
                    if (state == SlotState.EMPTY) NmColor.Neutral.C400 else NmColor.Neutral.C600
                ),
                fontSize = if (narrow) 12.sp else 13.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )

        // 키워드는 위, 값은 아래 — 정본의 `justifyContent: space_between`.
        Spacer(GlanceModifier.defaultWeight())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(state.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(state.accent)),
                modifier = GlanceModifier.size(if (narrow) 12.dp else 13.dp)
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = duration,
                style = TextStyle(
                    color = ColorProvider(state.valueColor),
                    fontSize = durationSize(duration, narrow),
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * 값이 길면 글자를 줄인다.
 *
 * 정본은 `15분`·`30분` 만 그렸지만 프리셋은 초 단위까지 자유롭게 만들 수 있어
 * `2시간 30분 30초` 같은 값이 나온다. RemoteViews 에는 자동 축소가 없고 `maxLines=1` 은
 * 그냥 잘라 버려서, **무엇을 맞춰 놨는지 알 수 없게 된다** — 위젯의 존재 이유가 그거다.
 */
private fun durationSize(text: String, narrow: Boolean) = when {
    text.length > LONG_DURATION -> if (narrow) 12.sp else 13.sp
    text.length > MEDIUM_DURATION -> if (narrow) 14.sp else 15.sp
    else -> if (narrow) 16.sp else 17.sp
}

/** `2시간 30분` = 7자. 그보다 길면 한 단계 더 줄인다. */
private const val MEDIUM_DURATION = 4
private const val LONG_DURATION = 7

/** 정본이 4개(≈81pt)에서 한 단계 줄인 값을 dp 로 옮긴 것. */
private val NARROW_WIDTH = 96.dp

private const val STARTED_VALUE = "시작됨"
private const val EMPTY_LABEL = "프리셋 미지정"
private const val EMPTY_VALUE = "지정하기"
