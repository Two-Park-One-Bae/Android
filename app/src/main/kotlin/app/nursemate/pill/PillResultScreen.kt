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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmConfirmDialog
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.vision.DetectedPill
import app.nursemate.ui.SystemBarIcons

/**
 * 인식 결과 — 디자인 `⑤ 인식 결과`.
 *
 * 사진 카드 위에 번호 배지가 달린 BBOX를 얹고, 아래에 알약별 카드를 세운다.
 *
 * ## 아직 서버가 없어 비어 있는 부분
 * 정본 카드에는 **색·모양·제형 칩과 앞/뒤 각인 표기**가 붙는다. 그 값은 전부
 * `POST /pill-attributes` 가 주는 것이라 인증(NM-407)이 붙어야 채울 수 있다.
 * 지금은 카드 머리(번호·크롭 썸네일·제목·이동 표시)까지만 만든다.
 * ⋮ 메뉴(수정·삭제)와 '+ 알약 추가'도 후보 선택이 가능해진 뒤에 붙인다.
 */
@Composable
fun PillResultScreen(
    state: PillUiState,
    onBack: () -> Unit,
    onRemovePill: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    // 여기서 나가면 **이미 차감된 식별 1회**와 사용자가 고친 속성이 함께 사라진다.
    // 미리보기②는 "다시 찍으면 됨"이라 확인 없이 보내지만, 이 화면은 대가가 다르다.
    // iOS 도 같은 자리에 확인을 둔다(DrugIdentificationVC "지금 나가면 식별한 내용이 사라져요").
    var confirmingExit by remember { mutableStateOf(false) }
    BackHandler { confirmingExit = true }

    val attributes = (state.attributes as? AttributePhase.Done)?.byPillId.orEmpty()

    // 지운 알약은 목록·사진 표시·번호에서 함께 빠진다. pillId 는 검출 순서로 고정돼 있어
    // 번호가 다시 매겨져도 서버에 보낸 키와 어긋나지 않는다.
    val pills = (state.detection as? DetectionPhase.Success)?.result?.pills.orEmpty()
        .mapIndexed { index, pill -> pillId(index) to pill }
        .filterNot { (id, _) -> id in state.removedPillIds }

    // 어느 카드의 ⋮ 를 눌렀는가. null 이면 메뉴가 닫힌 상태다.
    var menuFor by remember { mutableStateOf<String?>(null) }
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
                        pills.forEachIndexed { index, (_, pill) ->
                            DetectionMarker(pill = pill, number = index + 1)
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
                            pills.forEachIndexed { index, (id, pill) ->
                                PillRow(
                                    pill = pill,
                                    number = index + 1,
                                    attribute = attributes[id],
                                    onMenuClick = { menuFor = id }
                                )
                            }
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

            ResultFooter(identified = 0, total = pills.size)
        }

        if (menuFor != null) {
            PillCardMenu(
                // 수정 화면은 ⑧(NM-395)에서 붙인다. 지금 눌러도 갈 곳이 없어 메뉴만 닫는다.
                onEdit = { menuFor = null },
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
 */
@Composable
private fun BoxScope.DetectionMarker(pill: DetectedPill, number: Int) {
    val box = pill.detection
    val left = PhotoSize * box.x
    val top = PhotoSize * box.y

    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(width = PhotoSize * box.width, height = PhotoSize * box.height)
            .border(2.dp, NmColor.Primary.C500, RoundedCornerShape(4.dp))
    )
    Box(
        modifier = Modifier
            .offset(
                x = left,
                // 배지가 박스 위에 걸치도록 올린다. 사진 위쪽에 붙은 알약은 잘리지 않게 0에서 멈춘다.
                y = (top - BadgeHeight).coerceAtLeast(0.dp)
            )
            .background(
                color = NmColor.Primary.C500,
                // 박스 왼쪽 위와 맞물리도록 왼쪽 아래만 각지게 둔다(정본 cornerRadius [4,4,4,0]).
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 4.dp, bottomStart = 0.dp)
            )
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text = "$number", style = BadgeLabel, color = Color.White)
    }
}

/**
 * 알약 카드 — 번호 · 크롭 썸네일 · 제목 · 속성 칩.
 *
 * @param attribute 서버가 뽑은 속성. null 이면 아직 못 받은 것이고, `failed` 면 이 알약만
 *                  추출에 실패한 것이라 **그 카드만** 직접 입력을 유도한다(spec §개별 추출 실패).
 */
@Composable
private fun PillRow(pill: DetectedPill, number: Int, attribute: PillAttribute?, onMenuClick: () -> Unit) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(NmColor.Primary.C50, CircleShape)
                    .border(1.dp, NmColor.Primary.C100, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "$number", style = NumberBadge, color = NmColor.Primary.C600)
            }

            Image(
                bitmap = pill.crop.asImageBitmap(),
                contentDescription = null,
                // 낱알이 잘리면 각인을 못 보므로 채우지 않고 맞춘다.
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NmColor.Neutral.C100)
            )

            // 추출에 실패한 알약은 이 카드만 그렇게 알린다. 나머지는 정상이다.
            val failed = attribute?.failed == true
            Text(
                text = if (failed) "정보 인식 실패 · 직접 입력해 주세요" else "알약을 선택해주세요",
                style = RowTitle,
                color = if (failed) NmColor.Error.C600 else colors.textSecondary,
                modifier = Modifier.weight(1f)
            )

            // 정본 카드에는 chevron 이 그려져 있지만, 스펙(§수정·삭제)은 수정·삭제 **둘 다**
            // ⋮ 메뉴로만 들어간다고 못박는다("수정 진입도 동일 ⋮ 메뉴 경유"). 둘을 함께 두면
            // 좁은 행에 서로 다른 동작을 하는 과녁이 두 개 생긴다 — 스펙을 따른다.
            Icon(
                painter = painterResource(R.drawable.nm_ic_more_vertical),
                contentDescription = "메뉴",
                tint = colors.textTertiary,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMenuClick)
                    .padding(5.dp)
            )
        }

        PillAttributeChips(attribute = attribute)
    }
}

/**
 * 하단 고정 — 진행 문구 + '결과 확인'.
 *
 * ⚠️ 활성 조건을 `identified == total` 로만 두면 **알약이 0개일 때도 활성**이 된다
 * (iOS가 그 상태다 — 다 지우면 버튼이 켜지지만 눌러도 아무 일이 없다).
 * `total > 0` 을 함께 본다.
 */
@Composable
private fun ResultFooter(identified: Int, total: Int) {
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
            text = if (done) "모든 알약을 식별했어요" else "알약을 모두 식별해주세요 · $identified/$total",
            style = ProgressLabel,
            color = if (done) NmColor.Primary.C500 else colors.textTertiary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    color = if (done) NmColor.Primary.C500 else NmColor.Neutral.C200,
                    shape = RoundedCornerShape(14.dp)
                ),
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
private val RowTitle = NmTypography.body.copy(fontWeight = FontWeight.SemiBold)
private val NumberBadge = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val BadgeLabel = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
private val ProgressLabel = NmTypography.caption
private val ConfirmLabel = NmTypography.bodyLarge.copy(fontWeight = FontWeight.Bold)
