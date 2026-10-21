package app.nursemate.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2BottomBar
import app.nursemate.core.designsystem.v2.NmV2BottomBarButton
import app.nursemate.core.designsystem.v2.NmV2ButtonKind
import app.nursemate.core.designsystem.v2.NmV2CardShape
import app.nursemate.core.designsystem.v2.NmV2Menu
import app.nursemate.core.designsystem.v2.NmV2MenuItem
import app.nursemate.core.designsystem.v2.NmV2Modal
import app.nursemate.core.designsystem.v2.NmV2NavBar
import app.nursemate.core.designsystem.v2.NmV2Surface
import app.nursemate.core.designsystem.v2.NmV2SurfaceStyle
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillHistoryPill
import app.nursemate.pill.PILL_DISCLAIMER
import app.nursemate.pill.PillRevokedBadge
import app.nursemate.pill.PillShareSheetV2
import coil3.compose.AsyncImage

/**
 * 식별 기록 상세 — 정본 「식별 기록 (NM-590)」 ⑤ ~ ⑩ · spec §식별 기록 — 기록 상세.
 *
 * 식별 시각 · 알약 목록 · `공유` · 면책 고지. `완료` 버튼은 없다 — `뒤로` 로 나가므로 `공유` 가 주 버튼이다.
 * 더보기 메뉴에는 `삭제` 만 있다 — 기록은 고쳐 쓰지 않는다.
 *
 * @param time 요약으로 받은 식별 시각(`10월 9일 (금) 오후 2:14`) — 조회 전에도 바로 보인다
 * @param pillCount 요약의 알약 수 — 조회 중 자리 표시 행 개수
 */
@Suppress("LongParameterList")
@Composable
fun PillHistoryDetailScreen(
    time: String?,
    pillCount: Int,
    state: HistoryDetailState,
    deleting: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPillDetail: (PillHistoryPill) -> Unit,
    onDelete: () -> Unit,
    onCopyText: () -> Unit,
    onSavePdf: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val loaded = state as? HistoryDetailState.Loaded

    Box(Modifier.fillMaxSize().background(NmTheme.semanticColors.surface)) {
        Column(Modifier.fillMaxSize()) {
            NmV2NavBar(
                title = "식별 기록",
                onBack = onBack,
                trailing = {
                    Icon(
                        painterResource(NmSymbols.MoreVert),
                        contentDescription = "더보기",
                        tint = NmTheme.semanticColors.textPrimary,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(enabled = loaded != null, role = Role.Button) { menu = true }
                    )
                }
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "time") { TimeRow(time) }
                when (state) {
                    HistoryDetailState.Loading -> items(pillCount.coerceAtLeast(1)) { PlaceholderRow() }

                    HistoryDetailState.Failed -> item(key = "failed") {
                        HistoryLoadFailed(onRetry, Modifier.fillParentMaxHeight(FAILED_HEIGHT).fillMaxWidth())
                    }

                    is HistoryDetailState.Loaded -> items(state.history.pills.withIndex().toList(), key = {
                        it.index
                    }) { (_, pill) ->
                        PillRow(pill, onDetail = { onPillDetail(pill) })
                    }
                }
            }
            NmV2BottomBar {
                // 조회 중 · 실패에서는 공유할 알약이 없다.
                NmV2BottomBarButton("공유", onClick = { sharing = true }, enabled = loaded != null)
                Text(PILL_DISCLAIMER, style = NmV2Type.caption, color = NmTheme.semanticColors.textTertiary)
            }
        }

        if (menu) {
            // 메뉴 바깥을 누르면 닫는다 — 정본 ⑥ 의 옅은 막(#0F172A1F)이다.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MenuScrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        menu =
                            false
                    }
            )
            NmV2Menu(
                items = listOf(
                    NmV2MenuItem("삭제", NmSymbols.Delete, destructive = true) {
                        menu = false
                        confirmDelete = true
                    }
                ),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp)
            )
        }
    }

    if (confirmDelete) {
        NmV2Modal(
            title = "이 기록을 삭제할까요?",
            message = "삭제하면 되돌릴 수 없어요",
            confirmText = "삭제",
            dismissText = "취소",
            confirmKind = NmV2ButtonKind.Destructive,
            onConfirm = {
                if (!deleting) {
                    confirmDelete = false
                    onDelete()
                }
            },
            onDismissRequest = { confirmDelete = false }
        )
    }
    if (sharing) {
        PillShareSheetV2(
            onCopyText = {
                sharing = false
                onCopyText()
            },
            onSavePdf = {
                sharing = false
                onSavePdf()
            },
            onDismiss = { sharing = false }
        )
    }
}

@Composable
private fun TimeRow(time: String?) {
    Row(
        Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(NmSymbols.EventUpcoming),
            null,
            tint = NmTheme.semanticColors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Text(time.orEmpty(), style = NmV2Type.metaLabel, color = NmTheme.semanticColors.textSecondary)
    }
}

/**
 * 알약 한 줄 — 낱알 이미지 · 품목명(허가 종료 배지) · 업체명 · 세부정보(ⓘ). 줄을 누르면 세부정보를 연다.
 * 사진은 **품목의 낱알 이미지**다 — 기록에는 촬영 사진이 없다. 이미지가 없으면 회색 자리만 둔다.
 */
@Composable
private fun PillRow(pill: PillHistoryPill, onDetail: () -> Unit) {
    val colors = NmTheme.semanticColors
    NmV2Surface(NmV2SurfaceStyle.Card, Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(NmV2CardShape)
                .clickable(role = Role.Button, onClick = onDetail)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = pill.pillThumbnailUrl.ifBlank { null },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(width = 64.dp, height = 34.dp)
                    .clip(RoundedCornerShape(NmRadius.sm))
                    .background(NmColor.Neutral.C100)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pill.pillName,
                        style = NmV2Type.tileTitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (pill.licenseStatus == LicenseStatus.REVOKED) PillRevokedBadge()
                }
                Text(pill.companyName, style = NmV2Type.rowSubtitle, color = colors.textSecondary, maxLines = 1)
            }
            Icon(
                painterResource(NmSymbols.Info),
                contentDescription = "세부정보",
                tint = NmColor.Primary.C500,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 조회 중 자리 표시 행 — 정본 ⑨. 사진 · 두 줄 글자 · 버튼 자리를 회색 덩어리로. */
@Composable
private fun PlaceholderRow() {
    NmV2Surface(NmV2SurfaceStyle.ListCard, Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp).height(60.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Block(48, 28, NmColor.Neutral.C100)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Block(150, 14, NmColor.Neutral.C200)
                Block(80, 10, NmColor.Neutral.C200)
            }
            Box(Modifier.size(20.dp).background(NmColor.Neutral.C100, CircleShape))
        }
    }
}

@Composable
private fun Block(width: Int, height: Int, color: Color) =
    Box(Modifier.size(width.dp, height.dp).background(color, RoundedCornerShape(NmRadius.sm)))

/** 조회 실패 안내가 차지하는 목록 높이 비율 — 그 안 가운데에 둔다. */
private const val FAILED_HEIGHT = 0.8f

/** 더보기 메뉴가 열렸을 때 화면에 덮는 옅은 막 — 정본 `#0F172A1F`(`shadow-card-color` 와 같은 값). */
private val MenuScrim = Color(0x1F0F172A)
