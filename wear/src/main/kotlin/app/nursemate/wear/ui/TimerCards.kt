package app.nursemate.wear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SurfaceTransformation
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

/** 목록 행 안쪽 여백 — 정본 `padding: [14, 16]`. 프리셋·활성이 같은 값을 쓴다. */
private val RowPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)

/** 모서리 — 정본 목록 행 34(애플워치 2x). 만료 행도 같은 값을 쓴다. */
private val RowShape = RoundedCornerShape(17.dp)

/** 목록 행 왼쪽 원형 자리의 지름 — 정본 `Ring 60`(애플워치 2x). */
private val LeadingSize = 30.dp

/** 그 원과 글자 사이. */
private val LeadingGap = 8.dp

/** 목록 행 링 두께. 작은 링이라 기본값을 쓰면 호가 점으로 뭉개진다. */
private val RingStroke = 3.dp

/** 원 안에 겹치는 글리프(일시정지)가 차지하는 비율. */
private const val LEADING_GLYPH_RATIO = 0.42f

/**
 * 만료 행의 [완료] 표시가 왼쪽 원보다 작은 비율.
 *
 * 왼쪽 원은 상태(울림·진행·프리셋)를 말하는 주 요소고, 이쪽은 「누르면 끝난다」는 딸린
 * 표시다. 같은 크기로 두면 둘째 줄 글자 자리를 그만큼 뺏겨 경과 시간이 말줄임된다.
 */
private const val COMPLETE_MARK_RATIO = 0.78f

/** 그 작은 원 안의 체크 글리프 비율. 원이 작아진 만큼 글리프는 상대적으로 키운다. */
private const val COMPLETE_GLYPH_RATIO = 0.52f

/**
 * 글자 덩어리가 행의 오른쪽 안쪽 끝에서 **더** 들어가는 거리.
 *
 * ⚠️ 곡면이 끝 항목의 귀퉁이를 잘라서, 행 안쪽 끝까지 글자를 채우면 거기서 잘린다.
 * 192dp 기준 끝 항목의 글자 줄에서 안전한 폭이 중심 ±64.6dp 인데, 이 값이 없으면
 * 글자가 ±77dp 까지 간다. [TimerRow] 주석 참고 — 값을 바꾸면 다시 측정한다.
 */
private val TextTrailingInset = 14.dp

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
internal fun ExpiredTimerCard(
    timer: CareTimer,
    now: Long,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    TimerRow(
        title = timer.label,
        // 정본 자리는 `00:00` 고정이지만 폰과 같은 값(경과 시간)을 쓴다 — 같은 타이머가
        // 표면마다 다르게 보이면 안 된다(NM-441).
        value = formatOverdue(timer.overdueAt(now)),
        valueColor = WearTimerColors.Warning,
        tail = timer.category.label,
        tailColor = categoryColor(timer.category),
        onClick = onComplete,
        modifier = modifier,
        transformation = transformation,
        container = WearTimerColors.ExpiredSurface,
        // 정본 `stroke: $warning-600, 1.5`. 만료 행은 다른 행과 확실히 갈라져 보여야 한다.
        border = BorderStroke(1.dp, WearTimerColors.WarningStrong),
        trailing = {
            // ⚠️ **[완료] 는 아이콘 하나다 — 전체폭 막대가 아니다.**
            // 막대를 아래에 두면 행이 일반 행의 2.2 배 키가 된다. 원형 화면에서 변형은
            // 항목의 **중심**을 기준으로 걸려서, 키 큰 항목은 중심이 아직 가운데일 때
            // 이미 위끝이 곡면에 닿아 잘린다 — 가로 여백으로는 못 막는다(192dp 실측).
            // 그걸 페이드로 가리려다 상수 네 개를 눈대중으로 맞추게 됐고, 근거 없는
            // 값이라 조건이 바뀌면 또 깨진다. 원인(키)을 없애는 쪽으로 되돌렸다.
            //
            // 행 전체가 이미 [완료] 동작이라 이 아이콘은 **무엇이 일어나는지 알리는 표시**다
            // (다른 행은 눌러서 들어가고, 이 행은 눌러서 끝낸다).
            val mark = LeadingSize.scaled() * COMPLETE_MARK_RATIO
            Box(
                modifier = Modifier.size(mark).clip(CircleShape).background(WearTimerColors.WarningStrong),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_check),
                    contentDescription = "완료",
                    tint = WearTimerColors.OnWarning,
                    modifier = Modifier.size(mark * COMPLETE_GLYPH_RATIO)
                )
            }
        }
    ) { size ->
        Icon(
            painter = painterResource(R.drawable.nm_ic_bell_ring),
            contentDescription = null,
            tint = WearTimerColors.Warning,
            modifier = Modifier.size(size * LEADING_GLYPH_RATIO)
        )
    }
}

/**
 * 진행 중·일시정지 행 — 정본 `W1 활성 타이머`.
 *
 * 누르면 W2(조작)로 간다(spec §워치 "카드 탭으로 진입").
 */
@Composable
internal fun RunningTimerCard(
    timer: CareTimer,
    now: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    val paused = timer.state == TimerState.PAUSED
    val accent = if (paused) WearTimerColors.Muted else WearTimerColors.Primary
    TimerRow(
        title = timer.label,
        value = formatRemaining(timer.remainingAt(now)),
        valueColor = if (paused) WearTimerColors.Muted else WearTimerColors.PrimarySoft,
        // 멈춰 있다는 것은 분류보다 먼저 알아야 한다 — 그 자리를 내준다.
        tail = if (paused) "일시정지" else timer.category.label,
        tailColor = if (paused) WearTimerColors.Muted else categoryColor(timer.category),
        onClick = onOpen,
        modifier = modifier,
        transformation = transformation
    ) { size ->
        // 두께를 직접 준다 — 목록 안 작은 링이라 기본값(큰 링 전제)을 쓰면 두께가
        // 반지름에 가까워져 호가 점으로 뭉개진다(실기기 확인).
        TimerRing(
            fraction = timer.ringFractionAt(now),
            color = accent,
            trackColor = WearTimerColors.Track,
            strokeWidth = RingStroke.scaled(),
            modifier = Modifier.size(size)
        )
        if (paused) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_pause_solid),
                contentDescription = null,
                tint = WearTimerColors.Muted,
                modifier = Modifier.size(size * LEADING_GLYPH_RATIO)
            )
        }
    }
}

/**
 * 프리셋 행 — 정본 `W1 프리셋 페이지`. 누르면 바로 시작된다.
 *
 * ⚠️ **활성 행과 같은 [TimerRow] 를 쓴다.** 둘은 같은 목록의 두 페이지라 구조가 달라지면
 * 페이지를 넘길 때 화면이 바뀐 것처럼 보인다.
 */
@Composable
internal fun PresetCard(
    preset: TimerPreset,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    TimerRow(
        title = preset.label,
        value = formatDuration(preset.durationSeconds),
        valueColor = WearTimerColors.Muted,
        tail = preset.category.label,
        tailColor = categoryColor(preset.category),
        onClick = onStart,
        modifier = modifier,
        transformation = transformation
    ) { size ->
        Icon(
            painter = painterResource(R.drawable.nm_ic_circle_play),
            contentDescription = null,
            tint = WearTimerColors.Primary,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * 프리셋·활성이 공유하는 목록 행 — **[원형 표시] 처치명 / 값 · 꼬리말**.
 *
 * ## 왜 오른쪽 끝에 값을 두지 않나
 * ⚠️ **곡면이 잘라 먹는 자리가 거기다.** 원형 화면의 목록은 위아래 끝 항목이 원에 잘려
 * 렌즈 모양이 되는데(구글 기본 설정 앱도 같다 — 에뮬레이터에서 확인), 잘리는 건 배경의
 * 네 귀퉁이다. 배경이 잘리는 것은 플랫폼 기본 동작이라 괜찮지만 **글자가 잘리면 WO-V1** 이다.
 * 예전에는 소요시간을 행의 오른쪽 끝에 붙여 놔서, 끝 항목에서 「30분」이 `30눈` 으로
 * 잘렸다(192dp 실측). 값을 제목 아래로 내리면 그 자리를 아예 비우게 된다.
 *
 * 구글 목록 행에 오른쪽 정렬 값이 없는 것도 같은 이유로 보인다.
 *
 * @param value 왼쪽에 굵게 오는 수 — 프리셋은 소요시간, 활성은 남은 시간
 * @param tail 그 뒤에 붙는 말 — 분류, 또는 멈춰 있으면 「일시정지」
 * @param leading 왼쪽 원형 자리. 자기 지름을 받는다
 */
@Composable
private fun TimerRow(
    title: String,
    value: String,
    valueColor: Color,
    tail: String,
    tailColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
    container: Color = WearTimerColors.Card,
    border: BorderStroke? = null,
    trailing: (@Composable () -> Unit)? = null,
    leading: @Composable BoxScope.(Dp) -> Unit
) {
    val leadingSize = LeadingSize.scaled()
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = container),
        border = border,
        shape = RowShape,
        contentPadding = RowPadding,
        transformation = transformation,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(leadingSize)) {
            leading(leadingSize)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                // ⚠️ 트레일링 요소가 있으면 그것이 이미 글자를 가장자리에서 떼어 놓는다.
                // 여백을 또 주면 글자 자리만 뺏겨 「-29:41 · 검사」가 `-29:…` 가 된다.
                .padding(
                    start = LeadingGap.scaled(),
                    end = if (trailing == null) TextTrailingInset.scaled() else LeadingGap.scaled()
                )
        ) {
            Text(
                text = title,
                style = WearTimerType.RowTitle.scaled(),
                color = WearTimerColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = valueColor)) { append(value) }
                    withStyle(SpanStyle(color = WearTimerColors.Muted)) { append(" · ") }
                    withStyle(SpanStyle(color = tailColor)) { append(tail) }
                },
                style = WearTimerType.RowSubtitle.scaled(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}
