package app.nursemate.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
import app.nursemate.detail.PillDetailScreen
import app.nursemate.detail.PillDetailViewModel
import app.nursemate.home.NmTab
import app.nursemate.pill.AttributePhase
import app.nursemate.pill.DetectionPhase
import app.nursemate.pill.HistorySave
import app.nursemate.pill.PillAnalysisFailedScreen
import app.nursemate.pill.PillCandidateViewModel
import app.nursemate.pill.PillCaptureRoute
import app.nursemate.pill.PillEditScreen
import app.nursemate.pill.PillLoadingScreen
import app.nursemate.pill.PillNotFoundScreen
import app.nursemate.pill.PillPreviewScreen
import app.nursemate.pill.PillRecognitionViewModel
import app.nursemate.pill.PillResultScreen
import app.nursemate.pill.changesFrom
import app.nursemate.pill.confirmedUntouched
import app.nursemate.pill.copyPillResult
import app.nursemate.pill.editOf
import app.nursemate.pill.finalPills
import app.nursemate.pill.identificationSummary
import app.nursemate.pill.isManualPill
import app.nursemate.pill.pillId
import app.nursemate.pill.sharePillResultPdf
import app.nursemate.pill.toResultUi
import app.nursemate.review.rememberReviewRequester

/**
 * 알약 탭 — 기록 목록(시작점, NM-555) → 촬영① → 미리보기② → 로딩④ → 식별 결과⑤ / 결과 없음⑥ / 분석 실패⑦.
 * 식별 결과가 최종 결과를 겸한다 — `완료` 가 기록을 남기고 들어온 화면으로 나간다(NM-587).
 * (권한 거부③은 촬영 진입점이 권한 상태를 보고 가른다.) 홈 `사진 찍기` 도 알약 탭으로 와서 촬영에 올린다.
 *
 * ## 셸이 아니라 여기 있는 이유
 * 화면이 여섯이라 [NurseMateApp] 쪽 그래프에 그대로 두면 함수 하나가 앱 전체 라우팅을 떠안는다.
 *
 * ## 탭 바 — 촬영만 뺀다
 * 촬영(뷰파인더)을 뺀 알약 화면은 모두 탭 바를 두른다(spec §화면 공통 · Apple HIG). 뷰파인더는 탭 바에 눌리면
 * 정사각 가이드가 잘리므로 뺀다. 모달 · 시트 · ⋮ 메뉴 · 이미지 비교 뷰어는 창이 따로라 탭 바를 덮는다.
 *
 * 탭 바로 다른 탭에 나가도 하던 식별은 알약 탭 스택에 남는다 — 로딩 중이면 요청은 ViewModel 에서 계속 돌고,
 * 알약 탭으로 돌아오면 결과로 넘어간다. 홈 `사진 찍기` 는 그 식별을 덮지 않고 보여 준다([startCaptureFromHome]).
 *
 * @param tabbed 화면에 알약 탭 바를 두르는 셸(NmNavHost 의 TabRoot)
 */
fun NavGraphBuilder.pillNavGraph(
    navController: NavController,
    tabbed: @Composable (@Composable () -> Unit) -> Unit,
    pillTab: @Composable () -> Unit
) {
    navigation(startDestination = NmRoute.PILL_TAB, route = NmRoute.PILL_GRAPH) {
        // 알약 탭 — 기록 목록. 탭 바 · 한도 팝업이 셸(NmNavHost)에 있어 그리는 일은 셸이 맡는다.
        composable(NmRoute.PILL_TAB) { pillTab() }
        capture(navController, tabbed)
        edit(navController, tabbed)
        detail(navController, tabbed)
        preview(navController, tabbed)
        loading(navController, tabbed)
        result(navController, tabbed)
        notFound(navController, tabbed)
        failed(navController, tabbed)
    }
}

private fun NavGraphBuilder.capture(navController: NavController, tabbed: PillTabbed) = composable(
    route = NmRoute.PILL_CAPTURE,
    arguments = listOf(
        navArgument("from") {
            type = NavType.StringType
            defaultValue = NmRoute.FROM_PILL_TAB
        }
    )
) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val fromHome = entry.cameFromHome()
    // 시스템 뒤로도 닫기와 같다 — 홈에서 왔으면 홈으로(spec §재촬영 · 뒤로).
    BackHandler(enabled = fromHome) { navController.leavePillScreen(fromHome = true) }
    // 구도를 잡는 동안 모델을 미리 연다 — 「이 사진 사용」 뒤 첫 식별의 GPU 준비(약 3초)를 감춘다.
    LaunchedEffect(viewModel) { viewModel.models.prewarm() }
    PillCaptureRoute(
        session = viewModel.analyticsSession,
        onPhotoSelected = { uri ->
            viewModel.selectPhoto(uri)
            navController.navigate(NmRoute.PILL_PREVIEW)
        },
        // 들어온 화면(알약 탭 목록 또는 홈)으로 돌아간다 — spec §재촬영 · 뒤로.
        onClose = { navController.leavePillScreen(fromHome) },
        tabbed = tabbed
    )
}

// 화면과 뷰모델 · 지표 · 후보 조회를 잇는 배선이라 한 자리에 둔다 — 쪼개면 같은 값을 여러 곳으로 넘기게 된다.
@Suppress("LongMethod")
private fun NavGraphBuilder.edit(navController: NavController, tabbed: PillTabbed) = composable(
    route = NmRoute.PILL_EDIT,
    arguments = listOf(navArgument("pillId") { type = NavType.StringType })
) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val candidateViewModel: PillCandidateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val candidates by candidateViewModel.state.collectAsStateWithLifecycle()

    val pillId = entry.arguments?.getString("pillId").orEmpty()
    val edit = state.editOf(pillId)

    val tracking = rememberEditTracking(viewModel.analyticsSession, state, pillId)

    // 확정 없이 나가는 **동작**에서 이탈을 센다. 화면이 사라지는 것으로 판단하면 안 된다 —
    // 세부정보(⑩)로 들어가도 사라지고, pop 때는 엔트리가 DESTROYED 가 되기 전에 이미
    // 컴포저블이 정리된다([EditTracking.reportExit]).
    // 되돌릴 값 · 고른 후보는 ViewModel 이 쥔다 — 탭 바로 다른 탭에 다녀와도 남아야 한다.
    remember(pillId) { viewModel.corrections.originalOf(pillId) }
    var pending by remember(pillId) {
        mutableStateOf(viewModel.corrections.pendingOf(pillId) ?: state.selections[pillId])
    }
    // 뒤로는 취소다 — 고친 것을 버린다(spec §선택). 내비 바 뒤로와 시스템 뒤로가 같은 길을 탄다.
    val cancel: () -> Unit = {
        viewModel.corrections.revert(pillId)
        viewModel.corrections.endEdit(pillId)
        tracking.reportExit(edit)
        navController.popBackStack()
    }
    BackHandler { cancel() }

    val search = { candidateViewModel.search(edit.conditions, edit.faces) }
    // 조건·각인이 바뀌면 후보를 다시 받는다. 스펙이 "입력마다 재호출(실시간)"이다.
    // 조회 실패의 「다시 시도」도 같은 것을 부른다 — 재추출이 아니라 조회라 식별 횟수를
    // 쓰지 않는다(NM-529).
    //
    // ⚠️ **모델값이 아니라 조건을 보낸다** — 모델이 추정한 모양을 보내면 하드 필터가 되어
    //    정답 약이 떨어진다(NM-516). 모델값은 `attributeToken` 으로 정렬에만 쓰인다.
    //
    // 요청에 실리는 값으로만 다시 부른다 — `모름` 처럼 화면에만 남는 값을 바꿔도 요청은 같다.
    LaunchedEffect(edit.conditions, edit.faces.front.toRequest(), edit.faces.back.toRequest()) { search() }
    // 화면 목록을 낸 조회를 이 알약 키로 남긴다 — 학습 라벨의 userInput(NM-571).
    LaunchedEffect(candidateViewModel, pillId) {
        candidateViewModel.answered.collect { viewModel.labels.recordQuery(pillId, it) }
    }

    val index = state.detectedIndexOf(pillId)

    tabbed {
        PillEditScreen(
            number = (index ?: 0) + 1,
            crop = state.cropOf(index),
            manual = pillId.isManualPill,
            attribute = edit.attribute,
            conditions = edit.conditions,
            onConditionsChange = { changed ->
                // 화면은 조건을 한 덩이로 넘겨 주므로 무엇이 바뀌었는지는 여기서 가린다.
                changed.changesFrom(edit.conditions).forEach(tracking::attrEdited)
                viewModel.corrections.updateEdit(pillId, edit.copy(conditions = changed))
            },
            faces = edit.faces,
            reading = state.faceReadings[pillId],
            onFacesChange = { changed ->
                // 각인만이 아니다 — 구분선 · 마크도 이 길로 온다. 무엇이 바뀌었는지 여기서 가린다.
                changed.changesFrom(edit.faces).forEach(tracking::attrEdited)
                viewModel.corrections.updateEdit(pillId, edit.copy(faces = changed))
            },
            candidates = candidates,
            selected = pending,
            onSelect = {
                pending = it
                viewModel.corrections.choose(pillId, it)
            },
            onConfirm = {
                confirmEdit(viewModel, tracking, pillId, pending, candidates.candidates)
                // 「선택」하고 돌아갈 때 그 알약 하나의 라벨을 보낸다(NM-571). 고른 후보가 상태에 들어간 뒤라야 한다.
                viewModel.labels.selected(pillId)
                viewModel.corrections.endEdit(pillId)
                navController.popBackStack()
            },
            onCancel = cancel,
            onDetail = { openDetail(navController, tracking, it) },
            onLoadMore = candidateViewModel::loadMore,
            onRetry = search,
            onRetryLoadMore = candidateViewModel::retryLoadMore,
            onPanelChange = { tracking.openPanel = it },
            onCompare = { tracking.buttonTapped("compare") }
        )
    }
}

private fun NavGraphBuilder.detail(navController: NavController, tabbed: PillTabbed) = composable(
    route = NmRoute.PILL_DETAIL,
    arguments = listOf(
        navArgument("pillCode") { type = NavType.StringType },
        navArgument("revoked") {
            type = NavType.BoolType
            defaultValue = false
        }
    ),
    // 표·미적재·이미지 없음처럼 **특정 품목에서만 나오는 상태**는 후보 목록을 훑어 찾기가
    // 사실상 불가능하다. 코드를 알면 바로 열 수 있게 둔다. 이 스킴의 intent-filter 는
    // **debug 매니페스트에만** 있어 릴리스에서는 밖에서 닿을 수 없다.
    deepLinks = listOf(navDeepLink { uriPattern = "nursemate-debug://pill/detail/{pillCode}" })
) { entry ->
    val viewModel: PillDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 결과에서 후보를 열어 보는 동안에도 각인 · 마크 판독이 이어지게 플로우 모델을 쥔다(NM-636).
    entry.pillViewModel(navController)

    val pillCode = entry.arguments?.getString("pillCode").orEmpty()
    val revoked = entry.arguments?.getBoolean("revoked") == true
    LaunchedEffect(pillCode) { viewModel.load(pillCode, revoked) }

    tabbed {
        PillDetailScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onRetry = viewModel::retry
        )
    }
}

private fun NavGraphBuilder.preview(navController: NavController, tabbed: PillTabbed) =
    composable(NmRoute.PILL_PREVIEW) { entry ->
        val viewModel = entry.pillViewModel(navController)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val usage by viewModel.usage.collectAsStateWithLifecycle()
        val previewReview = rememberReviewRequester()
        tabbed {
            PillPreviewScreen(
                state = state,
                usage = usage,
                onRetake = {
                    viewModel.analyticsSession.buttonTapped(target = "retake", screen = "photo_preview")
                    viewModel.discardPhoto()
                    navController.popBackStack()
                },
                onConfirm = {
                    when {
                        // 한도 0 이면 요청 없이 이 화면 위에 안내한다(spec §세션 중 소진 방어 · 정본 ②-a).
                        viewModel.limit.blocked -> viewModel.limit.show()

                        else -> {
                            // 한 번 돌았던 사진이면(실패 · 한도로 돌아옴) 다시 돌린다. 검출이 끝났으면 서버 요청만 다시 한다.
                            if (state.detection != DetectionPhase.Idle) viewModel.retryDetection()
                            navController.navigate(NmRoute.PILL_LOADING)
                        }
                    }
                },
                limitReached = state.attributes == AttributePhase.LimitReached,
                onDismissLimit = viewModel.limit::dismiss,
                // 한도 안내를 본 날은 스토어 리뷰 묶음 1 로 요청하지 않는다(NM-561).
                onLimitShown = previewReview::limitShown
            )
        }
    }

private fun NavGraphBuilder.loading(navController: NavController, tabbed: PillTabbed) =
    composable(NmRoute.PILL_LOADING) { entry ->
        val viewModel = entry.pillViewModel(navController)
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { viewModel.startDetection() }

        // 끝나면 결과 화면으로 **갈아탄다**(로딩을 스택에서 뺀다). 그냥 쌓으면 결과에서
        // 뒤로 갔을 때 로딩으로 돌아가 추론이 다시 돈다.
        //
        // 검출 0개는 실패가 아니라 정상 결과라 ⑥으로, 모델·추론이 깨진 것만 ⑦로 간다.
        // 로딩 화면은 **온디바이스 검출과 서버 속성 추출을 함께** 덮는다(spec §한도 도달 플로우의 B).
        // 검출만 끝났다고 결과로 보내면 속성 칸이 빈 채로 한 번 그려진다.
        LaunchedEffect(state.detection, state.attributes) {
            val destination = when {
                state.detection == DetectionPhase.Empty -> NmRoute.PILL_NOT_FOUND

                state.detection is DetectionPhase.Failed -> NmRoute.PILL_FAILED

                state.attributes is AttributePhase.Done -> NmRoute.PILL_RESULT

                state.attributes is AttributePhase.Failed -> NmRoute.PILL_FAILED

                // 한도 도달은 화면을 옮기지 않고 이 위에 안내를 덮는다.
                else -> null
            }
            if (destination != null) {
                // 결과 화면으로 **갈아탄다**(로딩을 스택에서 뺀다). 그냥 쌓으면 결과에서
                // 뒤로 갔을 때 로딩으로 돌아가 추론이 다시 돈다.
                navController.navigate(destination) {
                    popUpTo(NmRoute.PILL_LOADING) { inclusive = true }
                }
            }
        }

        // 서버가 429 로 막았다 — 미리보기로 돌아가 거기서 안내한다(정본 ②-a). 사진은 그대로 둔다 —
        // 자정에 한도가 풀리면 같은 사진으로 바로 다시 할 수 있다.
        LaunchedEffect(state.attributes) {
            if (state.attributes == AttributePhase.LimitReached) {
                navController.popBackStack(NmRoute.PILL_PREVIEW, inclusive = false)
            }
        }

        tabbed { PillLoadingScreen(state = state) }
    }

private fun NavGraphBuilder.result(navController: NavController, tabbed: PillTabbed) =
    composable(NmRoute.PILL_RESULT) { entry ->
        val viewModel = entry.pillViewModel(navController)
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val fromHome = remember(entry) { navController.pillFlowFromHome() }

        // 중도이탈 경과시간의 기준점. 여기까지 와야 "식별 세션"이다.
        LaunchedEffect(Unit) { viewModel.analyticsSession.resultShown() }

        // 남겼으면 들어온 화면으로 — 알약 탭이면 방금 남긴 기록이 목록 맨 위에 보인다(spec §완료 · 공유).
        val review = rememberReviewRequester()
        LaunchedEffect(state.save) {
            if (state.save == HistorySave.Saved) {
                viewModel.analyticsSession.completed(state.identificationSummary())
                // 상태를 비우기 전에 읽는다 — 이번 식별을 고치지 않고 골랐는지(스토어 리뷰 묶음 1, NM-561).
                val unmodified = state.confirmedUntouched()
                navController.exitPillFlow(viewModel, fromHome)
                // 들어온 화면으로 돌아온 직후에 검사한다(spec §스토어 리뷰 요청).
                review.identified(unmodified)
            }
        }

        tabbed {
            PillResultScreen(
                ui = state.toResultUi(),
                save = state.save,
                onExit = {
                    viewModel.analyticsSession.sessionExited(state.identificationSummary())
                    navController.exitPillFlow(viewModel, fromHome)
                },
                onEditPill = { navController.navigate(NmRoute.pillEdit(it)) },
                onRemovePill = viewModel.corrections::removePill,
                // 새 키로 수정 화면을 빈 입력으로 연다. 목록에는 확인을 눌러야 들어간다(spec NM-187).
                onAddPill = {
                    viewModel.analyticsSession.buttonTapped(target = "add_pill", screen = "identify_result")
                    navController.navigate(NmRoute.pillEdit(viewModel.corrections.nextManualPillId()))
                },
                onDetail = { candidate ->
                    viewModel.analyticsSession.buttonTapped(target = "pill_detail", screen = "identify_result")
                    navController.navigate(
                        NmRoute.pillDetail(candidate.pillCode, candidate.licenseStatus == LicenseStatus.REVOKED)
                    )
                },
                onCopyText = {
                    viewModel.analyticsSession.shared(method = "copy_text", contentType = "pill_result")
                    context.copyPillResult(state.finalPills())
                },
                onSavePdf = {
                    viewModel.analyticsSession.shared(method = "save_pdf", contentType = "pill_result")
                    context.sharePillResultPdf(state.finalPills())
                },
                onSave = viewModel.history::save,
                // 저장하지 못한 채 끝낸다 — 식별은 마쳤으니 완주로 센다.
                onLeaveUnsaved = {
                    viewModel.analyticsSession.completed(state.identificationSummary())
                    navController.exitPillFlow(viewModel, fromHome)
                },
                onDismissSaveFailure = viewModel.history::dismissFailure
            )
        }
    }

private fun NavGraphBuilder.notFound(navController: NavController, tabbed: PillTabbed) =
    composable(NmRoute.PILL_NOT_FOUND) { entry ->
        val viewModel = entry.pillViewModel(navController)
        val state by viewModel.state.collectAsStateWithLifecycle()
        tabbed {
            PillNotFoundScreen(
                state = state,
                onRetake = {
                    viewModel.analyticsSession.buttonTapped(target = "retake", screen = "not_found")
                    navController.restartCapture(viewModel)
                },
                onGalleryTap = { viewModel.analyticsSession.buttonTapped(target = "gallery", screen = "not_found") },
                onPickFromGallery = { uri ->
                    viewModel.selectPhoto(uri)
                    // 촬영 화면을 거치지 않고 바로 미리보기로. 결과 없음 화면은 스택에서 뺀다.
                    navController.navigate(NmRoute.PILL_PREVIEW) {
                        popUpTo(NmRoute.PILL_CAPTURE) { inclusive = false }
                    }
                },
                // ⑥ 은 결과 화면을 본 적이 없어 세션 이탈로 세지 않는다 — `pill_identify_result`
                // 의 outcome=empty 가 이미 그 시도를 설명한다.
                onExit = { navController.exitPillFlow(viewModel, navController.pillFlowFromHome()) }
            )
        }
    }

private fun NavGraphBuilder.failed(navController: NavController, tabbed: PillTabbed) =
    composable(NmRoute.PILL_FAILED) { entry ->
        val viewModel = entry.pillViewModel(navController)
        val state by viewModel.state.collectAsStateWithLifecycle()
        tabbed {
            PillAnalysisFailedScreen(
                // 검출(모델)과 속성 추출(네트워크) 중 실제로 실패한 쪽의 사유를 보여준다.
                message = (state.detection as? DetectionPhase.Failed)?.message
                    ?: (state.attributes as? AttributePhase.Failed)?.message,
                onRetry = {
                    viewModel.retryDetection()
                    navController.navigate(NmRoute.PILL_LOADING) {
                        popUpTo(NmRoute.PILL_FAILED) { inclusive = true }
                    }
                },
                // 사진은 살려 둔다 — 미리보기에서 '이 사진 사용'을 다시 누를 수 있어야 한다.
                onBack = { navController.popBackStack() }
            )
        }
    }

/** 화면에 알약 탭 바를 두르는 셸. */
internal typealias PillTabbed = @Composable (@Composable () -> Unit) -> Unit
