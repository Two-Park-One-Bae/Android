package app.nursemate.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2CardShape
import app.nursemate.core.designsystem.v2.NmV2Color
import app.nursemate.core.designsystem.v2.NmV2ProgressRing
import app.nursemate.core.designsystem.v2.NmV2Surface
import app.nursemate.core.designsystem.v2.NmV2SurfaceStyle
import app.nursemate.core.designsystem.v2.NmV2Tag
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.designsystem.v2.NmV2Wordmark
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerState
import app.nursemate.core.model.Usage
import app.nursemate.core.model.formatDuration
import app.nursemate.core.model.formatRemaining
import app.nursemate.timer.v2Tone

/**
 * 홈 — 정본 `DESIGN.pen` 「홈 · V2.1.0 (NM-585)」 · `spec/feature/home/README.md`.
 *
 * 위에서 아래로 로고 · 기능 카드 두 장 · 진행 중인 타이머 · 최근 식별. 카드는 내용이 없어도 자리를
 * 지킨다 — 타이머가 생기거나 없어져도 아래 카드가 움직이지 않게(spec §구성).
 * 탭 바는 [app.nursemate.navigation.NmTabScaffold] 가 두른다.
 *
 * 홈에서는 타이머를 조작하지 않는다 — 완료 · 일시정지 · 정지는 타이머 탭에서.
 */
@Suppress("LongParameterList")
@Composable
fun HomeScreen(
    usage: Usage?,
    timers: List<CareTimer>,
    now: () -> Long,
    recent: HomeRecent,
    onTakePhoto: () -> Unit,
    onPillCard: () -> Unit,
    onNewTimer: () -> Unit,
    onTimerCard: () -> Unit,
    onRecent: (historyId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NmTheme.semanticColors.surface)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        NmV2Wordmark(Modifier.padding(top = 12.dp, bottom = 8.dp))
        // 두 장은 늘 같은 높이다 — 큰 글꼴에서 한쪽이 자라면 다른 쪽도 따라 자란다.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillTile(usage, onTakePhoto, onPillCard, Modifier.weight(1f))
            TimerTile(timers.size, onNewTimer, onTimerCard, Modifier.weight(1f))
        }
        TimersCard(timers, now, onTimerCard)
        RecentCard(recent, onRecent)
    }
}

// ── 기능 카드 ────────────────────────────────────────────────────────

/**
 * `알약 식별` 카드. 남은 횟수 · 초기화 시각 · `사진 찍기`.
 *
 * - 0회: 숫자와 초기화 줄을 경고색(`error-600`), `사진 찍기` 비활성 — 팝업은 띄우지 않는다
 * - 모름(조회 전 · 실패): 숫자 `–`, `사진 찍기` 활성 — 막을지는 서버가 판정한다(429)
 */
@Composable
private fun PillTile(usage: Usage?, onTakePhoto: () -> Unit, onCard: () -> Unit, modifier: Modifier) {
    val exhausted = usage?.exhausted == true
    FeatureTile(
        title = "알약 식별",
        icon = NmSymbols.Pill,
        iconTint = NmColor.Primary.C500,
        label = "오늘 남은 횟수",
        value = usage?.remaining?.toString() ?: "–",
        valueColor = if (exhausted) NmColor.Error.C600 else NmTheme.semanticColors.textPrimary,
        // 모르면 숫자만 `–` 로 두고 한도도 지어내지 않는다 — 정본 ⑥ 은 `/15` 를 그렸지만 그건 이 계정의
        // 한도일 뿐이다(개발 계정은 1,000,000). 없는 값을 보이면 한도를 잘못 알린다.
        unit = usage?.let { "/${it.limit}" },
        caption = if (exhausted) "내일 00:00 초기화" else "매일 00:00 초기화",
        captionColor = if (exhausted) NmColor.Error.C600 else NmTheme.semanticColors.textTertiary,
        buttonText = "사진 찍기",
        buttonIcon = NmSymbols.PhotoCamera,
        buttonEnabled = !exhausted,
        onButton = onTakePhoto,
        onCard = onCard,
        modifier = modifier
    )
}

/** `처치 타이머` 카드. 개수 · `새 타이머`. */
@Composable
private fun TimerTile(count: Int, onNewTimer: () -> Unit, onCard: () -> Unit, modifier: Modifier) {
    FeatureTile(
        title = "처치 타이머",
        icon = NmSymbols.Timer,
        iconTint = NmColor.Secondary.C500,
        label = "진행 중인 타이머",
        value = count.toString(),
        valueColor = NmTheme.semanticColors.textPrimary,
        unit = "개",
        caption = null,
        captionColor = Color.Unspecified,
        buttonText = "새 타이머",
        buttonIcon = NmSymbols.Add,
        buttonEnabled = true,
        onButton = onNewTimer,
        onCard = onCard,
        modifier = modifier
    )
}

@Suppress("LongParameterList")
@Composable
private fun FeatureTile(
    title: String,
    @DrawableRes icon: Int,
    iconTint: Color,
    label: String,
    value: String,
    valueColor: Color,
    unit: String?,
    caption: String?,
    captionColor: Color,
    buttonText: String,
    @DrawableRes buttonIcon: Int,
    buttonEnabled: Boolean,
    onButton: () -> Unit,
    onCard: () -> Unit,
    modifier: Modifier
) {
    val colors = NmTheme.semanticColors
    NmV2Surface(NmV2SurfaceStyle.Card, modifier.fillMaxHeight().heightIn(min = TileHeight)) {
        Column(
            Modifier
                .clip(NmV2CardShape)
                .clickable(role = Role.Button, onClick = onCard)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = NmV2Type.tileTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Icon(painterResource(icon), null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = NmV2Type.rowSubtitle, color = colors.textSecondary)
                MetricText(value, valueColor, unit)
                if (caption != null) Text(caption, style = NmV2Type.caption, color = captionColor)
            }
            TileButton(buttonText, buttonIcon, buttonEnabled, onButton)
        }
    }
}

/**
 * 숫자와 단위를 **한 줄 글자 하나**로 그린다(`12` + `/15`). 넘치면 글자를 줄여 맞춘다.
 *
 * ⚠️ 한도가 큰 계정(개발 계정 1,000,000회)에서 숫자가 카드 폭을 넘어 줄바꿈되고 초기화 줄이
 *    밀려 사라졌다(S24 실기기). 단위를 `em` 으로 숫자에 묶어 두어야 줄일 때 같이 줄어든다.
 *    정본의 단위 아래 여백 7 은 두 글자의 기준선을 맞춘 결과라 한 줄 글자의 기준선 정렬로 같다.
 */
@Composable
private fun MetricText(value: String, valueColor: Color, unit: String?) {
    val unitColor = NmTheme.semanticColors.textSecondary
    val text = remember(value, valueColor, unit, unitColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = valueColor)) { append(value) }
            if (unit != null) {
                withStyle(
                    SpanStyle(color = unitColor, fontSize = UNIT_EM.em, fontWeight = NmV2Type.metricUnit.fontWeight)
                ) { append("\u2009$unit") }
            }
        }
    }
    BasicText(
        text = text,
        style = NmV2Type.metric,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = METRIC_MIN_SP.sp, maxFontSize = NmV2Type.metric.fontSize)
    )
}

/**
 * 기능 카드 안 작은 볼록 버튼 — 여백 9 · 12, 글자 13/700 `primary-700`.
 * 비활성이면 평면 `neutral-100` · 글자 `neutral-400`(정본 ③ 한도 소진).
 */
@Composable
private fun TileButton(text: String, @DrawableRes icon: Int, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(NmRadius.md)
    val content = if (enabled) NmColor.Primary.C700 else NmColor.Neutral.C400
    val fill = if (enabled) {
        Brush.verticalGradient(listOf(NmTheme.semanticColors.surface, NmV2Color.Raised.neutralBottom))
    } else {
        Brush.verticalGradient(listOf(NmColor.Neutral.C100, NmColor.Neutral.C100))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(1.dp, NmColor.Neutral.C200, shape)
            // ⚠️ 비활성이어도 누름을 **삼킨다** — 안 그러면 카드의 클릭으로 새어 한도 팝업이 뜬다
            //    (spec home: 0회는 `사진 찍기` 비활성 · 팝업 없음).
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    // 자기 노드로 묶어야 화면 읽기 프로그램이 「비활성 버튼」으로 읽는다 — 안 묶으면 글자가
                    // 카드 노드에 합쳐져 카드의 일부로 읽히고 눌린다.
                    Modifier
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            disabled()
                        }
                        .pointerInput(Unit) { detectTapGestures {} }
                }
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(17.dp))
        Text(text, style = NmV2Type.smallButton, color = content)
    }
}

// ── 진행 중인 타이머 ─────────────────────────────────────────────────

/**
 * 최대 3줄. 4개 이상이면 3줄만 보이고 제목 오른쪽에 `전체 보기`(spec home §진행 중인 타이머 카드).
 * 타이머가 없으면 `진행 중인 타이머가 없어요` 이고 카드를 누르지 않는다.
 */
@Composable
private fun TimersCard(timers: List<CareTimer>, now: () -> Long, onClick: () -> Unit) {
    // 시각은 여기서만 읽는다 — 1초마다 이 카드만 다시 그린다(화면 전체가 아니라).
    val at = now()
    val ordered = homeTimerOrder(timers, at)
    val colors = NmTheme.semanticColors
    NmV2Surface(NmV2SurfaceStyle.Card, Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .clip(NmV2CardShape)
                .clickable(enabled = ordered.isNotEmpty(), role = Role.Button, onClick = onClick)
                .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 6.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "진행 중인 타이머",
                    style = NmV2Type.sectionTitle,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (ordered.size > MAX_ROWS) Text("전체 보기", style = NmV2Type.link, color = NmColor.Primary.C700)
            }
            if (ordered.isEmpty()) {
                Text(
                    "진행 중인 타이머가 없어요",
                    style = NmV2Type.empty,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
                )
            } else {
                ordered.take(MAX_ROWS).forEach { TimerRow(it, at) }
            }
        }
    }
}

@Composable
private fun TimerRow(timer: CareTimer, now: Long) {
    val colors = NmTheme.semanticColors
    val expired = timer.state == TimerState.RINGING || timer.isExpiredAt(now)
    val paused = timer.state == TimerState.PAUSED
    val accent = when {
        expired -> NmV2Color.Caution.C500
        paused -> NmColor.Neutral.C400
        else -> NmColor.Primary.C500
    }
    val clockColor = when {
        expired -> NmV2Color.Caution.C700
        paused -> colors.textTertiary
        else -> colors.textPrimary
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 만료는 원이 다 찬다(정본 ④ — 각도 없이 채운 원).
        NmV2ProgressRing(if (expired) 1f else timer.ringFractionAt(now), accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timer.label,
                    style = NmV2Type.rowTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                NmV2Tag(timer.category.label, tone = timer.category.v2Tone)
            }
            val duration = formatDuration(timer.durationSeconds)
            Text(
                if (paused) "$duration · 일시정지" else duration,
                style = NmV2Type.rowSubtitle,
                color = colors.textSecondary
            )
        }
        Text(formatRemaining(if (expired) 0 else timer.remainingAt(now)), style = NmV2Type.clock, color = clockColor)
    }
}

/** 단위 크기 — 정본 14 / 숫자 34. */
private const val UNIT_EM = 14f / 34f

/** 숫자를 줄일 수 있는 가장 작은 크기. */
private const val METRIC_MIN_SP = 16

/** 기능 카드 높이 — 정본 204. 두 장이 늘 같은 높이여야 한다. */
private val TileHeight = 204.dp

/** 진행 중인 타이머 카드에 보이는 줄 수. */
private const val MAX_ROWS = 3
