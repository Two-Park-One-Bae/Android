package app.nursemate.pill

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2BottomBar
import app.nursemate.core.designsystem.v2.NmV2BottomBarButton
import app.nursemate.core.designsystem.v2.NmV2ButtonKind
import app.nursemate.core.designsystem.v2.NmV2CardShape
import app.nursemate.core.designsystem.v2.NmV2Color
import app.nursemate.core.designsystem.v2.NmV2Modal
import app.nursemate.core.designsystem.v2.NmV2NavBar
import app.nursemate.core.designsystem.v2.NmV2Surface
import app.nursemate.core.designsystem.v2.NmV2SurfaceStyle
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.vision.DetectedPill
import app.nursemate.ui.SystemBarIcons
import coil3.compose.AsyncImage

/**
 * 식별 결과 — 정본 「알약 식별 (V2.1.0)」 ① ~ ⑩ · spec §식별 → 식별 결과 · §완료 · 공유 (NM-587).
 *
 * 사진 위 영역 표시 · 진행 표시 · 한 줄 카드 · `+ 알약 추가`, 아래에 `공유` · `완료` · 면책 고지.
 *
 * ## 이 화면이 최종 결과를 겸한다
 * 출시된 앱은 모두 확정해야 `결과 확인` 이 켜지고 최종 결과 리스트로 넘어갔다. V2.1.0 은 그 화면을 없앴다 —
 * 하나라도 확정하면 여기서 바로 공유하고 `완료` 로 기록을 남긴다. 확정하지 않은 알약은 기록 · 공유물에서 빠진다.
 *
 * ## 카드는 상태만 말한다
 * 속성 · 조건은 수정 화면의 일이다. 카드는 「이 알약이 지금 어느 단계인가」를 배경 · 번호 색으로 말하고,
 * 사진 위 테두리 · 번호 태그가 같은 색을 써 어느 카드가 어느 알약인지 잇는다([PillResultStatus]).
 *
 * @param save 기록 저장 단계 — 저장 중에는 `완료` 가 `저장 중…` 이고, 실패면 모달을 띄운다
 * @param onExit 이탈 확인에서 `나가기` — 기록 없이 들어온 화면으로
 * @param onSave `완료`(또는 종료 확인의 `종료` · 실패 모달의 `다시 시도`)
 * @param onLeaveUnsaved 저장 실패 모달의 `나가기` — 저장하지 않고 들어온 화면으로
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun PillResultScreen(
    ui: PillResultUi,
    save: HistorySave,
    onExit: () -> Unit,
    onEditPill: (String) -> Unit,
    onRemovePill: (String) -> Unit,
    onAddPill: () -> Unit,
    onDetail: (PillCandidate) -> Unit,
    onCopyText: () -> Unit,
    onSavePdf: () -> Unit,
    onSave: () -> Unit,
    onLeaveUnsaved: () -> Unit,
    onDismissSaveFailure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    val saving = save == HistorySave.Saving
    // 시트 · 확인 모달은 화면을 돌려도 남는다.
    var confirmingExit by rememberSaveable { mutableStateOf(false) }
    var confirmingFinish by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    // 어느 카드의 ⋮ 를 눌렀는가 — 메뉴 오른쪽 위가 놓일 자리와 함께.
    var menuFor by remember { mutableStateOf<String?>(null) }
    var menuTopEnd by remember { mutableStateOf(IntOffset.Zero) }

    // 여기서 나가면 이미 쓴 식별 1회와 고친 내용이 함께 사라진다 — 확인을 받는다. 저장 중에는 나가지 않는다.
    BackHandler { if (!saving) confirmingExit = true }
    // 메뉴가 열려 있으면 뒤로는 메뉴만 닫는다 — 나중에 건 BackHandler 가 먼저 받는다.
    BackHandler(enabled = menuFor != null) { menuFor = null }

    // 인셋은 안쪽 Column 에만 건다 — ⋮ 메뉴는 화면 기준 좌표(positionInRoot)로 놓이므로 바깥 상자가 화면 원점이어야 한다.
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            NmV2NavBar(title = "식별 결과", onBack = { if (!saving) confirmingExit = true })
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
                    ResultPhoto(ui)
                }
                Progress(ui)
                // 저장 중에는 결과를 고치지 못한다 — 보낸 뒤에 고친 것은 기록에 남지 않고 화면만 닫힌다.
                ui.cards.forEach { card ->
                    ResultCardRow(
                        card = card,
                        enabled = !saving,
                        onClick = { onEditPill(card.id) },
                        onDetail = { card.selected?.let(onDetail) },
                        onMenu = { topEnd ->
                            menuTopEnd = topEnd
                            menuFor = card.id
                        }
                    )
                }
                AddPillButton(enabled = !saving, onClick = onAddPill)
            }
            ResultBottomBar(
                canFinish = ui.confirmed > 0,
                saving = saving,
                onShare = { sharing = true },
                onFinish = { if (ui.unconfirmed > 0) confirmingFinish = true else onSave() }
            )
        }

        menuFor?.let { target ->
            PillCardMenu(
                topEnd = menuTopEnd,
                onEdit = {
                    menuFor = null
                    onEditPill(target)
                },
                onDelete = {
                    menuFor = null
                    deleting = target
                },
                onDismiss = { menuFor = null }
            )
        }
    }

    deleting?.let { target ->
        NmV2Modal(
            title = "이 알약을 삭제할까요?",
            confirmText = "삭제",
            dismissText = "취소",
            confirmKind = NmV2ButtonKind.Destructive,
            onConfirm = {
                deleting = null
                onRemovePill(target)
            },
            onDismissRequest = { deleting = null }
        )
    }
    if (confirmingFinish) {
        NmV2Modal(
            title = "알약 식별을 종료할까요?",
            message = "확정하지 않은 ${ui.unconfirmed}개는 기록에 남지 않아요",
            confirmText = "종료",
            dismissText = "취소",
            onConfirm = {
                confirmingFinish = false
                onSave()
            },
            onDismissRequest = { confirmingFinish = false }
        )
    }
    if (confirmingExit) {
        NmV2Modal(
            title = "지금 나가면 식별한 내용이 사라져요",
            message = "사용한 식별 횟수는 돌아오지 않아요",
            confirmText = "나가기",
            dismissText = "취소",
            confirmKind = NmV2ButtonKind.Destructive,
            onConfirm = {
                confirmingExit = false
                onExit()
            },
            onDismissRequest = { confirmingExit = false }
        )
    }
    (save as? HistorySave.Failed)?.let { failed ->
        SaveFailedModal(failed, onSave, onLeaveUnsaved, onDismissSaveFailure)
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

/** 사진 카드 — 유리 카드 250 안에 사진 234(모서리 14), 그 위에 영역 표시. */
@Composable
private fun ResultPhoto(ui: PillResultUi) {
    NmV2Surface(NmV2SurfaceStyle.Card, Modifier.size(ResultPhotoSize + 16.dp)) {
        Box(
            Modifier
                .padding(8.dp)
                .size(ResultPhotoSize)
                .clip(RoundedCornerShape(14.dp))
                .background(NmColor.Neutral.C100)
        ) {
            ui.photo?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "촬영한 사진",
                    // 검출 좌표가 정사각 사진 기준이라 채운다 — 사진은 이미 중앙 정사각으로 잘려 있다.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ui.cards.forEach { card ->
                card.detected?.let { DetectionMarker(it, card.number, card.status.colors().marker) }
            }
        }
    }
}

/**
 * 사진 위 영역 테두리 + 번호 태그. 좌표는 0~1 정규화라 [ResultPhotoSize] 를 곱한다.
 * 태그는 영역 왼쪽 위에 얹히고, 사진 위로 넘치지 않게 0 에서 멈춘다.
 */
@Composable
private fun BoxScope.DetectionMarker(pill: DetectedPill, number: Int, color: Color) {
    val box = pill.detection
    val left = ResultPhotoSize * box.x
    val top = ResultPhotoSize * box.y
    Box(
        Modifier
            .offset(x = left, y = top)
            .size(width = ResultPhotoSize * box.width, height = ResultPhotoSize * box.height)
            .border(2.dp, color, RoundedCornerShape(4.dp))
    )
    Box(
        Modifier
            .offset(x = left, y = (top - TagHeight).coerceAtLeast(0.dp))
            // 영역 왼쪽 위와 맞물리게 왼쪽 아래만 각지다(정본 [4,4,4,0]).
            .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text("$number", style = TagLabel, color = Color.White)
    }
}

/**
 * `알약 N개를 찾았어요` · `확정 / 전체 식별 완료` · `N개 남음` · 진행 막대.
 * 모두 확정하지 않아도 끝낼 수 있으므로 「모두 식별하라」는 안내는 두지 않는다(spec §진행 표시).
 */
@Composable
private fun Progress(ui: PillResultUi) {
    val colors = NmTheme.semanticColors
    val total = ui.cards.size
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("알약 ${ui.found}개를 찾았어요", style = NmV2Type.tileTitle, color = colors.textPrimary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${ui.confirmed} / $total 식별 완료",
                style = NmV2Type.chipConfirmed,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (ui.unconfirmed > 0) {
                Text("${ui.unconfirmed}개 남음", style = NmV2Type.rowSubtitle, color = colors.textTertiary)
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(NmColor.Neutral.C100)) {
            if (total > 0 && ui.confirmed > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(ui.confirmed.toFloat() / total)
                        .fillMaxHeight()
                        .background(NmColor.Success.C500, CircleShape)
                )
            }
        }
    }
}

/**
 * 한 줄 카드 — 번호 · 사진 · 상태 · (식별 완료면) ⓘ · ⋮. 카드를 누르면 수정이다.
 *
 * 사진은 잘라 낸 알약 사진이다. 직접 추가한 알약은 사진에 영역이 없어 고른 품목의 낱알 이미지를 쓴다(spec NM-187).
 */
@Composable
private fun ResultCardRow(
    card: PillResultCard,
    enabled: Boolean,
    onClick: () -> Unit,
    onDetail: () -> Unit,
    onMenu: (IntOffset) -> Unit
) {
    val colors = NmTheme.semanticColors
    val tone = card.status.colors()
    val identified = card.status == PillResultStatus.IDENTIFIED
    // 메뉴는 가로로 카드 오른쪽 끝, 세로로 ⋮ 아래에 놓인다 — 둘을 따로 잰다.
    var cardRight by remember { mutableIntStateOf(0) }
    var menuTop by remember { mutableIntStateOf(0) }

    NmV2Surface(
        if (identified) NmV2SurfaceStyle.ListCardSuccess else NmV2SurfaceStyle.Card,
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .onGloballyPositioned { cardRight = it.positionInRoot().round().x + it.size.width }
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(NmV2CardShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(start = 14.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(26.dp).background(tone.badge, CircleShape), contentAlignment = Alignment.Center) {
                Text("${card.number}", style = NmV2Type.smallButton, color = tone.number)
            }
            Thumbnail(card)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        card.title(),
                        style = if (card.status == PillResultStatus.PENDING) RowTitlePending else RowTitle,
                        color = tone.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (card.selected?.licenseStatus == LicenseStatus.REVOKED) PillRevokedBadge()
                }
                card.subtitle()?.let {
                    Text(
                        it,
                        style = NmV2Type.rowSubtitle,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (identified) {
                Box(
                    Modifier.size(
                        32.dp
                    ).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onDetail),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(NmSymbols.Info),
                        contentDescription = "세부정보",
                        tint = NmColor.Primary.C500,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Box(
                Modifier
                    .size(width = 28.dp, height = 32.dp)
                    .onGloballyPositioned { menuTop = it.positionInRoot().round().y + it.size.height }
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button) { onMenu(IntOffset(cardRight, menuTop)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(NmSymbols.MoreVert),
                    contentDescription = "더보기",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(card: PillResultCard) {
    val modifier = Modifier.size(40.dp).clip(RoundedCornerShape(NmRadius.md)).background(NmColor.Neutral.C100)
    val crop = card.detected?.crop
    if (crop != null) {
        // 낱알이 잘리면 각인을 못 보므로 채우지 않고 맞춘다.
        Image(crop.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        AsyncImage(
            model = card.selected?.pillThumbnailUrl?.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

/** 첫 줄 — 식별 완료면 품목명. */
private fun PillResultCard.title(): String = when (status) {
    PillResultStatus.IDENTIFIED -> selected?.pillName.orEmpty()
    PillResultStatus.FAILED -> "정보를 읽지 못했어요"
    PillResultStatus.PENDING -> "후보를 골라 주세요"
}

/** 둘째 줄 — 식별 완료면 업체명(직접 추가면 `· 직접 추가`), 인식 실패면 할 일. 선택 전에는 없다. */
private fun PillResultCard.subtitle(): String? = when (status) {
    PillResultStatus.IDENTIFIED -> selected?.companyName?.let { if (manual) "$it · 직접 추가" else it }
    PillResultStatus.FAILED -> "직접 입력"
    PillResultStatus.PENDING -> null
}

/** `+ 알약 추가` — 미탐지 누락을 사용자가 직접 메운다(spec NM-187). 볼록한 흰 판 · 모서리 12. */
@Composable
private fun AddPillButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(NmRadius.md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NmTheme.semanticColors.surface, NmV2Color.Raised.neutralBottom)))
            .border(1.dp, NmColor.Neutral.C200, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(NmSymbols.Add), null, tint = NmColor.Primary.C700, modifier = Modifier.size(18.dp))
        Text("알약 추가", style = RowTitle, color = NmColor.Primary.C700)
    }
}

/**
 * `공유`(보조) · `완료`(주) + 면책 고지. 하나도 확정하지 않았으면 둘 다 꺼진다 — 빈 기록을 만들지 않는다.
 * 저장 중에는 `완료` 가 `저장 중…` 이고 다시 눌리지 않으며, `공유` 는 꺼진다.
 */
@Composable
private fun ResultBottomBar(canFinish: Boolean, saving: Boolean, onShare: () -> Unit, onFinish: () -> Unit) {
    NmV2BottomBar {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NmV2BottomBarButton(
                "공유",
                onClick = onShare,
                modifier = Modifier.weight(1f),
                enabled = canFinish && !saving,
                kind = NmV2ButtonKind.Secondary
            )
            NmV2BottomBarButton(
                if (saving) "저장 중…" else "완료",
                onClick = { if (!saving) onFinish() },
                modifier = Modifier.weight(1f),
                enabled = canFinish
            )
        }
        Text(PILL_DISCLAIMER, style = NmV2Type.caption, color = NmTheme.semanticColors.textTertiary)
    }
}

/** 사진 한 변 — 정본 234. 영역 좌표가 이 크기를 기준으로 놓인다. */
private val ResultPhotoSize = 234.dp

/** 번호 태그 높이 — 글자 11 + 위아래 1. 영역 위로 올릴 거리다. */
private val TagHeight = 18.dp

private val RowTitle = NmV2Type.menu.copy(fontWeight = FontWeight.SemiBold)
private val RowTitlePending = NmV2Type.menu.copy(fontWeight = FontWeight.Normal)
private val TagLabel = NmV2Type.caption.copy(fontWeight = FontWeight.Bold)
