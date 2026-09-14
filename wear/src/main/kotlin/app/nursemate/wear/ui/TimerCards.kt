package app.nursemate.wear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerState
import app.nursemate.core.model.formatDuration
import app.nursemate.core.model.formatOverdue
import app.nursemate.core.model.formatRemaining
import app.nursemate.wear.R

// 안쪽 여백·모서리는 정본 값(애플워치 2x)을 dp 로 옮긴 것이다.
// ⚠️ Wear Material3 의 기본 여백은 본문이 여러 줄인 카드를 전제해서, 한 줄짜리 목록 행에
// 그대로 쓰면 과하다 — 203dp 화면에서 두 장이면 벌써 꽉 찬다.

/** 만료 카드 안쪽 여백 — 정본 `padding: [16, 18]`. */
private val ExpiredPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp)

/** 진행 중 행 안쪽 여백 — 정본 `padding: [14, 16]`. */
private val RunningPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)

/** 모서리 — 정본 만료 32 · 진행 34 · 프리셋 30(애플워치 2x). */
private val ExpiredShape = RoundedCornerShape(16.dp)
private val RunningShape = RoundedCornerShape(17.dp)
private val PresetShape = RoundedCornerShape(15.dp)

/** 프리셋 행 안쪽 여백 — 정본 `padding: [16, 18]`. */
private val PresetPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp)

/** 진행 링 지름 — 정본 `Ring 60`(애플워치 2x) 을 dp 로 옮긴 값. */
private val RingSize = 30.dp

/** [완료] 표시 모서리 — 정본 `cornerRadius: 26`. */
private val CompleteShape = RoundedCornerShape(13.dp)

/** 분류 태그 색 — 정본 W1 의 분류 텍스트. 워치는 배경 없이 색으로만 구분한다. */
internal fun categoryColor(category: TimerCategory): Color = when (category) {
    TimerCategory.TEST -> WearTimerColors.CategoryTest
    TimerCategory.TREATMENT -> WearTimerColors.CategoryTreatment
    TimerCategory.MEDICATION -> WearTimerColors.CategoryMedication
}

/**
 * 만료 카드 — 정본 `W1 활성 타이머` 맨 위 경고색 카드.
 *
 * 끄는 길은 [완료] 하나다(폰 C1 과 같은 규칙). 일시정지·정지는 만료 전에만 의미가 있다.
 */
@Composable
internal fun ExpiredTimerCard(timer: CareTimer, now: Long, onComplete: () -> Unit) {
    Card(
        onClick = onComplete,
        colors = CardDefaults.cardColors(containerColor = WearTimerColors.ExpiredSurface),
        // 정본 `stroke: $warning-600, 1.5`. 만료 카드는 다른 카드와 확실히 갈라져 보여야 한다.
        border = BorderStroke(1.dp, WearTimerColors.WarningStrong),
        shape = ExpiredShape,
        contentPadding = ExpiredPadding,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_bell_ring),
                    contentDescription = null,
                    tint = WearTimerColors.Warning,
                    modifier = Modifier.size(14.dp)
                )
                TimerLabel(
                    label = timer.label,
                    category = timer.category,
                    style = WearTimerType.ExpiredLabel,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 5.dp)
                )
            }
            // 정본 자리는 `00:00` 고정이지만 폰과 같은 값(경과 시간)을 쓴다 — 같은 타이머가
            // 표면마다 다르게 보이면 안 된다(NM-441 에서 폰을 그렇게 정했다).
            Text(
                text = formatOverdue(timer.overdueAt(now)),
                style = WearTimerType.Overdue,
                color = WearTimerColors.Warning,
                maxLines = 1
            )
        }
        // ⚠️ **[완료] 를 버튼으로 만들지 않는다.** Wear 버튼은 터치 타깃 48dp 를 보장하려고
        // 투명 여백을 얹어서, 정본(22dp)의 세 배가 되고 카드까지 부푼다 — 실측 78dp,
        // 정본 환산 49dp 였다. 카드 전체가 이미 [완료] 동작이라 안쪽은 눌릴 필요가 없다.
        // 눌리는 것을 하나로 두면 접근성 트리도 깔끔해진다.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .clip(CompleteShape)
                .background(WearTimerColors.WarningStrong)
                .padding(vertical = 5.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_check),
                contentDescription = null,
                tint = WearTimerColors.OnWarning,
                modifier = Modifier.size(10.dp)
            )
            Text(text = "완료", style = WearTimerType.Action, color = WearTimerColors.OnWarning)
        }
    }
}

/**
 * 진행 중·일시정지 행 — 정본 `W1 활성 타이머`.
 *
 * 누르면 W2(조작)로 간다(spec §워치 "카드 탭으로 진입"). 정본의 오른쪽 꺾쇠가 그 표시다.
 */
@Composable
internal fun RunningTimerCard(timer: CareTimer, now: Long, onOpen: () -> Unit) {
    val paused = timer.state == TimerState.PAUSED
    val accent = if (paused) WearTimerColors.Muted else WearTimerColors.Primary

    Button(
        onClick = onOpen,
        colors = ButtonDefaults.buttonColors(containerColor = WearTimerColors.Card),
        shape = RunningShape,
        contentPadding = RunningPadding,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RingSize)) {
            // 두께를 직접 준다 — 목록 안 30dp 링이라 기본값(큰 링 전제)을 쓰면 두께가
            // 반지름에 가까워져 호가 점으로 뭉개진다(실기기 확인).
            TimerRing(
                fraction = timer.ringFractionAt(now),
                color = accent,
                trackColor = WearTimerColors.Track,
                strokeWidth = 3.dp,
                modifier = Modifier.size(RingSize)
            )
            if (paused) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_pause),
                    contentDescription = null,
                    tint = WearTimerColors.Muted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            TimerLabel(label = timer.label, category = timer.category, style = WearTimerType.TimerLabel)
            Text(
                text = if (paused) {
                    "${formatRemaining(timer.remainingAt(now))} · 일시정지"
                } else {
                    formatRemaining(timer.remainingAt(now))
                },
                style = WearTimerType.Remaining,
                color = if (paused) WearTimerColors.Muted else WearTimerColors.PrimarySoft,
                maxLines = 1
            )
        }
        Icon(
            painter = painterResource(R.drawable.nm_ic_chevron_right),
            contentDescription = null,
            tint = WearTimerColors.Muted,
            modifier = Modifier.size(11.dp)
        )
    }
}

/**
 * 프리셋 행 — 정본 `W1 프리셋 페이지`. 누르면 바로 시작된다.
 *
 * ⚠️ **`Card` 가 아니라 `Button` 이다.** Wear Material3 에서 목록 행은 `Button` 이고
 * `Card` 는 여러 줄짜리 내용 덩어리를 담는 것이다. `Card` 로 만들었더니 한 줄인데도 59dp 가
 * 돼 203dp 화면에 두 장이면 꽉 찼다 — 안쪽 여백을 줄여도 소용없었다(실기기에서 확인).
 * `Button` 은 기본 높이가 52dp 로, 터치 타깃 최소 48dp(WO-V2)를 지키는 선에서 가장 낮다.
 */
@Composable
internal fun PresetCard(preset: TimerPreset, onStart: () -> Unit) {
    // ⚠️ **`CompactButton` 이 아니라 `Button` 이다 — 되돌리지 말 것.**
    // `CompactButton` 은 보이는 높이가 32dp 로 정본(31.9dp)과 딱 맞지만, 터치 타깃 48dp
    // (WO-V2)를 채우려고 위아래 8dp 씩 투명 여백을 붙인다 — 간격을 0 으로 둬도 행 사이가
    // **16dp** 로 벌어진다(실측). 정본은 6.2dp 다.
    // `Button` 은 보이는 높이 자체가 52dp 라 그 여백이 필요 없고, 간격을 정본대로 6dp 로
    // 좁힐 수 있다(실측 6.1dp).
    //
    // 높이 52dp 는 못 낮춘다 — `height`·`requiredHeight` 둘 다 안 먹는다(컴포넌트가 자기
    // 최소를 바깥에서 다시 건다). 그래서 낮추는 대신 **안을 채웠다**: 아이콘과 글자를
    // 한 단계씩 키워 행 높이와 균형을 맞췄다.
    Button(
        onClick = onStart,
        icon = {
            Icon(
                painter = painterResource(R.drawable.nm_ic_circle_play),
                contentDescription = null,
                tint = WearTimerColors.Primary,
                // 정본 30px(=15dp). 행이 52dp 라 그대로 두면 비어 보여 20dp 로 키웠다.
                modifier = Modifier.size(20.dp)
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = WearTimerColors.Card),
        shape = PresetShape,
        // 정본 `padding: [16, 18]`. 내용은 Button 이 세로 가운데로 잡아 준다.
        contentPadding = PresetPadding,
        modifier = Modifier.fillMaxWidth()
    ) {
        // ⚠️ **가중치를 가진 자식은 하나여야 하고, 그 하나가 남는 폭을 다 먹어야 한다.**
        // 라벨에 `fill = false` 를 주고 뒤에 weight Spacer 를 또 두면 남는 폭이 반씩 나뉘어
        // **시간이 카드 한가운데 붙는다** — 실측 우여백 44.7dp(정본 9.2dp)였다.
        // 폰 위젯 지정 화면에서 똑같이 겪고도 반복했다.
        Text(
            text = preset.label,
            style = WearTimerType.PresetLabel,
            color = WearTimerColors.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 6.dp)
        )
        Text(
            text = formatDuration(preset.durationSeconds),
            style = WearTimerType.Duration,
            color = WearTimerColors.Muted,
            maxLines = 1
        )
    }
}

/**
 * 처치 키워드 + 분류 태그.
 *
 * ⚠️ **여기까지만 보여 준다.** 메모는 워치에 노출하지 않는다(spec §워치 프라이버시 공통 규칙).
 */
@Composable
private fun TimerLabel(label: String, category: TimerCategory, style: TextStyle, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = style,
            color = WearTimerColors.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = category.label,
            style = WearTimerType.Category,
            color = categoryColor(category),
            maxLines = 1
        )
    }
}
