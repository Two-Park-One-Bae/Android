package app.nursemate.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2ButtonFloating
import app.nursemate.core.designsystem.v2.NmV2Surface
import app.nursemate.core.designsystem.v2.NmV2SurfaceStyle
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.PillHistorySummary
import app.nursemate.core.model.Usage
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * 알약 탭 — 촬영 버튼 + 식별 기록 목록. 정본 「알약 식별 — 식별 기록 (NM-590)」 ① ~ ④.
 *
 * - 제목 `알약 식별` · `오늘 남은 횟수 n/limit`(홈 카드와 같은 값, 0회면 경고색)
 * - 소제목 `식별 기록`, 날짜별 묶음(`오늘` · `어제` · `10월 7일 (수)`) 안에 한 줄 항목
 * - 빈 상태 · 불러오기 실패에서도 촬영 버튼은 그대로 쓴다
 * - 끝에 `최근 100건까지 보관해요`
 *
 * 촬영 버튼은 오른쪽 아래에 띄운다 — 타이머 탭 추가 버튼과 같은 자리. 목록 끝에 버튼 높이만큼 여백을 둔다.
 */
@Suppress("LongParameterList")
@Composable
fun PillTabScreen(
    usage: Usage?,
    state: HistoryListState,
    today: LocalDate,
    onCapture: () -> Unit,
    onOpen: (PillHistorySummary) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("알약 식별", style = NmV2Type.screenTitle, color = colors.textPrimary)
                // 모르면 줄을 비운다 — 한도를 지어내지 않는다(홈 카드와 같은 규칙).
                if (usage != null) {
                    Text(
                        "오늘 남은 횟수 ${usage.remaining}/${usage.limit}",
                        style = NmV2Type.cardBody,
                        color = if (usage.exhausted) NmColor.Error.C600 else colors.textSecondary
                    )
                }
            }
            Text(
                "식별 기록",
                style = NmV2Type.tileTitle,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp)
            )
            when (state) {
                HistoryListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NmColor.Primary.C500)
                }

                HistoryListState.Failed -> HistoryLoadFailed(onRetry, Modifier.fillMaxSize().padding(bottom = FabRoom))

                is HistoryListState.Loaded -> if (state.items.isEmpty()) {
                    HistoryStateBlock(
                        NmSymbols.History,
                        "아직 식별 기록이 없어요",
                        Modifier.fillMaxSize().padding(bottom = FabRoom)
                    )
                } else {
                    HistoryList(state, today, onOpen, onLoadMore, onRetryMore = onLoadMore)
                }
            }
        }
        NmV2ButtonFloating(
            icon = NmSymbols.PhotoCamera,
            contentDescription = "촬영",
            onClick = onCapture,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp)
        )
    }
}

/** 날짜 묶음 하나 = 목록 카드 한 장. */
private data class DateGroup(val label: String, val items: List<Pair<PillHistorySummary, ZonedDateTime?>>)

@Composable
private fun HistoryList(
    state: HistoryListState.Loaded,
    today: LocalDate,
    onOpen: (PillHistorySummary) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit
) {
    val groups = remember(state.items, today) {
        val parsed = state.items.map { it to parseIdentifiedAt(it.identifiedAt) }
        val dated = parsed.filter { (_, at) -> at != null }
            .groupBy { (_, at) -> at!!.toLocalDate() }
            .map { (date, items) -> DateGroup(dateGroupLabel(date, today), items) }
        // 시각을 읽지 못한 기록은 **맨 끝** 묶음에 모은다 — 버리면 기록이 사라진 것처럼 보이고, 중간에 끼면
        // 앞 날짜의 기록처럼 읽힌다.
        val undated = parsed.filter { (_, at) -> at == null }
        if (undated.isEmpty()) dated else dated + DateGroup("", undated)
    }
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(
        listState,
        enabled = state.nextCursor != null && !state.loadingMore && !state.moreFailed,
        onLoadMore
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = FabRoom),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.items.first().first.historyId }) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (group.label.isNotEmpty()) {
                    Text(group.label, style = NmV2Type.groupLabel, color = NmTheme.semanticColors.textTertiary)
                }
                NmV2Surface(NmV2SurfaceStyle.ListCard, Modifier.fillMaxWidth()) {
                    Column {
                        group.items.forEachIndexed { index, (item, at) ->
                            HistoryRow(item, at, onClick = { onOpen(item) })
                            if (index <
                                group.items.lastIndex
                            ) {
                                HorizontalDivider(thickness = 1.dp, color = NmColor.Neutral.C100)
                            }
                        }
                    }
                }
            }
        }
        item(key = END_KEY) { ListEnd(state, onRetryMore) }
    }
}

@Composable
private fun HistoryRow(item: PillHistorySummary, at: ZonedDateTime?, onClick: () -> Unit) {
    val colors = NmTheme.semanticColors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 한 줄 — 품목명이 넘치면 품목명만 말줄임하고 `외 N` 은 남긴다.
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.firstPillName,
                style = NmV2Type.tileTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (item.pillCount >
                1
            ) {
                Text(
                    "외 ${item.pillCount - 1}",
                    style = NmV2Type.cardBody,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }
        if (at != null) Text(listTime(at), style = NmV2Type.rowSubtitle, color = colors.textSecondary)
        Icon(painterResource(NmSymbols.ChevronRight), null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
    }
}

/** 목록 끝 — 이어서 받는 중 · 이어서 실패 · 끝(`최근 100건까지 보관해요`). */
@Composable
private fun ListEnd(state: HistoryListState.Loaded, onRetryMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        when {
            state.loadingMore -> CircularProgressIndicator(
                Modifier.size(24.dp),
                color = NmColor.Primary.C500,
                strokeWidth = 2.dp
            )

            state.moreFailed -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("불러오지 못했어요", style = NmV2Type.rowSubtitle, color = NmTheme.semanticColors.textTertiary)
                RetryButton(onRetryMore)
            }

            state.nextCursor == null -> Text(
                "최근 100건까지 보관해요",
                style = NmV2Type.rowSubtitle,
                color = NmTheme.semanticColors.textTertiary
            )
        }
    }
}

/**
 * 목록 끝 칸이 화면에 들어오면 다음 묶음을 부른다.
 *
 * ⚠️ 칸 **개수**로 재지 않는다 — 한 칸이 날짜 묶음 하나라 날짜가 적으면(100건이 이틀) 늘 「끝에서 세 칸 안」이 되어
 *    모든 묶음을 연달아 받는다. 끝 칸이 실제로 보일 때만 부른다.
 */
@Composable
private fun LoadMoreWhenNearEnd(listState: LazyListState, enabled: Boolean, onLoadMore: () -> Unit) {
    val atEnd by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == END_KEY } }
    }
    LaunchedEffect(atEnd, enabled) { if (atEnd && enabled) onLoadMore() }
}

/** 촬영 버튼(56) + 아래 여백(16) + 틈 — 마지막 항목이 버튼에 가리지 않게. */
private val FabRoom = 88.dp
private const val END_KEY = "end"
