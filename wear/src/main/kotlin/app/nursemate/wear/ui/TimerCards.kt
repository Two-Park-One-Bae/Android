package app.nursemate.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import app.nursemate.core.model.CareTimer
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerPreset
import app.nursemate.core.model.TimerState
import app.nursemate.core.model.formatDuration
import app.nursemate.core.model.formatRemaining
import app.nursemate.wear.R

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
internal fun ExpiredTimerCard(timer: CareTimer, pending: Boolean, onComplete: () -> Unit) {
    Card(
        onClick = onComplete,
        enabled = !pending,
        colors = CardDefaults.cardColors(containerColor = WearTimerColors.ExpiredSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_bell_ring),
                contentDescription = null,
                tint = WearTimerColors.Warning,
                modifier = Modifier.size(16.dp)
            )
            TimerLabel(
                label = timer.label,
                category = timer.category,
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            )
        }
        Button(
            onClick = onComplete,
            enabled = !pending,
            colors = ButtonDefaults.buttonColors(containerColor = WearTimerColors.WarningStrong),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_check),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "완료",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * 진행 중·일시정지 카드 — 정본 `W1 활성 타이머`.
 *
 * ⚠️ **아직 누를 수 없다.** 정본은 카드 탭으로 W2(조작)로 가는데 그 화면이 다음 단계라,
 * 지금 꼬리표(chevron)를 그리면 눌러도 아무 일이 없는 죽은 표시가 된다.
 */
@Composable
internal fun RunningTimerCard(timer: CareTimer, now: Long) {
    val paused = timer.state == TimerState.PAUSED
    val accent = if (paused) WearTimerColors.Muted else WearTimerColors.Primary

    Card(
        onClick = {},
        enabled = false,
        colors = CardDefaults.cardColors(containerColor = WearTimerColors.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                CircularProgressIndicator(
                    progress = { timer.progressAt(now) },
                    colors = ProgressIndicatorDefaults.colors(
                        indicatorColor = accent,
                        trackColor = WearTimerColors.Track
                    ),
                    modifier = Modifier.size(32.dp)
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
                TimerLabel(label = timer.label, category = timer.category)
                Text(
                    text = if (paused) {
                        "${formatRemaining(timer.remainingAt(now))} · 일시정지"
                    } else {
                        formatRemaining(timer.remainingAt(now))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (paused) WearTimerColors.Muted else WearTimerColors.PrimarySoft,
                    maxLines = 1
                )
            }
        }
    }
}

/** 프리셋 행 — 정본 `W1 프리셋 페이지`. 누르면 바로 시작된다. */
@Composable
internal fun PresetCard(preset: TimerPreset, pending: Boolean, onStart: () -> Unit) {
    Card(
        onClick = onStart,
        enabled = !pending,
        colors = CardDefaults.cardColors(containerColor = WearTimerColors.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        // ⚠️ **가중치를 가진 자식은 하나여야 한다.** 라벨과 여백에 각각 weight 를 주면
        // 남는 폭이 반씩 나뉘어 라벨이 짧을수록 시간이 왼쪽으로 붙는다(정본은 우측 정렬).
        // 왼쪽 묶음만 늘리고 시간은 SpaceBetween 이 끝으로 민다 — 폰 위젯에서 겪은 것과 같다.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_circle_play),
                    contentDescription = null,
                    tint = if (pending) WearTimerColors.Muted else WearTimerColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = WearTimerColors.OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = formatDuration(preset.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = WearTimerColors.Muted,
                maxLines = 1
            )
        }
    }
}

/**
 * 처치 키워드 + 분류 태그.
 *
 * ⚠️ **여기까지만 보여 준다.** 메모는 워치에 노출하지 않는다(spec §워치 프라이버시 공통 규칙).
 */
@Composable
private fun TimerLabel(label: String, category: TimerCategory, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = WearTimerColors.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = category.label,
            style = MaterialTheme.typography.bodySmall,
            color = categoryColor(category),
            maxLines = 1
        )
    }
}
