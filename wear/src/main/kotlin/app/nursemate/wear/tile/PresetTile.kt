package app.nursemate.wear.tile

import android.graphics.Color
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontSetting
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.formatDuration

/**
 * 프리셋 시작 타일 — 정본 「타이머 워치 / 위젯 — Smart Stack 시작 카드」.
 *
 * ## 왜 Compose 가 아닌가
 * 타일은 우리 프로세스가 그리는 게 아니라 **시스템 런처가 그린다**. 그래서 Compose 가 아니라
 * ProtoLayout 으로 레이아웃을 기술해 넘긴다(위젯의 RemoteViews 와 같은 사정).
 *
 * ## 정본과 갈리는 것
 * 정본은 396×484px(=198×242dp) **사각** 프레임에 2×2 를 그렸는데 우리 기기는 203dp **원형**이라
 * 네 모서리가 곡면 밖으로 나간다. [primaryLayout] 이 기기 모양에 맞춰 여백을 잡아 주므로 그 안에
 * 2행을 넣고, 셀 높이는 정본 65dp 대신 남는 높이를 반씩 나눠 쓴다.
 */
internal fun MaterialScope.presetTileLayout(
    presets: List<TimerPreset>,
    justStarted: String?,
    onStart: (String) -> Clickable,
    onMore: () -> Clickable
): LayoutElement = primaryLayout(
    mainSlot = {
        if (presets.isEmpty()) {
            text("폰에서 프리셋을 만드세요".layoutString, typography = Typography.BODY_MEDIUM, maxLines = 2)
        } else {
            // 칸이 넷뿐이라 프리셋이 더 많으면 셋만 보이고 마지막은 [더 보기] 가 된다 —
            // 안 그러면 뒤쪽 프리셋을 워치에서 아예 시작할 수 없다.
            val overflow = presets.size > MAX_CELLS
            val shown = presets.take(if (overflow) MAX_CELLS - 1 else MAX_CELLS)
            val cells = buildList<MaterialScope.() -> LayoutElement> {
                shown.forEach { preset ->
                    add { presetButton(preset, onStart(preset.id), justStarted == preset.id) }
                }
                if (overflow) add { moreButton(onMore()) }
            }
            Column.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .addContent(cellRow(cells.take(CELLS_PER_ROW)))
                .apply {
                    if (cells.size > CELLS_PER_ROW) {
                        addContent(Spacer.Builder().setHeight(dp(ROW_GAP_DP)).build())
                        addContent(cellRow(cells.drop(CELLS_PER_ROW)))
                    }
                }
                .build()
        }
    }
)

private fun MaterialScope.cellRow(cells: List<MaterialScope.() -> LayoutElement>): LayoutElement =
    buttonGroup(spacing = CELL_GAP_DP) {
        cells.forEach { cell -> buttonGroupItem(cell) }
    }

/** 넘치는 프리셋을 보러 가는 칸. 앱의 프리셋 페이지를 곧장 연다. */
private fun MaterialScope.moreButton(click: Clickable): LayoutElement = button(
    onClick = click,
    labelContent = {
        text(
            "더 보기".layoutString,
            typography = Typography.LABEL_SMALL,
            color = KEYWORD.argb,
            maxLines = 1,
            settings = listOf(FontSetting.weight(KEYWORD_WEIGHT))
        )
    },
    width = expand(),
    height = expand(),
    shape = Corner.Builder().setRadius(dp(CELL_RADIUS_DP)).build(),
    colors = ButtonColors(containerColor = CONTAINER.argb, labelColor = KEYWORD.argb)
)

/**
 * 셀 하나 — 정본은 키워드가 위, 그 아래에 `아이콘 + 시간` 이다.
 *
 * `iconContent` 를 쓰면 아이콘이 **왼쪽**에 붙어 정본과 달라진다. 그래서 아이콘을 시간과 묶어
 * `secondaryLabelContent` 에 넣는다.
 */
private fun MaterialScope.presetButton(preset: TimerPreset, click: Clickable, justStarted: Boolean): LayoutElement =
    button(
        onClick = click,
        labelContent = {
            text(
                preset.label.layoutString,
                typography = Typography.LABEL_SMALL,
                color = KEYWORD.argb,
                maxLines = 1,
                settings = listOf(FontSetting.weight(KEYWORD_WEIGHT))
            )
        },
        secondaryLabelContent = {
            Row.Builder()
                .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                .apply {
                    // ⚠️ 「시작됨」일 때는 **시계 아이콘을 뺀다.** 셀 폭이 73dp 뿐이라 아이콘까지
                    // 두면 「시…」로 잘린다(실기기 확인). 시간이 사라진 자리라 아이콘도 의미가 없다.
                    if (!justStarted) {
                        addContent(
                            icon(ICON_TIMER, width = dp(ICON_DP), height = dp(ICON_DP), tintColor = ICON.argb)
                        )
                        addContent(Spacer.Builder().setWidth(dp(ICON_GAP_DP)).build())
                    }
                }
                .addContent(
                    text(
                        (if (justStarted) STARTED_LABEL else formatDuration(preset.durationSeconds)).layoutString,
                        typography = Typography.LABEL_SMALL,
                        color = (if (justStarted) STARTED else Color.WHITE).argb,
                        maxLines = 1,
                        settings = listOf(FontSetting.weight(DURATION_WEIGHT))
                    )
                )
                .build()
        },
        width = expand(),
        height = expand(),
        // material3 기본은 `shapes.full` 이라 정사각형 셀이 **원**이 된다. 정본은 라운드 사각형이다.
        shape = Corner.Builder().setRadius(dp(CELL_RADIUS_DP)).build(),
        colors = ButtonColors(
            containerColor = CONTAINER.argb,
            labelColor = KEYWORD.argb,
            secondaryLabelColor = Color.WHITE.argb
        )
    )

internal const val ICON_TIMER = "timer"

/** 정본 `#FFFFFF14` — 검은 배경 위 흰색 8%. */
private const val CONTAINER = 0x14FFFFFF

/** 정본 `#FFFFFFCC` — 키워드는 80%. */
private const val KEYWORD = 0xCCFFFFFF.toInt()

/** 정본 `#FFFFFFB3` — 아이콘은 70%. */
private const val ICON = 0xB3FFFFFF.toInt()

/** 정본 `cornerRadius 36px` (÷2). */
private const val CELL_RADIUS_DP = 18f

/** 정본 키워드 600 · 시간 700. 토큰 기본은 둘 다 500 이라 따로 준다. */
private const val KEYWORD_WEIGHT = 600
private const val DURATION_WEIGHT = 700

/** 눌렀다는 표시. 타일은 눌러도 화면이 그대로라, 없으면 또 누르게 된다. */
private const val STARTED_LABEL = "시작됨"

/** 정본 `primary-300`. */
private const val STARTED = 0xFF7DD3FC.toInt()

private const val MAX_CELLS = 4
private const val CELLS_PER_ROW = 2
private const val ICON_DP = 11f
private const val ICON_GAP_DP = 4f
private const val CELL_GAP_DP = 4f
private const val ROW_GAP_DP = 4f
