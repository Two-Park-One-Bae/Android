package app.nursemate.pill

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidate
import app.nursemate.ui.SystemBarIcons

/**
 * 수정·후보 선택 — 디자인 `⑧-a 진입 (선택 전)`.
 *
 * 위에 속성 카드, 아래에 그 속성으로 조회한 후보. **속성을 고치면 후보가 곧바로 바뀐다.**
 *
 * ## 후보 0개는 오류가 아니다
 * 하드 필터 AND 라 조건이 좁으면 아무것도 안 걸린다. 그때는 조건을 풀라고 안내한다
 * (spec §수정·후보 선택 — NM-246).
 *
 * ## 허가 종료 품목도 고를 수 있다
 * 지참약이 허가 종료 품목일 수 있어 허가상태는 **판단 보조 정보지 차단 조건이 아니다.**
 * 배지만 달고 서버가 정한 순서(뒤쪽)를 그대로 따른다.
 */
@Composable
fun PillEditScreen(
    number: Int,
    manual: Boolean,
    crop: Bitmap?,
    attribute: PillAttribute,
    onAttributeChange: (PillAttribute) -> Unit,
    faces: FaceInputs,
    onFacesChange: (FaceInputs) -> Unit,
    candidates: CandidateUiState,
    selected: PillCandidate?,
    onSelect: (PillCandidate) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDetail: (PillCandidate) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    /** 지금 펼쳐 둔 선택판. 이탈 지표(`pill_flow_exit.editing_attribute`)가 읽는다. */
    onPanelChange: (AttributePanel?) -> Unit = {},
    /** 후보 썸네일을 눌러 이미지 비교를 열었다 — 지표(`button_tap`)만 쓴다. */
    onCompare: () -> Unit = {}
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    // 어느 선택판을 펼쳐 뒀는지는 화면만의 사정이라 뷰모델에 두지 않는다. 회전해도 남게 Saveable.
    //
    // ⚠️ 진입하면 **각인판이 펼쳐진 채로** 시작한다. 정본 ⑧-a 는 접힌 상태를 그리지만,
    // MVP 는 색·모양·제형만 자동이고 **각인은 사람이 직접 넣어야 한다**(spec §로드맵 —
    // 각인 자동은 V1). 접어 두면 이 화면에서 유일하게 해야 할 일이 꺾쇠 뒤에 숨는다.
    // iOS 도 같은 이유로 `openPanel = .imprint` 로 시작한다.
    var open by rememberSaveable { mutableStateOf<AttributePanel?>(AttributePanel.Imprint) }

    // 진입 직후 값(각인)도 알려야 한다 — 아무것도 안 건드리고 나가는 경우가 이탈의 다수다.
    LaunchedEffect(open) { onPanelChange(open) }

    // 각인 칸의 커서 자리는 기호를 끼워 넣을 때 필요해서 TextFieldValue 로 들고 있다.
    // 글자 자체의 주인은 뷰모델([faces])이고 이것은 커서를 얹은 사본이다.
    var frontText by remember { mutableStateOf(TextFieldValue(faces.front.imprint)) }
    var backText by remember { mutableStateOf(TextFieldValue(faces.back.imprint)) }
    var focusedSide by remember { mutableStateOf<FaceSide?>(null) }

    // 이미지 비교 뷰어에 띄울 후보. null 이면 안 열려 있다.
    var comparing by remember { mutableStateOf<PillCandidate?>(null) }

    // ⚠️ 포커스만 보고 기호 바를 띄우면 안 된다. 뒤로 키로 키보드를 내려도 각인 칸은 포커스를
    //    쥔 채라 바가 화면 아래에 홀로 남는다. 키보드가 실제로 떠 있는지를 함께 본다.
    //    (safeDrawing 을 먹은 Column 안이어도 WindowInsets.ime 는 창 원본 값을 준다.)
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusManager = LocalFocusManager.current
    LaunchedEffect(imeVisible) {
        if (!imeVisible) {
            focusedSide = null
            // 포커스까지 풀어야 각인 칸 테두리가 파란 채로 남지 않는다. 키보드를 내린 것은
            // "다 적었다"는 뜻인데 칸만 열려 있으면 아직 입력 중처럼 보인다.
            focusManager.clearFocus()
        }
    }

    // 비교 뷰어는 상태 표시줄까지 덮어야 해서 Column 밖 Box 에 얹는다(정본 Dim 이 전체 화면이다).
    Box(modifier = modifier.fillMaxSize().background(colors.bgApp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            NmNavBar(title = "수정", onBack = onCancel)

            // LazyColumn 이라야 목록 끝에 닿았는지 알 수 있다 — 무한 스크롤의 전제다.
            // verticalScroll Column 안에 넣으면 높이가 무한이라 그 판단을 못 한다.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                // 정본에서 후보 행 사이는 8, 블록(카드·헤더·목록) 사이는 14 다. 좁은 쪽을 기본으로
                // 두고 넓혀야 하는 자리에만 [BlockGap] 을 더한다 — LazyColumn 은 간격을 하나만 받는다.
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    PillEditAttributeCard(
                        number = number,
                        manual = manual,
                        crop = crop,
                        attribute = attribute,
                        faces = faces,
                        open = open,
                        onToggle = { panel ->
                            open = panel.takeIf { it != open }
                            if (open != AttributePanel.Imprint) focusedSide = null
                        },
                        onColorToggle = { color ->
                            val current = attribute.colors.orEmpty()
                            onAttributeChange(
                                attribute.copy(colors = if (color in current) current - color else current + color)
                            )
                        },
                        onTransparentChange = { onAttributeChange(attribute.copy(isTransparent = it)) },
                        onChange = onAttributeChange,
                        imprint = {
                            ImprintPanel(
                                faces = faces,
                                frontText = frontText,
                                backText = backText,
                                onChange = onFacesChange,
                                onTextChange = { side, value ->
                                    if (side == FaceSide.Front) frontText = value else backText = value
                                },
                                onFocus = { side -> focusedSide = side }
                            )
                        }
                    )
                }

                item { CandidateHeader() }

                candidateSection(
                    state = candidates,
                    selected = selected,
                    actions = CandidateActions(
                        onSelect = onSelect,
                        onDetail = onDetail,
                        onThumbnail = {
                            onCompare()
                            comparing = it
                        },
                        onLoadMore = onLoadMore
                    )
                )
            }

            // 후보를 고르면 안내 대신 확인·취소가 뜬다(정본 ⑧-f).
            EditFooter(confirmEnabled = selected != null, onConfirm = onConfirm, onCancel = onCancel)

            val side = focusedSide
            if (side != null && open == AttributePanel.Imprint && imeVisible) {
                PillSymbolBar(
                    onSymbol = { symbol ->
                        val next = (if (side == FaceSide.Front) frontText else backText).insert(symbol)
                        if (side == FaceSide.Front) {
                            frontText = next
                            onFacesChange(faces.copy(front = faces.front.copy(imprint = next.text)))
                        } else {
                            backText = next
                            onFacesChange(faces.copy(back = faces.back.copy(imprint = next.text)))
                        }
                    }
                )
            }
        }

        val compare = comparing
        if (compare != null) {
            // 뒤로 가면 화면이 아니라 뷰어부터 닫는다.
            BackHandler { comparing = null }
            PillImageCompare(candidate = compare, crop = crop, onClose = { comparing = null })
        }
    }
}

/** 후보 헤더 — '후보' + 번개 아이콘 '실시간'. 정본 padding=[4,2,0,2]. */
@Composable
private fun CandidateHeader() {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BlockGap + 4.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "후보", style = SectionTitle, color = colors.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_zap),
                contentDescription = null,
                tint = NmColor.Primary.C500,
                modifier = Modifier.size(13.dp)
            )
            Text(text = "실시간", style = HintLabel, color = NmColor.Primary.C600)
        }
    }
}

/** 후보 목록 — 비었으면 왜 비었는지 알리고, 있으면 행과 다음 장 표시·선택 안내를 낸다. */
private fun LazyListScope.candidateSection(
    state: CandidateUiState,
    selected: PillCandidate?,
    actions: CandidateActions
) {
    if (state.candidates.isEmpty()) {
        item { CandidateEmpty(state, modifier = Modifier.padding(top = BlockGap)) }
        return
    }

    itemsIndexed(state.candidates, key = { _, candidate -> candidate.pillCode }) { index, candidate ->
        // 끝에 닿으면 다음 장을 부른다. 이미 받는 중이면 뷰모델이 무시한다.
        if (index == state.candidates.lastIndex && state.hasMore) {
            LaunchedEffect(candidate.pillCode) { actions.onLoadMore() }
        }
        PillCandidateRow(
            candidate = candidate,
            selected = candidate.pillCode == selected?.pillCode,
            onClick = { actions.onSelect(candidate) },
            onDetailClick = { actions.onDetail(candidate) },
            onThumbnailClick = { actions.onThumbnail(candidate) },
            // 헤더와 첫 행 사이만 블록 간격(14)이고, 행끼리는 8 이다.
            modifier = if (index == 0) Modifier.padding(top = BlockGap) else Modifier
        )
    }

    if (state.loadingMore) {
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = NmColor.Primary.C500,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // 고르고 나면 이 안내가 사라지고 푸터에 확인·취소가 뜬다(정본 ⑧-a → ⑧-f).
    if (selected == null) {
        item {
            Text(
                text = "후보를 선택하면 확인 버튼이 나타나요",
                style = SelectHint,
                color = NmTheme.semanticColors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
        }
    }
}

/**
 * 화면 아래 고정 고지.
 *
 * 정본은 이것을 Content 밖 푸터에 둔다. 목록과 함께 흘려보내면 후보를 훑는 동안 사라져
 * 정작 고를 때 안 보인다.
 */
@Composable
private fun EditFooter(confirmEnabled: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NmTheme.semanticColors.bgApp)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (confirmEnabled) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NmButtonSecondary(text = "취소", onClick = onCancel, modifier = Modifier.weight(1f))
                NmButtonPrimary(text = "확인", onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
        DisclaimerText()
    }
}

@Composable
private fun DisclaimerText() {
    val colors = NmTheme.semanticColors
    Text(
        text = "널스메이트의 알약 식별 결과는 참고용 보조 정보입니다. 투약 전 반드시 처방 내용과 " +
            "약품 라벨을 확인하시고, 최종 판단은 의료진의 확인을 따라 주세요.",
        style = Disclaimer,
        color = colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val SectionTitle = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
private val HintLabel = NmTypography.caption.copy(fontWeight = FontWeight.Medium)
private val SelectHint = NmTypography.body.copy(fontSize = 13.sp)

/** 정본 Content 의 블록 간격 14 에서 목록 간격 8 을 뺀 나머지. */
private val BlockGap = 6.dp

private val Disclaimer = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal)
