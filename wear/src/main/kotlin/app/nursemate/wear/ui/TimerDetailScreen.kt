package app.nursemate.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerState
import app.nursemate.core.model.formatDuration
import app.nursemate.core.model.formatRemaining
import app.nursemate.wear.R
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * W2 조작 — 정본 `타이머 워치 / W2 조작`.
 *
 * spec §워치 — "카드 탭으로 진입: [일시정지/재개] [정지(취소)]".
 * **[완료] 는 없다.** 완료는 만료 뒤에만 가능하고, 그건 W1 의 만료 카드에서 한다.
 *
 * ## 원형 기준으로 다시 짠 구조
 * 정본은 애플워치(사각 396×484)라 폭이 어디서나 같다. 그 배치를 그대로 옮기면 원형에서
 * 깨진다 — 좌우 여백만 맞췄다가 Play 「시계 모양」으로 **두 번** 거부당했다
 * (2026-09-21, 09-23). 그래서 원형에서 성립하는 구조로 바꿨다:
 *
 * ```
 *  ╭───────────────╮   ← 화면 가장자리를 도는 진행 링 (12시는 시스템 시계 자리로 비운다)
 *  │ ┌───────────┐ │   ← 링에 내접하는 정사각형 = 정보 영역
 *  │ │ 1 뒤로·처치명·분류 │
 *  │ │ 2 남은 시간·전체 시간 │
 *  │ │ 1   ◯   ◯      │  라벨 없는 둥근 아이콘 버튼
 *  │ └───────────┘ │
 *  ╰───────────────╯
 * ```
 *
 * 정보 영역의 정의는 [ringInscribedInset] 에, 1:2:1 분할은 [Band] 의 `weight` 에 있다.
 * 정본의 세로 버튼 배치는 원형에서 성립하지 않는다 — 정본 개정 요청 대상이다.
 */
@Composable
fun TimerDetailScreen(
    timer: CareTimer,
    now: Long,
    onBack: () -> Unit,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit
) {
    val paused = timer.state == TimerState.PAUSED
    val stroke = EdgeProgressStroke.scaled()

    // ⚠️ **진행 값은 반드시 `State` 를 거쳐 넘긴다 — 그냥 계산해 넣으면 링이 멈춘다.**
    //
    // [CircularProgressIndicator] 는 안에서 이렇게 관찰한다(1.6.2 바이트코드 확인):
    // ```
    // LaunchedEffect(Unit) { snapshotFlow(updatedProgress).collectLatest { animateTo(it) } }
    // ```
    // `LaunchedEffect(Unit)` 이라 **처음 넘어온 람다 인스턴스 하나가 평생 고정**되고,
    // `snapshotFlow` 는 그 람다 **안에서 읽은 스냅샷 State 가 바뀔 때만** 다시 평가한다.
    // `{ timer.ringFractionAt(now) }` 처럼 평범한 `Long` 을 캡처하면 읽는 State 가 없어
    // 최초 1회만 방출하고 끝난다 — 숫자는 흐르는데 링만 멈추는 이유가 이것이다.
    // (`TimerRing` 의 표가 기록한 증상과 같은 원인이다.)
    //
    // `rememberUpdatedState` 는 리컴포지션마다 State 에 새 값을 써 넣으므로, 람다가 그
    // State 를 읽는 순간 관찰이 성립한다.
    val ringFraction = rememberUpdatedState(timer.ringFractionAt(now))
    val inset = ringInscribedInset(stroke)

    // ⚠️ 스크롤하지 않는 화면이라 `scrollState` 를 주지 않는다 — 스크롤 인디케이터가 없어야 한다.
    ScreenScaffold {
        // ⚠️ **진행 표시는 화면 가장자리를 따라 돈다 — 가운데 링이 아니다.**
        //
        // 예전엔 가운데에 지름 100dp 링을 두고 그 **안에** 남은 시간을 넣었다. 그런데 글자는
        // 가로로 길고 원 안쪽은 가로가 좁다 — 형태가 서로 안 맞는다. 큰 글꼴 + 작은 워치에서
        // `14:48` 이 링 반지름의 **142%** 를 차지해 원호에 파고들었다(192dp · 글꼴 1.24 실측).
        // 글자를 더 줄이면 WO-V14(12sp)에, 안 줄이면 WO-V1(겹침 금지)에 걸려 빠져나갈 데가 없다.
        //
        // 링을 화면 끝으로 보내면 가운데가 통째로 비어, 글자가 정보 영역 전체를 쓴다.
        // 구글 기본 시계 앱(`com.google.android.deskclock`)의 스톱워치도 같은 구조다.
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(
                progress = { ringFraction.value },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CircularProgressIndicatorDefaults.FullScreenPadding),
                // ⚠️ **12 시 자리를 비운다.** 안 비우면 시스템 시계(`TimeText`)가 선 위에 얹혀
                // 그 구간의 진행이 가려진다. 문서가 이걸 위해 각도를 열어 두었다 —
                // 「시간 등의 중요한 정보를 위한 공간을 확보할 수 있도록 간격이 있는 진행
                // 상태 표시기를 만드세요. 간격을 만들려면 startAngle 및 endAngle 을 변경합니다」.
                startAngle = CircularProgressIndicatorDefaults.StartAngle + TIME_TEXT_GAP_DEGREES / 2f,
                endAngle = CircularProgressIndicatorDefaults.StartAngle - TIME_TEXT_GAP_DEGREES / 2f,
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = if (paused) WearTimerColors.Muted else WearTimerColors.Primary,
                    trackColor = WearTimerColors.Track
                ),
                strokeWidth = stroke
            )
            // 정보 영역을 1 : 2 : 1 로 나눈다. 각 칸은 자기 몫 안에서 가운데 정렬이라,
            // 글꼴 배율이 올라가 내용이 커져도 칸의 경계는 움직이지 않는다.
            //
            // ⚠️ **처치명과 분류를 각각 한 칸씩 주지 않는다.** 1.5 : 1 : 2 : 1 로 나눠 봤더니
            // 둘이 자기 칸에서 따로 가운데 정렬돼 사이가 14dp 벌어졌고(192dp 실측), 한 덩어리로
            // 안 읽혔다. 버튼 칸도 25% → 18% 로 줄어 원이 29.6dp → 20.7dp 가 됐다.
            // 둘은 한 칸 안에서 [DetailHeader] 가 직접 붙여 놓는다.
            Column(
                modifier = Modifier.fillMaxSize().padding(inset),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Band(weight = HEADER_WEIGHT) { DetailHeader(timer, onBack) }
                Band(weight = TIME_WEIGHT) { RemainingText(timer, now, paused) }
                Band(weight = ACTION_WEIGHT) { h -> ActionRow(paused, h, onPauseOrResume, onStop) }
            }
        }
    }
}

/**
 * 정보 영역 1:2:1 의 한 칸.
 *
 * 자기 높이를 [content] 에 넘겨준다 — 아래 칸의 둥근 버튼이 칸 높이에 맞춰 커져야 해서다.
 * 쓰지 않는 칸은 그냥 받지 않으면 된다.
 *
 * [align] 은 칸 **안에서** 내용을 어디에 둘지다. 기본은 가운데지만, 제목과 분류처럼
 * 한 덩어리로 읽혀야 하는 이웃은 서로 맞닿는 쪽에 붙인다.
 */
@Composable
private fun ColumnScope.Band(weight: Float, align: Alignment = Alignment.Center, content: @Composable (Dp) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.weight(weight).fillMaxWidth(),
        contentAlignment = align
    ) {
        content(maxHeight)
    }
}

/**
 * 링에 내접하는 정사각형이 화면 가장자리에서 들어가는 거리.
 *
 * ⚠️ **화면이 아니라 _링 안쪽_ 에 내접한다.** 플랫폼이 말하는 안전 영역은
 * `androidx.wear.widget.BoxInsetLayout` 의 상수다:
 * ```java
 * private static final float FACTOR = 0.146447f;   // (1 - sqrt(2)/2)/2
 * ```
 * 그건 **화면 원**에 내접하는 정사각형이라 링 두께만큼 모자라고, 그만큼 정보가 진행선에
 * 닿는다 — 실제로 버튼 배경이 진행선과 겹쳤다(192dp 에서 실측 여백 2.6dp).
 *
 * (목록은 이 값을 쓰지 않는다. 스크롤하는 화면은 `HORIZONTAL_PADDING_FRACTION` 쪽이다.)
 *
 * ```
 *   링 안쪽 반지름  r = 화면반지름 − (링 여백 + 링 두께)
 *   정사각형 반변      = r / √2
 *   들이는 거리        = 화면반지름 − r / √2
 * ```
 *
 * 192dp 화면이면 96 − (96 − 8)/√2 = 33.8dp 다. 화면 기준 14.6%(28.1dp)보다 5.7dp 더 들어간다.
 */
@Composable
private fun ringInscribedInset(stroke: Dp): Dp {
    val radius = LocalConfiguration.current.screenWidthDp / 2f
    val ringInner = radius - CircularProgressIndicatorDefaults.FullScreenPadding.value - stroke.value
    return ceil(radius - ringInner / sqrt(2f)).dp
}

/**
 * 하단 칸 — **라벨 없는 둥근 아이콘 버튼 둘**.
 *
 * ⚠️ **라벨을 뺐다.** 원형 화면에서 「일시정지」 4자를 두 버튼에 나눠 담으면 가장 좁은
 * 조건(192dp · 글꼴 1.24)에서 말줄임이 난다. 글자를 줄이면 WO-V14(핵심 텍스트 12sp)에
 * 걸리고, 폭을 넓히면 진행선과 겹친다. 아이콘만 두면 둘 다 피한다.
 *
 * 라벨이 사라진 만큼 아이콘의 `contentDescription` 이 **유일한 접근성 이름**이다 —
 * 비우면 토크백이 「버튼」으로만 읽는다.
 *
 * ⚠️ **아이콘은 채운 글리프(`*_solid`)를 쓴다.** 목록 카드가 쓰는 Lucide 획 아이콘은
 * 라벨이 받쳐 줄 때의 선택이라, 아이콘만 남기면 일시정지(잉크 6/24)와 정지(18/24)의
 * 무게가 안 맞는다. 정본 `DESIGN.pen` 은 라벨 있는 알약 버튼을 전제하므로 이 건은
 * **정본 개정 요청 대상**이다.
 */
@Composable
private fun ActionRow(paused: Boolean, bandHeight: Dp, onPauseOrResume: () -> Unit, onStop: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ACTION_ROW_GAP.scaled(), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundActionButton(
            icon = if (paused) R.drawable.nm_ic_play_solid else R.drawable.nm_ic_pause_solid,
            label = if (paused) "재개" else "일시정지",
            tint = WearTimerColors.OnBackground,
            diameter = bandHeight,
            onClick = onPauseOrResume
        )
        RoundActionButton(
            icon = R.drawable.nm_ic_stop_solid,
            label = "정지",
            tint = WearTimerColors.Danger,
            diameter = bandHeight,
            onClick = onStop
        )
    }
}

/**
 * 둥근 조작 버튼.
 *
 * ⚠️ **보이는 원과 누르는 영역을 나눈다.** 보이는 원은 칸 높이에 맞춘다(192dp 에서 31dp).
 * 그대로 두면 WO-V2 의 48dp 에 못 미치므로 누르는 `Box` 만 [MinTouchTarget] 으로 키운다.
 * `requiredSize` 여야 한다 — `size` 는 칸이 주는 제약에 눌려 칸 높이로 되돌아간다.
 * 넘치는 만큼은 빈 자리라 다른 것을 가리지 않는다.
 */
@Composable
private fun RoundActionButton(icon: Int, label: String, tint: Color, diameter: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .requiredSize(maxOf(diameter, MinTouchTarget))
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(WearTimerColors.Card),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(diameter * ACTION_ICON_RATIO)
            )
        }
    }
}

/**
 * 첫째 칸 — 처치명과 그 아래 분류 캡션. 뒤로 가기 꺾쇠가 왼쪽에 겹쳐 앉는다.
 *
 * ⚠️ **꺾쇠는 배치에서 뺀다.** 같은 흐름에 넣으면 그 폭만큼 제목이 오른쪽으로 밀려,
 * 아래 칸들만 가운데고 이 줄만 치우친 것처럼 보인다. `Box` 안에 겹쳐 둔다.
 * 대신 제목에 좌우로 같은 여백을 줘서 왼쪽은 꺾쇠를 피하고 가운데 정렬은 지킨다 —
 * 그 여백은 히트 영역(24dp)이 아니라 **꺾쇠 글리프의 오른쪽 끝**에 맞춘다.
 * 히트 영역까지 피하면 192dp 에서 이름 자리가 그만큼 더 줄어든다.
 *
 * ⚠️ **분류를 옆에 두지 않는다.** 나란히 두면 분류가 가로를 먼저 가져가, 192dp 에서
 * 이름 자리가 48dp 밖에 안 남아 정본 프리셋 중 가장 긴 「투약 반응 관찰」이 `투약 반…` 이
 * 됐다. 아래 칸([CategoryCaption])으로 내리면 이름이 칸 폭을 다 쓴다.
 *
 * 꺾쇠는 **누를 수 있어야 한다.** Wear 의 표준 뒤로 가기는 오른쪽 스와이프지만(WO-V3),
 * 그리기만 하고 안 눌리면 죽은 표시가 된다. 장갑 낀 손으로는 스와이프가 잘 안 먹기도 해서
 * 둘 다 둔다.
 */
@Composable
private fun DetailHeader(timer: CareTimer, onBack: () -> Unit) {
    val backHit = BACK_HIT.scaled()
    val titleInset = (backHit + BACK_ICON.scaled()) / 2 + CHEVRON_CLEARANCE.scaled()
    Box(modifier = Modifier.fillMaxSize()) {
        // 헤더 칸을 다시 6 : 4 로 나눈다. 제목은 자기 몫의 **아래**, 캡션은 자기 몫의
        // **위**에 붙여 둘이 맞닿게 한다 — 각자 가운데 정렬하면 사이가 벌어져 한 덩어리로
        // 안 읽힌다(1.5 : 1 : 2 : 1 로 칸을 쪼갰을 때 192dp 에서 14dp 벌어졌다).
        //
        // ⚠️ **6 : 4 는 자리만 잡는다 — 글자를 가두지 않는다.** `weight` 가 준 높이를
        // 그대로 글자에 물리면 글꼴 1.24 에서 잘린다. 192dp 기준 캡션 몫이 12.4dp 인데
        // 10sp × 1.24 는 14.5dp 라서다. 규정대로 맞추려면 캡션이 8.5sp 여야 하는데
        // WO-V14 의 보조 텍스트 하한이 10sp 다 — **가두면 규정을 못 지킨다.**
        // 그래서 `wrapContentHeight(unbounded = true)` 로 높이 제약을 풀어, 큰 글꼴에서는
        // 덩어리가 칸 밖으로 **넘치게** 둔다. 위아래 이웃(시스템 시계·남은 시간)에 여유가
        // 있어 넘쳐도 겹치지 않는다 — 넘치는 양은 실측으로 확인한다.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .padding(horizontal = titleInset),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.weight(TITLE_SHARE), contentAlignment = Alignment.BottomCenter) {
                Text(
                    modifier = Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true),
                    text = timer.label,
                    style = WearTimerType.DetailLabel.scaled().tightLines(),
                    color = WearTimerColors.OnBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(modifier = Modifier.weight(CAPTION_SHARE), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = timer.category.label,
                    // 자간을 살짝 벌린다. 캡션은 두 글자뿐이라 그냥 두면 제목 아래 붙은
                    // 덩어리로 뭉쳐 보이는데, 벌려 두면 제목에 **딸린 설명**으로 읽힌다.
                    style = WearTimerType.DetailCaption.scaled().tightLines()
                        .copy(letterSpacing = CAPTION_TRACKING),
                    color = categoryColor(timer.category),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .padding(top = TITLE_CAPTION_GAP.scaled())
                )
            }
        }
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(contentColor = WearTimerColors.Primary),
            modifier = Modifier.align(Alignment.CenterStart).size(backHit)
        ) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_chevron_left),
                contentDescription = "뒤로",
                tint = WearTimerColors.Primary,
                modifier = Modifier.size(BACK_ICON.scaled())
            )
        }
    }
}

/**
 * 줄 사이를 최소로 조인 글자.
 *
 * `includeFontPadding` 은 글꼴이 글자 위아래에 얹는 여백이다. 끄면 줄 상자가 글리프에
 * 딱 맞아, 두 줄을 붙여도 계산한 높이와 화면이 일치한다.
 *
 * ⚠️ **`lineHeight` 와 `LineHeightStyle.Trim` 은 쓰지 않는다.** 줄 높이를 글리프보다
 * 낮게 주면 Trim 이 남는 여백이 아니라 **글자 자체를 자른다** — 「투약」이 `ㅌ야` 로
 * 나왔다(192dp 실측, 1.05 와 1.2 둘 다). 높이는 칸을 나눠서 잡고 글자는 건드리지 않는다.
 */
private fun TextStyle.tightLines(): TextStyle = copy(platformStyle = PlatformTextStyle(includeFontPadding = false))

/** 가운데 칸 — 남은 시간과 전체 시간. 담을 도형이 없으니 화면이 크면 같이 키운다. */
@Composable
private fun RemainingText(timer: CareTimer, now: Long, paused: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatRemaining(timer.remainingAt(now)),
            style = WearTimerType.DetailRemaining.scaled(),
            color = WearTimerColors.OnBackground,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = if (paused) "일시정지" else formatDuration(timer.durationSeconds),
            style = WearTimerType.DetailTotal.scaled(),
            color = WearTimerColors.Muted,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** 가장자리 진행 표시의 두께. 화면을 두르는 선이라 가운데 링보다 가늘어도 읽힌다. */
private val EdgeProgressStroke = 6.dp

/**
 * 12 시 방향에 비워 두는 각도 — 시스템 시계(`TimeText`)가 앉는 자리다.
 *
 * 구글 기본 시계 앱의 스톱워치가 비우는 폭과 비슷하게 잡았다(에뮬레이터 실측).
 */
private const val TIME_TEXT_GAP_DEGREES = 40f

/** 두 조작 버튼 사이 간격. */
private val ACTION_ROW_GAP = 8.dp

/**
 * 둥근 버튼 안에서 아이콘이 차지하는 비율.
 *
 * Material 아이콘 버튼의 통례(지름의 절반)를 따른다. 채운 글리프라 더 키우면 원을 꽉 채워
 * 버튼처럼 안 보인다.
 */
private const val ACTION_ICON_RATIO = 0.5f

/** 정보 영역의 세로 분할 — 헤더 : 남은 시간 : 버튼. */
private const val HEADER_WEIGHT = 1f
private const val TIME_WEIGHT = 2f
private const val ACTION_WEIGHT = 1f

/**
 * 헤더 칸 안의 제목 : 캡션 비율. 글자를 가두는 상자가 아니라 **쉬는 자리**다.
 *
 * 기본 글꼴에서는 둘 다 자기 몫 안에 들어간다(192dp: 제목 18.6dp 에 16.4dp,
 * 캡션 12.4dp 에 11.7dp). 글꼴을 최대(1.24)로 올리면 각각 20.4 / 14.5dp 가 되어 넘치는데,
 * 줄이면 `WearTimerType.DetailCaption` 이 8.5sp 가 되어 WO-V14 하한(보조 10sp)을 깬다.
 * 그래서 줄이지 않고 넘치게 둔다.
 */
private const val TITLE_SHARE = 6f
private const val CAPTION_SHARE = 4f

/** 제목이 꺾쇠 글리프에서 떨어지는 거리. */
private val CHEVRON_CLEARANCE = 4.dp

/**
 * 제목과 분류 캡션 사이.
 *
 * 줄 높이를 이미 최소로 조여 둬서 이 값이 곧 둘 사이의 전부다 — 0 이면 한글 받침과
 * 다음 줄 윗획이 닿는다.
 */
private val TITLE_CAPTION_GAP = 2.dp

/** 분류 캡션의 자간. 두 글자짜리 캡션이 제목에 딸린 설명으로 읽히게 한다. */
private val CAPTION_TRACKING = 0.6.sp

/** 뒤로 가기 꺾쇠와 그 히트 영역. */
private val BACK_ICON = 12.dp
private val BACK_HIT = 24.dp

/** ⚠️ 접근성 하한(WO-V2). **비례로 줄이지 않는다.** */
private val MinTouchTarget = 48.dp
