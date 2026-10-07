package app.nursemate.pill

import android.graphics.Bitmap
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmConfirmDialog
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.vision.DetectedPill
import app.nursemate.ui.SystemBarIcons
import coil3.compose.AsyncImage

/**
 * 인식 결과 — 정본 `① 인식 결과` (NM-490 · NM-516).
 *
 * 사진 카드 위에 번호 배지가 달린 BBOX를 얹고, 아래에 알약별 카드를 세운다.
 *
 * ## 카드는 상태만 말한다
 * v0 카드에는 색·모양·제형 칩과 앞뒤 각인이 함께 붙어 높이가 두 배였다. V1 은 그걸 전부
 * **수정 화면으로 옮기고**, 여기엔 「이 알약이 지금 어느 단계인가」만 남긴다
 * ([PillResultStatus]). 결과 화면에서 고를 것은 없고, 어디를 더 손봐야 하는지만 보면 된다.
 *
 * 사진 위 테두리·번호 태그는 카드와 **같은 색**을 쓴다 — 그래야 셋째 카드가 사진 어느
 * 알약인지 눈으로 이어진다.
 */
@Composable
fun PillResultScreen(
    state: PillUiState,
    onBack: () -> Unit,
    onRemovePill: (String) -> Unit,
    onEditPill: (String) -> Unit,
    onAddPill: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    // 여기서 나가면 **이미 차감된 식별 1회**와 사용자가 고친 속성이 함께 사라진다.
    // 미리보기②는 "다시 찍으면 됨"이라 확인 없이 보내지만, 이 화면은 대가가 다르다.
    // iOS 도 같은 자리에 확인을 둔다(DrugIdentificationVC "지금 나가면 식별한 내용이 사라져요").
    var confirmingExit by remember { mutableStateOf(false) }
    BackHandler { confirmingExit = true }

    // ⚠️ 추출 결과 맵을 직접 읽지 않는다 — 수정 화면에서 고친 값이 여기 반영돼야 한다.

    // 지운 알약은 목록·사진 표시·번호에서 함께 빠진다. pillId 는 검출 순서로 고정돼 있어
    // 번호가 다시 매겨져도 서버에 보낸 키와 어긋나지 않는다.
    //
    // 수동 추가 알약은 검출 결과 **뒤에** 붙고 사진에 대응 영역이 없다 — 그래서 목록은
    // 이렇게 한 줄로 합쳐 두고, 오버레이만 [ResultPill.detected] 가 있는 것에만 그린다.
    val detectedById = (state.detection as? DetectionPhase.Success)?.result?.pills.orEmpty()
        .mapIndexed { index, pill -> pillId(index) to pill }
        .toMap()
    val pills = state.pillIds().map { ResultPill(id = it, detected = detectedById[it]) }

    // 어느 카드의 ⋮ 를 눌렀는가. null 이면 메뉴가 닫힌 상태다.
    var menuFor by remember { mutableStateOf<String?>(null) }
    // 메뉴 우측 상단이 놓일 지점 — 가로는 카드 오른쪽 끝, 세로는 ⋮ 버튼 아래.
    var menuTopEnd by remember { mutableStateOf(IntOffset.Zero) }
    var deletingFor by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgApp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            NmNavBar(title = "인식 결과", onBack = { confirmingExit = true })

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PillPhotoCard(photo = state.photo) {
                        pills.forEachIndexed { index, item ->
                            item.detected?.let {
                                DetectionMarker(
                                    pill = it,
                                    number = index + 1,
                                    // 카드와 **같은 색**이어야 어느 카드가 어느 알약인지 눈으로 이어진다(정본 ①).
                                    tone = PillResultStatus
                                        .of(state.editOf(item.id), state.selections[item.id])
                                        .colors()
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (val phase = state.detection) {
                        is DetectionPhase.Success -> {
                            Text(
                                text = "알약 ${pills.size}개를 찾았어요",
                                style = ListTitle,
                                color = colors.textPrimary
                            )
                            pills.forEachIndexed { index, item ->
                                PillRow(
                                    crop = item.detected?.crop,
                                    number = index + 1,
                                    edit = state.editOf(item.id),
                                    selected = state.selections[item.id],
                                    onClick = { onEditPill(item.id) },
                                    onMenuClick = { topEnd ->
                                        menuTopEnd = topEnd
                                        menuFor = item.id
                                    }
                                )
                            }

                            AddPillButton(onClick = onAddPill)
                        }

                        // 탐지 0개는 `⑥ 결과 없음`, 실패는 `⑦ 분석 실패`로 갈라져야 한다.
                        // 그 화면들을 만들기 전까지는 여기서 사실만 알린다.
                        DetectionPhase.Empty -> Text(
                            text = "알약을 찾지 못했어요",
                            style = ListTitle,
                            color = colors.textSecondary
                        )

                        is DetectionPhase.Failed -> Text(
                            text = phase.message,
                            style = NmTypography.body,
                            color = NmColor.Error.C600
                        )

                        else -> Unit
                    }
                }
            }

            ResultFooter(
                identified = pills.count { it.id in state.selections },
                total = pills.size,
                onConfirm = onConfirm
            )
        }

        if (menuFor != null) {
            PillCardMenu(
                topEnd = menuTopEnd,
                onEdit = {
                    val target = menuFor
                    menuFor = null
                    target?.let(onEditPill)
                },
                onDelete = {
                    deletingFor = menuFor
                    menuFor = null
                },
                onDismiss = { menuFor = null }
            )
        }

        deletingFor?.let { target ->
            NmConfirmDialog(
                title = "이 알약을 삭제할까요?",
                confirmLabel = "삭제",
                confirmContainer = NmColor.Error.C500,
                onConfirm = {
                    deletingFor = null
                    onRemovePill(target)
                },
                onDismiss = { deletingFor = null }
            )
        }

        if (confirmingExit) {
            NmConfirmDialog(
                title = "지금 나가면 식별한 내용이 사라져요",
                message = "사용한 식별 횟수는 돌아오지 않아요",
                confirmLabel = "나가기",
                confirmContainer = NmColor.Error.C500,
                onConfirm = {
                    confirmingExit = false
                    onBack()
                },
                onDismiss = { confirmingExit = false }
            )
        }
    }
}

/**
 * 사진 위 BBOX + 번호 배지.
 *
 * 검출 좌표는 0~1 정규화라 [PhotoSize] 를 곱해 배치한다.
 * 배지는 박스 **왼쪽 위에 얹히며**, 화면 밖으로 밀리지 않게 0 이상으로 잘라 둔다.
 *
 * ⚠️ **색은 카드가 정한다**([tone]). 정본이 「사진 박스 · 태그도 같은 색」으로 못박았다 —
 * 여기만 따로 칠하면 확정한 알약이 사진에서는 파란 채로 남아 어느 카드인지 못 잇는다.
 */
@Composable
private fun BoxScope.DetectionMarker(pill: DetectedPill, number: Int, tone: PillStatusColors) {
    val box = pill.detection
    val left = PhotoSize * box.x
    val top = PhotoSize * box.y

    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(width = PhotoSize * box.width, height = PhotoSize * box.height)
            .border(2.dp, tone.marker, RoundedCornerShape(4.dp))
    )
    Box(
        modifier = Modifier
            .offset(
                x = left,
                // 배지가 박스 위에 걸치도록 올린다. 사진 위쪽에 붙은 알약은 잘리지 않게 0에서 멈춘다.
                y = (top - BadgeHeight).coerceAtLeast(0.dp)
            )
            .background(
                color = tone.marker,
                // 박스 왼쪽 위와 맞물리도록 왼쪽 아래만 각지게 둔다(정본 cornerRadius [4,4,4,0]).
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 4.dp, bottomStart = 0.dp)
            )
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text = "$number", style = BadgeLabel, color = Color.White)
    }
}

/**
 * 인식 결과 **한 줄 상태 카드** — 정본 ①·⑭ (NM-490 · NM-516).
 *
 * ## 속성·조건·후보 수를 보여 주지 않는다
 * v0 카드는 색·모양·제형 칩과 앞뒤 각인 두 줄을 함께 그려 높이가 두 배였다. V1 에서 그건
 * **수정 화면의 일**이다 — 결과 화면은 「이 알약이 지금 어느 단계인가」만 말한다.
 *
 * 상태는 [PillResultStatus] 가 정하고, 배경 톤과 번호 색으로 드러난다. 사진 위 테두리·번호
 * 태그도 **같은 색**을 쓴다([DetectionMarker]) — 어느 카드가 어느 알약인지 눈으로 잇는 길이다.
 *
 * 카드 전체가 누를 영역이다(= 수정). ⋮ 는 수정·삭제 둘 다 연다.
 */
@Composable
private fun PillRow(
    crop: Bitmap?,
    number: Int,
    edit: PillEdit,
    selected: PillCandidate?,
    onClick: () -> Unit,
    onMenuClick: (IntOffset) -> Unit
) {
    val colors = NmTheme.semanticColors
    val status = PillResultStatus.of(edit, selected)
    val tone = status.colors()

    // 메뉴는 가로로 카드 오른쪽 끝, 세로로 ⋮ 버튼 아래에 놓인다 — 둘을 따로 잰다.
    var cardRight by remember { mutableIntStateOf(0) }
    var menuTop by remember { mutableIntStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .onGloballyPositioned { cardRight = it.positionInRoot().round().x + it.size.width }
            .clip(RoundedCornerShape(14.dp))
            .background(tone.card)
            // 카드 전체가 수정으로 들어가는 과녁이다(정본 ③ 변경 — 「한 줄 카드는 카드 전체가 누를 영역」).
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(tone.badge, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "$number", style = NumberBadge, color = tone.number)
        }

        val thumbnail = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NmColor.Neutral.C100)
        if (crop != null) {
            Image(
                bitmap = crop.asImageBitmap(),
                contentDescription = null,
                // 낱알이 잘리면 각인을 못 보므로 채우지 않고 맞춘다.
                contentScale = ContentScale.Fit,
                modifier = thumbnail
            )
        } else {
            // 수동 추가 알약은 사진에 대응 영역이 없다. 확정 전에는 자리만, 확정 뒤에는
            // 고른 후보의 낱알 이미지를 쓴다(spec NM-187).
            AsyncImage(
                model = selected?.pillThumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = thumbnail
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = selected?.pillName ?: status.title(),
                style = RowTitleDone,
                color = tone.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 둘째 줄은 **있을 때만** 그린다 — 확정이면 업체명, 실패면 무엇을 하라는 안내다.
            // 선택 전에는 할 말이 없어 한 줄로 둔다.
            status.subtitle(selected)?.let { sub ->
                Text(
                    text = sub,
                    style = if (status == PillResultStatus.FAILED) RowSubtitleAction else RowSubtitle,
                    color = if (status == PillResultStatus.FAILED) colors.textSecondary else colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 정본 카드에는 chevron 이 그려져 있지만, 스펙(§수정·삭제)은 **수정·삭제 둘 다** ⋮ 로
        // 들어간다고 적는다. 카드 탭이 수정이고 ⋮ 가 수정·삭제라, chevron 을 더하면 같은 일을
        // 하는 과녁이 셋이 된다.
        Icon(
            painter = painterResource(R.drawable.nm_ic_more_vertical),
            contentDescription = "메뉴",
            tint = colors.textTertiary,
            modifier = Modifier
                .size(28.dp)
                .onGloballyPositioned { menuTop = it.positionInRoot().round().y + it.size.height }
                .clip(CircleShape)
                .clickable { onMenuClick(IntOffset(cardRight, menuTop)) }
                .padding(5.dp)
        )
    }
}

/** 첫 줄 — 확정이면 품목명이 대신 들어가므로 여기엔 나머지 둘만 있다. */
private fun PillResultStatus.title(): String = when (this) {
    PillResultStatus.FAILED -> "정보 인식 실패"

    // 정본 ① 의 문구. 「알약을 선택해주세요」가 아니다.
    else -> "후보를 골라 주세요"
}

/** 둘째 줄 — 없으면 한 줄짜리 카드다. */
private fun PillResultStatus.subtitle(selected: PillCandidate?): String? = when (this) {
    PillResultStatus.IDENTIFIED -> selected?.companyName
    PillResultStatus.FAILED -> "직접 입력"
    PillResultStatus.PENDING -> null
}

/**
 * 하단 고정 — 진행 문구 + '결과 확인'.
 *
 * ⚠️ 활성 조건을 `identified == total` 로만 두면 **알약이 0개일 때도 활성**이 된다
 * (iOS가 그 상태다 — 다 지우면 버튼이 켜지지만 눌러도 아무 일이 없다).
 * `total > 0` 을 함께 본다.
 */
@Composable
private fun ResultFooter(identified: Int, total: Int, onConfirm: () -> Unit) {
    val colors = NmTheme.semanticColors
    val done = total > 0 && identified == total

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgApp)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            // 정본이 세 갈래로 말한다 — 아직 하나도 못 골랐을 때 / 남은 개수를 셀 때 / 다 됐을 때.
            text = when {
                done -> "${total}개 모두 식별 완료"
                identified == 0 -> "알약을 모두 식별해주세요 · $identified/$total"
                else -> "${total - identified}개 더 식별해주세요 · $identified/$total"
            },
            style = ProgressLabel,
            color = if (done) NmColor.Secondary.C600 else colors.textTertiary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (done) NmColor.Primary.C500 else NmColor.Neutral.C200)
                .clickable(enabled = done, onClick = onConfirm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "결과 확인",
                style = ConfirmLabel,
                color = if (done) NmColor.Neutral.C0 else colors.textTertiary
            )
        }
    }
}

/** 번호 배지 높이 — 텍스트 11sp + 위아래 패딩 1. BBOX 위로 올릴 거리 계산에 쓴다. */
private val BadgeHeight = 18.dp

// 정본 스케일에 없는 크기들이다. 화면이 요구하는 값이라 여기 명시한다.
private val ListTitle = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)

/** 목록 한 줄. 수동 추가 알약은 [detected] 가 없어 사진 오버레이도 크롭도 없다. */
private data class ResultPill(val id: String, val detected: DetectedPill?)

/** 정본 `⑤ / 알약 추가 버튼`. 미탐지 누락을 사용자가 직접 메우는 자리다(spec NM-187). */
@Composable
private fun AddPillButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NmColor.Primary.C50)
            .border(1.5.dp, NmColor.Primary.C300, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.nm_ic_plus),
            contentDescription = null,
            tint = NmColor.Primary.C600,
            modifier = Modifier.size(18.dp)
        )
        Text(text = "알약 추가", style = AddPillLabel, color = NmColor.Primary.C600)
    }
}

private val AddPillLabel = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

// 정본 ①·⑭ 의 카드 글자 — 첫 줄 14/bold, 둘째 줄 12. 「직접 입력」만 600 으로 눌러 둔다
// (할 일을 가리키는 말이라 업체명보다 세야 한다).
private val RowTitleDone = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
private val RowSubtitle = NmTypography.caption.copy(fontSize = 12.sp)
private val RowSubtitleAction = NmTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
private val NumberBadge = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val BadgeLabel = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
private val ProgressLabel = NmTypography.caption
private val ConfirmLabel = NmTypography.bodyLarge.copy(fontWeight = FontWeight.Bold)
