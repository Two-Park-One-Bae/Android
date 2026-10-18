package app.nursemate.pill

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmV2BottomBar
import app.nursemate.core.designsystem.v2.NmV2BottomBarButton
import app.nursemate.core.designsystem.v2.NmV2NavBar
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.ImprintSource
import app.nursemate.core.model.PillAttribute
import app.nursemate.core.model.PillCandidate
import app.nursemate.core.model.PillColor
import app.nursemate.core.model.PillConditions
import app.nursemate.ui.SystemBarIcons

/**
 * 수정 · 후보 선택 — 정본 「알약 식별 — 수정 · 후보 선택 (NM-588)」 ① ~ ⑳.
 *
 * 위에 속성 카드([PillEditCard]), 아래에 그 조건으로 조회한 후보 2열 격자([PillCandidateCard]).
 * **조건을 고치면 후보가 곧바로 바뀐다.** 후보를 누르면 파란 테두리로 고르고 하단에 `선택` 이 나타난다
 * (고르기 전에는 없다). 화면 뒤로는 취소다 — 고친 것을 버린다.
 *
 * ## 후보 0개는 오류가 아니다
 * 하드 조건이 좁으면 아무것도 안 걸린다. 그때는 조건을 풀라고 안내한다(spec §후보 영역 상태).
 *
 * ## 허가 종료 품목도 고를 수 있다
 * 지참약이 허가 종료 품목일 수 있어 허가상태는 **판단 보조 정보지 차단 조건이 아니다.** 배지만 달고 서버가
 * 정한 순서(뒤쪽)를 그대로 따른다.
 *
 * @param manual 수동 추가(`알약 추가`) — 사진도 모델 출력도 없다
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun PillEditScreen(
    number: Int,
    manual: Boolean,
    crop: Bitmap?,
    /** 모델이 읽은 값 — **읽기 전용**이다. 칩에 회색으로 비치고 되돌리기의 기준이 된다. */
    attribute: PillAttribute,
    conditions: PillConditions,
    onConditionsChange: (PillConditions) -> Unit,
    faces: FaceInputs,
    onFacesChange: (FaceInputs) -> Unit,
    /** 온디바이스가 읽은 값 — `원래 값` 의 기준이다. 아직 안 읽었으면 null */
    reading: FaceReading?,
    candidates: CandidateUiState,
    selected: PillCandidate?,
    onSelect: (PillCandidate) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDetail: (PillCandidate) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    /** 지금 펼쳐 둔 선택판. 이탈 지표(`pill_flow_exit.editing_attribute`)가 읽는다. */
    onPanelChange: (AttributePanel?) -> Unit = {},
    /** 후보의 `크게 보기` 로 이미지 비교를 열었다 — 지표(`button_tap`)만 쓴다. */
    onCompare: () -> Unit = {}
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    // 외형 메뉴 · 면 메뉴 · 각인 입력 줄 — 한 번에 하나만 연다([EditMenus]).
    val menus = remember { EditMenus() }
    LaunchedEffect(menus.open) { onPanelChange(menus.open) }
    // 치는 동안의 **초안**이다. 글자의 주인은 뷰모델([faces])이고, 확인을 눌러야 넘어간다 —
    // 한 글자마다 넘기면 후보가 글자 수만큼 왕복한다. 커서 자리는 기호를 끼워 넣을 때 쓴다.
    var draft by remember { mutableStateOf(TextFieldValue()) }
    // 이미지 비교 뷰어에 띄울 후보. null 이면 안 열려 있다.
    var comparing by remember { mutableStateOf<PillCandidate?>(null) }

    val focusManager = LocalFocusManager.current
    fun applyFace(side: FaceSide, change: (FaceInput) -> FaceInput) {
        onFacesChange(
            if (side ==
                FaceSide.Front
            ) {
                faces.copy(front = change(faces.front))
            } else {
                faces.copy(back = change(faces.back))
            }
        )
        menus.stopImprint()
    }

    // ⚠️ 포커스만 보고 입력 줄을 띄우면 안 된다. 뒤로 키로 키보드를 내려도 각인 칸은 포커스를 쥔 채라
    //    줄이 화면 아래에 홀로 남는다. 키보드가 실제로 떠 있는지를 함께 본다.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // 뒤로 키로 키보드를 내린 것은 「그만 친다」다. 쳐 둔 글자가 있으면 넘기고, 비었으면 그대로 둔다 —
    // 빈 칸을 「없음」으로 읽으면 사용자가 무심코 하드 조건을 건다(spec §각인 입력).
    LaunchedEffect(imeVisible) {
        val side = menus.editingSide
        if (!imeVisible && side != null) {
            val typed = draft.text.trim()
            if (typed.isEmpty()) {
                menus.stopImprint()
            } else {
                applyFace(side) { it.typed(typed) }
            }
        }
    }

    // 입력 줄이 닫히면 각인 칸의 포커스도 놓는다 — 쥔 채로 두면 키보드가 다시 올라온다.
    LaunchedEffect(menus.editingSide) { if (menus.editingSide == null) focusManager.clearFocus() }

    // 입력 줄의 「원래 값」 — 앞면만 자동 판독이 있다.
    val originalImprint = menus.editingSide?.let { side -> if (side == FaceSide.Front) reading?.imprint else null }
    Box(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .closesMenusOnOutsideDown(menus)
    ) {
        CompositionLocalProvider(LocalMenuAnchor provides menus::anchorAt) {
            Column(
                Modifier
                    .fillMaxSize()
                    // ⚠️ 아래쪽 여백은 **하단 묶음만** 먹는다. 여기서 통째로 먹으면 키보드가 뜰 때 목록까지
                    //    키보드 높이만큼 줄어, 조건을 고치는 동안 후보가 거의 안 보인다.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    )
            ) {
                NmV2NavBar(title = if (manual) "알약 추가" else "알약 $number 수정", onBack = onCancel)

                // LazyColumn 이라야 목록 끝에 닿았는지 알 수 있다 — 이어서 조회의 전제다.
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "card") {
                        PillEditCard(
                            title = if (manual) "새 알약" else "알약 $number",
                            crop = crop,
                            attribute = attribute,
                            conditions = conditions,
                            faces = faces,
                            auto = !manual && !attribute.failed,
                            editingSide = menus.editingSide,
                            open = menus.open,
                            faceMenu = menus.faceMenu,
                            onFaceMenu = menus::face,
                            onToggle = menus::toggle,
                            // 색만 여러 개를 고른다 — 같은 색을 다시 누르면 뺀다.
                            onColorToggle = { color -> onConditionsChange(conditions.toggled(color)) },
                            onChange = onConditionsChange,
                            onFacesChange = onFacesChange,
                            onEditImprint = { side ->
                                val value = (if (side == FaceSide.Front) faces.front else faces.back).imprint.orEmpty()
                                draft = TextFieldValue(value, selection = TextRange(value.length))
                                menus.startImprint(side)
                            }
                        )
                    }
                    item(key = "header") {
                        Text(
                            candidates.headerLabel(),
                            style = NmV2Type.tileTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
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
                            onLoadMore = onLoadMore,
                            onRetry = onRetry,
                            onRetryLoadMore = onRetryLoadMore
                        )
                    )
                }

                // 하단 묶음 — 키보드가 뜨면 그 **위**에 선다. `safeDrawing.only(Bottom)` 이 키보드와
                // 내비게이션 바 중 큰 쪽을 골라 주므로 둘을 따로 더하지 않는다.
                EditBottom(
                    editingSide = menus.editingSide,
                    draft = draft,
                    original = originalImprint,
                    selected = selected != null,
                    onDraftChange = { draft = it },
                    onFace = ::applyFace,
                    onConfirm = onConfirm
                )
            }
        }
    }

    comparing?.let { candidate ->
        // 다이얼로그로 띄워 탭 바와 상태 바까지 덮는다(spec §화면 공통 — 이미지 비교 뷰어는 탭 바를 덮는다).
        Dialog(
            onDismissRequest = { comparing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            PillImageCompare(candidate = candidate, crop = crop, onClose = { comparing = null })
        }
    }
}

/**
 * 하단 묶음 — 키보드가 뜨면 그 **위**에 선다. `safeDrawing.only(Bottom)` 이 키보드와 내비게이션 바 중 큰 쪽을
 * 골라 주므로 둘을 따로 더하지 않는다.
 *
 * 각인을 치는 중이면 입력 줄 + 기호 바, 후보를 골랐으면 `선택` + 고지, 아니면 고지만.
 *
 * @param original 사진에서 읽은 각인(앞면만) — `원래 값` 의 기준
 * @param onFace 면 하나를 바꾸고 입력 줄을 닫는다
 */
@Suppress("LongParameterList")
@Composable
private fun EditBottom(
    editingSide: FaceSide?,
    draft: TextFieldValue,
    original: String?,
    selected: Boolean,
    onDraftChange: (TextFieldValue) -> Unit,
    onFace: (FaceSide, (FaceInput) -> FaceInput) -> Unit,
    onConfirm: () -> Unit
) {
    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        when {
            editingSide != null -> {
                // 각인 입력 줄 + 기호 바가 키보드 **바로 위**에 쌓인다. 고지는 그동안 내린다.
                PillImprintInputRow(
                    side = editingSide,
                    text = draft,
                    // `원래 값` 은 고쳤을 때만 보인다(spec §각인 입력).
                    original = original?.takeIf { it != draft.text.trim() },
                    onTextChange = onDraftChange,
                    onConfirm = { onFace(editingSide) { it.typed(draft.text.trim()) } },
                    onRevert = {
                        onFace(editingSide) {
                            it.copy(imprint = original, imprintSource = ImprintSource.MODEL, imprintUnknown = false)
                        }
                    },
                    onUnknown = { onFace(editingSide) { it.imprintUnknown() } },
                    onNone = { onFace(editingSide) { it.typed("") } }
                )
                PillSymbolBar(onSymbol = { symbol -> onDraftChange(draft.insert(symbol)) })
            }

            // 후보를 고르면 `선택` 이 나타나고 고지는 그 아래로 간다(spec §면책 고지 ①).
            selected -> NmV2BottomBar {
                NmV2BottomBarButton("선택", onClick = onConfirm)
                Disclaimer()
            }

            else -> Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { Disclaimer() }
        }
    }
}

private fun PillConditions.toggled(color: PillColor) = copy(
    colors = if (color in
        colors
    ) {
        colors - color
    } else {
        colors + color
    }
)

@Composable
private fun Disclaimer() = Text(
    PILL_DISCLAIMER,
    style = NmV2Type.caption,
    color = NmTheme.semanticColors.textTertiary,
    modifier = Modifier.fillMaxWidth()
)

/** 후보 격자 — 비었으면 왜 비었는지 알리고, 있으면 두 장씩 줄을 세우고 목록 끝 표시를 붙인다. */
private fun LazyListScope.candidateSection(
    state: CandidateUiState,
    selected: PillCandidate?,
    actions: CandidateActions
) {
    if (state.candidates.isEmpty()) {
        // 안내는 후보 영역의 가운데에 둔다(spec §후보 영역 상태).
        item(key = "empty") {
            CandidateEmpty(
                state = state,
                onRetry = actions.onRetry,
                modifier = Modifier.fillParentMaxHeight(EMPTY_HEIGHT)
            )
        }
        return
    }
    val rows = state.candidates.chunked(2)
    itemsIndexed(rows, key = { _, row -> row.first().pillCode }) { index, row ->
        // 끝에 닿으면 다음 장을 부른다. 이미 받는 중이거나 **한 번 실패했으면** 뷰모델이 무시한다 —
        // 실패 뒤 자동 재시도는 하지 않는다(NM-529).
        if (index == rows.lastIndex && state.hasMore) {
            LaunchedEffect(row.first().pillCode) { actions.onLoadMore() }
        }
        val second = row.getOrNull(1)
        PillCandidatePair(
            left = { modifier -> CandidateCell(row[0], selected, actions, modifier) },
            right = second?.let { candidate ->
                @Composable { modifier: Modifier -> CandidateCell(candidate, selected, actions, modifier) }
            }
        )
    }
    candidateListTail(state, actions)
}

@Composable
private fun CandidateCell(
    candidate: PillCandidate,
    selected: PillCandidate?,
    actions: CandidateActions,
    modifier: Modifier
) = PillCandidateCard(
    candidate = candidate,
    selected = candidate.pillCode == selected?.pillCode,
    onClick = { actions.onSelect(candidate) },
    onDetail = { actions.onDetail(candidate) },
    onCompare = { actions.onThumbnail(candidate) },
    modifier = modifier
)

/** 목록 **끝** — 이어서 조회 실패 · 받는 중 · 200개+ 안내. 서로 배타적이지 않아 갈라 뒀다. */
private fun LazyListScope.candidateListTail(state: CandidateUiState, actions: CandidateActions) {
    // 이어서 조회가 실패했으면 목록 끝에 한 줄만 둔다 — 보이는 후보는 그대로다(NM-529).
    if (state.loadMoreFailed) {
        item(key = "load-more-failed") {
            CandidateLoadMoreFailed(onRetry = actions.onRetryLoadMore)
        }
    }
    if (state.loadingMore) {
        item(key = "loading-more") {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = NmColor.Primary.C500,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    // 200 에서 잘렸으면 목록 끝에서 한 번 더 알린다 — 끝까지 훑고도 못 찾은 사람에게 할 말이다.
    if (state.truncated && !state.hasMore) {
        item(key = "truncated") {
            Text(
                "찾는 약이 없다면 조건을 더 입력해 주세요",
                style = NmV2Type.cardBody,
                color = NmTheme.semanticColors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

/** 빈 상태가 차지하는 목록 높이 비율 — 그 안 가운데에 둔다. */
private const val EMPTY_HEIGHT = 0.6f

/**
 * 헤더에 적을 말. 개수를 **아는 때만** 적는다 — 모르는 상태에서 0 을 적으면 「조건에 맞는 약이 없다」가 된다.
 *
 * | | |
 * |---|---|
 * | 조회 전 · **조회 실패** | 「후보」 |
 * | 0개 | 「후보 0개」 — 물어봤고 정말 없다 |
 * | 200 에서 잘림 | 「후보 200개+」 |
 */
private fun CandidateUiState.headerLabel(): String = when {
    !searched || failed -> "후보"
    truncated -> "후보 ${ids.size}개+"
    else -> "후보 ${ids.size}개"
}

/**
 * 수정 화면에서 열려 있는 것 — 외형 메뉴([open]) · 면 메뉴([faceMenu]) · 각인 입력 줄([editingSide]).
 *
 * **한 번에 하나만** 연다. 둘이 같이 떠 있으면 메뉴끼리 겹치거나 키보드가 메뉴를 가린다. 하나를 열면 나머지를 닫는
 * 규칙을 여기 한 곳에 둔다.
 *
 * ## 바깥 탭으로 닫는 것은 팝업이 아니라 화면이 한다
 * 메뉴 팝업은 다른 칩으로 한 번에 넘어가려고 포커스를 안 가진다. 그 상태로 팝업이 바깥 탭에 스스로 닫히면 탭 하나가
 * **팝업 닫기와 아래 칩 클릭 둘 다**로 가서, 열린 칩을 다시 누르면 닫혔다가 곧바로 다시 열렸다(2026-10-19 실기기).
 * 시간 창으로 두 번째를 먹는 땜질은 길게 누르면 새고, 일부러 바로 다시 연 탭까지 먹었다.
 *
 * 그래서 팝업은 바깥 탭에 닫히지 않게 두고(`dismissOnClickOutside = false`), 화면이 **터치가 시작될 때마다**
 * [onDown] 으로 알린다. 그 터치가 열린 메뉴의 칩([anchor]) 밖에서 시작됐을 때만 닫는다. 열린 칩 위의 탭은 칩이
 * 자기 [toggle] · [face] 로 한 번만 처리한다 — 탭 하나가 한 가지 일만 한다.
 */
@Stable
internal class EditMenus {
    var open by mutableStateOf<AttributePanel?>(null)
        private set
    var faceMenu by mutableStateOf<FaceMenuKey?>(null)
        private set
    var editingSide by mutableStateOf<FaceSide?>(null)
        private set

    /** 열린 메뉴를 띄운 칩의 창 기준 자리. 그 칩이 그려질 때 [anchorAt] 로 알려 준다. */
    private var anchor: Rect? = null

    /** 수정 화면 자신의 자리 — 터치 좌표를 창 기준으로 바꿀 때 쓴다. 상태가 아니다(다시 그릴 일이 없다). */
    var screen: LayoutCoordinates? = null

    /** 외형 메뉴 — 같은 칩을 다시 누르면 닫는다. */
    fun toggle(panel: AttributePanel) {
        open = panel.takeIf { it != open }
        faceMenu = null
        if (open != null) editingSide = null
    }

    fun face(key: FaceMenuKey?) {
        faceMenu = key
        if (key != null) {
            open = null
            editingSide = null
        }
    }

    /** 열린 메뉴의 칩이 자기 자리를 알린다. */
    fun anchorAt(bounds: Rect) {
        anchor = bounds
    }

    /**
     * 화면 어딘가에서 터치가 시작됐다(창 기준 좌표). 메뉴가 열려 있고 그 칩 밖이면 닫는다 — 바깥 탭 닫기.
     * 팝업 안의 터치는 팝업 창으로 가서 여기 오지 않는다. 각인 입력 줄은 건드리지 않는다(키보드).
     */
    fun onDown(position: Offset) {
        if (open == null && faceMenu == null) return
        if (anchor?.contains(position) == true) return
        open = null
        faceMenu = null
    }

    fun startImprint(side: FaceSide) {
        open = null
        faceMenu = null
        editingSide = side
    }

    fun stopImprint() {
        editingSide = null
    }
}

/**
 * 터치가 시작될 때마다 메뉴에 알린다 — 열린 칩 밖이면 닫는다([EditMenus.onDown]). Initial 단계에서 보기만 하고
 * 먹지 않으므로 아래 칩 · 목록은 그 탭을 그대로 받는다. 화면 좌표는 [EditMenus.screen] 에 두고 창 기준으로 바꾼다.
 */
private fun Modifier.closesMenusOnOutsideDown(menus: EditMenus): Modifier = this
    .onGloballyPositioned { menus.screen = it }
    .pointerInput(menus) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            menus.screen?.let { menus.onDown(it.localToWindow(down.position)) }
        }
    }

/**
 * 열린 메뉴를 띄운 칩이 자기 창 기준 자리를 알리는 곳 — [EditMenus.onDown] 이 그 밖의 터치에만 메뉴를 닫으려고 쓴다.
 * 수정 화면 밖(디자인 갤러리 등)에서는 아무 데도 안 알린다.
 */
internal val LocalMenuAnchor = staticCompositionLocalOf<(Rect) -> Unit> { {} }
