package app.nursemate.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerState

/**
 * C1 타이머 카드 — 정본 `타이머 / C1 리스트`.
 *
 * ## 상세 화면이 없다
 * spec §생성 → 실행이 "앱 안의 조작은 C1 카드에서 끝낸다"로 못박았다. 그래서 일시정지·재개·
 * [+1분]·메모·정지가 전부 이 카드 안에 있고, 탭할 상세 화면을 만들지 않는다.
 */
@Composable
fun TimerCard(
    timer: CareTimer,
    now: Long,
    memoEditing: Boolean,
    onPauseOrResume: () -> Unit,
    onExtend: () -> Unit,
    onMemoToggle: () -> Unit,
    onMemoSave: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(CardElevation, CardShape, spotColor = ShadowColor, ambientColor = ShadowColor)
            .clip(CardShape)
            .background(colors.surface, CardShape)
            .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(CardGap)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TopRowGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressRing(timer, now)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 긴 제목이 태그를 밀어내지 않게 한 줄로 말줄임한다.
                    Text(
                        text = timer.label,
                        style = LabelStyle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    CategoryTag(timer.category)
                }
                Text(formatDuration(timer.durationSeconds), style = TotalStyle, color = colors.textTertiary)
            }

            Box(
                Modifier
                    .size(StopSize)
                    .clip(CircleShape)
                    .background(NmColor.Neutral.C100, CircleShape)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(DsR.drawable.nm_ic_close),
                    contentDescription = "정지",
                    tint = NmColor.Neutral.C500,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (memoEditing) {
            MemoField(initial = timer.memo.orEmpty(), onSave = onMemoSave)
        } else {
            timer.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                Text(memo, style = MemoStyle, color = colors.textSecondary)
            }
        }

        // [일시정지|재개] [+1분] [메모] — 3등분.
        //
        // ⚠️ **[+1분] 은 디자인 정본에 없다.** spec(NM-438 재작성본)이 카드 버튼으로 넣었는데
        // `타이머 / C1 리스트` 프레임의 Actions 에는 버튼이 둘뿐이다. iOS 가 먼저 3등분
        // (`.fillEqually`)으로 만들고 [+1분]만 아이콘 없이 뒀기에 그대로 맞췄다 — 같은 화면을
        // 두 플랫폼이 다르게 줄 수는 없어서다. 디자인 정본화 요청 대상이다.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ActionGap)) {
            val paused = timer.state == TimerState.PAUSED
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = painterResource(if (paused) DsR.drawable.nm_ic_play else DsR.drawable.nm_ic_pause),
                label = if (paused) "재개" else "일시정지",
                emphasized = paused,
                onClick = onPauseOrResume
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = null,
                label = "+1분",
                emphasized = false,
                onClick = onExtend
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = painterResource(DsR.drawable.nm_ic_sticky_note),
                label = "메모",
                emphasized = false,
                onClick = onMemoToggle
            )
        }
    }
}

/**
 * 만료 카드 — 정본은 `warning-50` 바탕에 `warning-300` 테두리다.
 *
 * 조작이 [완료] 하나뿐이다. 울리는 중에는 일시정지도 [+1분]도 없다(spec §상태).
 */
@Composable
fun ExpiredTimerCard(timer: CareTimer, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(NmColor.Warning.C50, CardShape)
            .border(ExpiredStroke, NmColor.Warning.C300, CardShape)
            .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(CardGap)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(DsR.drawable.nm_ic_bell_ring),
                    contentDescription = null,
                    tint = NmColor.Warning.C600,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = timer.label,
                    style = ExpiredLabelStyle,
                    color = NmColor.Warning.C900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // 만료 카드의 태그만 배경이 흰색이다 — 경고색 바탕에서 계열색 배경이 묻힌다.
                CategoryTag(timer.category, background = NmColor.Neutral.C0)
            }
            Text("00:00", style = ExpiredLabelStyle, color = NmColor.Warning.C600)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CompleteShape)
                .background(NmColor.Warning.C600, CompleteShape)
                .clickable(onClick = onComplete)
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_check),
                contentDescription = null,
                tint = NmColor.Neutral.C0,
                modifier = Modifier.size(16.dp)
            )
            Text("완료", style = CompleteStyle, color = NmColor.Neutral.C0, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * 진행률 링 64x64 — 남은 비율만큼 12시부터 시계방향으로 채운다.
 *
 * 정본의 `innerRadius: 0.86` 은 도넛 두께가 반지름의 14%(=4.5dp)라는 뜻이다.
 * 일시정지는 진행 색을 `neutral-300` 으로 죽여 멈춘 걸 색으로 알린다.
 */
@Composable
private fun ProgressRing(timer: CareTimer, now: Long) {
    val paused = timer.state == TimerState.PAUSED
    val remaining = timer.remainingAt(now)
    Box(Modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(RingSize)) {
            val stroke = Stroke(width = RingStroke.toPx())
            val inset = RingStroke.toPx() / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = NmColor.Neutral.C100,
                startAngle = 0f,
                sweepAngle = FULL_TURN,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = if (paused) NmColor.Neutral.C300 else NmColor.Primary.C500,
                startAngle = TOP_ANGLE,
                sweepAngle = FULL_TURN * (1f - timer.progressAt(now)),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
        }
        Text(
            text = formatRemaining(remaining),
            // 한 시간을 넘으면 `1:12:40` 이라 13sp 로는 64dp 안에 안 들어간다(정본도 11로 줄인다).
            style = if (remaining >= HOUR_SECONDS) RingTextSmall else RingText,
            color = if (paused) NmTheme.semanticColors.textSecondary else NmTheme.semanticColors.textPrimary,
            textAlign = TextAlign.Center,
            // `20:30:30` 처럼 두 자리 시가 되면 11sp 로도 넘친다. 줄바꿈 대신 스스로 줄인다 —
            // 링 안에서 두 줄이 되면 가운데가 어긋나 읽기 더 어렵다.
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 13.sp),
            modifier = Modifier.padding(horizontal = RingStroke)
        )
    }
}

/** 메모 인라인 입력 — 정본 `타이머 / C1 — 메모 인라인 입력`. 카드를 떠나지 않는다. */
@Composable
private fun MemoField(initial: String, onSave: (String) -> Unit) {
    val colors = NmTheme.semanticColors
    var value by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ActionShape)
            .background(NmColor.Neutral.C50, ActionShape)
            .border(MemoStroke, NmColor.Primary.C500, ActionShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            textStyle = MemoInputStyle.copy(color = colors.textPrimary),
            singleLine = true,
            cursorBrush = SolidColor(NmColor.Primary.C500),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(value) })
        )
        Box(
            Modifier
                .size(MemoSaveSize)
                .clip(CircleShape)
                .background(NmColor.Primary.C500, CircleShape)
                .clickable { onSave(value) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_check),
                contentDescription = "메모 저장",
                tint = NmColor.Neutral.C0,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CategoryTag(category: TimerCategory, background: Color = tagBackground(category)) {
    Text(
        text = category.label,
        style = TagStyle,
        color = tagForeground(category),
        modifier = Modifier
            .clip(TagShape)
            .background(background, TagShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** 카드 하단 액션 버튼. [emphasized] 는 재개(primary 계열)를 뜻한다. */
@Composable
private fun ActionButton(
    icon: Painter?,
    label: String,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    val background = if (emphasized) NmColor.Primary.C50 else NmColor.Neutral.C50
    val stroke = if (emphasized) NmColor.Primary.C300 else colors.border
    val content = if (emphasized) NmColor.Primary.C700 else colors.textPrimary

    Row(
        modifier = modifier
            .clip(ActionShape)
            .background(background, ActionShape)
            .border(1.dp, stroke, ActionShape)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (emphasized) NmColor.Primary.C700 else colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            style = ActionStyle,
            color = content,
            modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp)
        )
    }
}

// 정본 수치 — `타이머 / C1 리스트`
private val CardShape = RoundedCornerShape(20.dp)
private val ActionShape = RoundedCornerShape(10.dp)
private val CompleteShape = RoundedCornerShape(12.dp)
private val TagShape = RoundedCornerShape(6.dp)
private val CardPadding = 16.dp
private val CardGap = 12.dp
private val TopRowGap = 14.dp
private val ActionGap = 8.dp
private val CardElevation = 3.dp
private val ExpiredStroke = 1.5.dp
private val MemoStroke = 1.5.dp
private val MemoSaveSize = 28.dp
private val RingSize = 64.dp

/** 정본 `innerRadius: 0.86` → 도넛 두께 = 32dp x 0.14. */
private val RingStroke = 4.5.dp
private val StopSize = 30.dp
private val ShadowColor = Color(0x1E1E293B)
private const val HOUR_SECONDS = 3600
private const val FULL_TURN = 360f
private const val TOP_ANGLE = -90f

private val LabelStyle = NmTypography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
private val ExpiredLabelStyle = NmTypography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
private val TotalStyle = NmTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)
private val MemoStyle = NmTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)
private val MemoInputStyle = NmTypography.body.copy(fontSize = 14.sp)
private val TagStyle = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
private val ActionStyle = NmTypography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
private val CompleteStyle = NmTypography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val RingText = NmTypography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val RingTextSmall = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
