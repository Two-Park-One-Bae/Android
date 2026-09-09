package app.nursemate.timer.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.nursemate.timer.formatDuration

/**
 * 지정 프리셋 위젯 — 정본 `타이머 / 위젯 — 잠금화면 시안 비교`.
 *
 * 홈 화면과 잠금화면 **양쪽**에 놓인다. 정본이 잠금화면만 그린 것은 iOS 가 두 표면을 따로
 * 만들어야 해서고, Android 는 `widgetCategory` 비트 하나로 같은 위젯이 둘 다 올라간다 —
 * 홈을 빼면 오히려 일이 는다(spec 본문은 "홈 화면 위젯 = MVP 제외", 개정 요청 대상).
 *
 * ## 색은 정본을 그대로 옮기지 않았다
 * 정본의 반투명 흰색은 iOS 잠금화면이 위젯을 **모노크롬으로 강제 렌더링**하는 걸 전제한
 * 값이다. Android 는 앱이 준 색을 그대로 그리므로 벽지 위에서도 읽히도록 흰 카드에 앱 색을
 * 얹었다. 배치(키워드 위 · 아이콘+시간 아래)와 굵기 대비는 정본 그대로다.
 *
 * ## 권한 판정을 그릴 때 한다
 * 탭 동작 자체를 갈아 끼운다 — 권한이 있으면 콜백으로 바로 시작하고, 없으면 앱을 연다
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
        val granted = entry.permissions().allGranted()

        // 프리셋은 앱에서 언제든 바뀐다(이름·시간 수정, 삭제). 값을 복사해 두지 않고 id 로
        // 매번 다시 찾아야 위젯이 지워진 프리셋을 계속 들고 있지 않는다.
        val presets = entry.presetRepository().presets

        provideContent {
            val slotId = currentState(PresetWidgetSlot.PRESET_ID)
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
                    action = tapAction(context, appWidgetId, preset, granted)
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
 * - 권한이 없으면 → 앱(타이머 탭). 관문이 뜨고, 권한을 받으면 그 프리셋이 그대로 시작된다.
 * - 둘 다 지났으면 → 앱을 열지 않고 바로 시작.
 */
private fun tapAction(context: Context, appWidgetId: Int, preset: TimerPreset?, granted: Boolean): Action = when {
    preset == null -> actionStartActivity(PresetWidgetConfigActivity.intent(context, appWidgetId))
    !granted -> actionStartActivity(PresetWidgetLaunch.startInApp(context, preset.id))
    else -> actionRunCallback<StartPresetAction>(startPresetParameters(preset.id))
}

@Composable
private fun PresetButton(preset: TimerPreset?, action: Action) {
    // 잠금화면 위젯 영역은 폭이 좁다(정본 기준 버튼당 81~110pt). 좁으면 한 단계 줄인다.
    val narrow = LocalSize.current.width < NARROW_WIDTH
    val empty = preset == null

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(if (empty) R.drawable.nm_widget_card_empty else R.drawable.nm_widget_card))
            .padding(horizontal = 10.dp, vertical = 11.dp)
            .clickable(action),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = preset?.label ?: EMPTY_LABEL,
            style = TextStyle(
                color = ColorProvider(if (empty) NmColor.Neutral.C400 else NmColor.Neutral.C600),
                fontSize = if (narrow) 12.sp else 13.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )

        // 키워드는 위, 값은 아래 — 정본의 `justifyContent: space_between`.
        Spacer(GlanceModifier.defaultWeight())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(if (empty) DsR.drawable.nm_ic_plus else DsR.drawable.nm_ic_timer),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    ColorProvider(if (empty) NmColor.Neutral.C400 else NmColor.Primary.C600)
                ),
                modifier = GlanceModifier.size(if (narrow) 12.dp else 13.dp)
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = preset?.let { formatDuration(it.durationSeconds) } ?: EMPTY_VALUE,
                style = TextStyle(
                    color = ColorProvider(if (empty) NmColor.Neutral.C500 else NmColor.Neutral.C900),
                    fontSize = if (narrow) 16.sp else 17.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

/** 정본이 4개(≈81pt)에서 한 단계 줄인 값을 dp 로 옮긴 것. */
private val NARROW_WIDTH = 96.dp

private const val EMPTY_LABEL = "프리셋 미지정"
private const val EMPTY_VALUE = "지정하기"
