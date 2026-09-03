package app.nursemate.navigation

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
import app.nursemate.core.model.LicenseStatus
import app.nursemate.detail.PillDetailScreen
import app.nursemate.detail.PillDetailViewModel
import app.nursemate.home.NmTab
import app.nursemate.pill.AttributePhase
import app.nursemate.pill.DetectionPhase
import app.nursemate.pill.PillAnalysisFailedScreen
import app.nursemate.pill.PillCandidateViewModel
import app.nursemate.pill.PillCaptureRoute
import app.nursemate.pill.PillEditScreen
import app.nursemate.pill.PillFinalScreen
import app.nursemate.pill.PillLimitAlert
import app.nursemate.pill.PillLoadingScreen
import app.nursemate.pill.PillNotFoundScreen
import app.nursemate.pill.PillPreviewScreen
import app.nursemate.pill.PillRecognitionViewModel
import app.nursemate.pill.PillResultScreen
import app.nursemate.pill.copyPillResult
import app.nursemate.pill.editOf
import app.nursemate.pill.finalPills
import app.nursemate.pill.isManualPill
import app.nursemate.pill.pillId

/**
 * 알약 식별 플로우 — 촬영① → 미리보기② → 로딩④ → 결과⑤ / 결과 없음⑥ / 분석 실패⑦.
 * (권한 거부③은 촬영 진입점이 권한 상태를 보고 가른다.)
 *
 * ## 셸이 아니라 여기 있는 이유
 * 화면이 여섯이라 [NurseMateApp] 쪽 그래프에 그대로 두면 함수 하나가 앱 전체 라우팅을 떠안는다.
 *
 * ## 탭바를 두르지 않는다
 * 정본의 ①~⑦ 프레임에 Tab Bar 가 없다. 촬영부터 결과까지 전부 전체화면 플로우다.
 */
fun NavGraphBuilder.pillNavGraph(navController: NavController) {
    navigation(startDestination = NmRoute.PILL_CAPTURE, route = NmRoute.PILL_GRAPH) {
        capture(navController)
        edit(navController)
        detail(navController)
        finalResult(navController)
        preview(navController)
        loading(navController)
        result(navController)
        notFound(navController)
        failed(navController)
    }
}

private fun NavGraphBuilder.capture(navController: NavController) = composable(NmRoute.PILL_CAPTURE) { entry ->
    val viewModel = entry.pillViewModel(navController)
    PillCaptureRoute(
        onPhotoSelected = { uri ->
            viewModel.selectPhoto(uri)
            navController.navigate(NmRoute.PILL_PREVIEW)
        },
        // 촬영이 알약 탭의 첫 화면이라 뒤로 갈 곳이 없다. 닫으면 홈으로 보낸다.
        onClose = { navController.switchTab(NmTab.Home) }
    )
}

private fun NavGraphBuilder.edit(navController: NavController) = composable(
    route = NmRoute.PILL_EDIT,
    arguments = listOf(navArgument("pillId") { type = NavType.StringType })
) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val candidateViewModel: PillCandidateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val candidates by candidateViewModel.state.collectAsStateWithLifecycle()

    val pillId = entry.arguments?.getString("pillId").orEmpty()
    val edit = state.editOf(pillId)

    // 취소하면 진입 시점으로 되돌린다 — 스펙이 "선택·확인 시 갱신, 취소 시 폐기"다.
    // 속성·각인은 후보를 실시간으로 조회해야 해서 고치는 즉시 뷰모델에 들어간다. 그래서
    // 되돌릴 값을 여기서 붙잡아 둔다.
    val original = remember(pillId) { state.editOf(pillId) }

    // 확인 전까지는 화면 안에만 둔다. 취소하고 나가면 결과 카드는 그대로여야 한다.
    var pending by remember(pillId) { mutableStateOf(state.selections[pillId]) }

    // 속성·각인이 바뀌면 후보를 다시 받는다. 스펙이 "입력마다 재호출(실시간)"이다.
    LaunchedEffect(edit) { candidateViewModel.search(edit.attribute, edit.faces) }

    val detected = (state.detection as? DetectionPhase.Success)?.result?.pills.orEmpty()
    val index = state.detection.let { detected.indices.firstOrNull { i -> pillId(i) == pillId } }

    PillEditScreen(
        number = (index ?: 0) + 1,
        crop = index?.let { detected.getOrNull(it)?.crop },
        manual = pillId.isManualPill,
        attribute = edit.attribute,
        onAttributeChange = { viewModel.corrections.updateEdit(pillId, edit.copy(attribute = it)) },
        faces = edit.faces,
        onFacesChange = { viewModel.corrections.updateEdit(pillId, edit.copy(faces = it)) },
        candidates = candidates,
        selected = pending,
        onSelect = { pending = it },
        onConfirm = {
            pending?.let { viewModel.corrections.selectCandidate(pillId, it) }
            // 수동 추가는 여기서 비로소 목록에 들어간다 — 취소하고 나가면 빈 카드가 남지 않는다.
            if (pillId.isManualPill) viewModel.corrections.addManualPill(pillId)
            navController.popBackStack()
        },
        onCancel = {
            viewModel.corrections.updateEdit(pillId, original)
            navController.popBackStack()
        },
        onDetail = { candidate ->
            navController.navigate(
                NmRoute.pillDetail(candidate.pillCode, candidate.licenseStatus == LicenseStatus.REVOKED)
            )
        },
        onLoadMore = candidateViewModel::loadMore
    )
}

private fun NavGraphBuilder.finalResult(navController: NavController) = composable(NmRoute.PILL_FINAL) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 확정한 후보만, 목록에 보이는 순서 그대로 모은다.
    val pills = state.finalPills()

    PillFinalScreen(
        pills = pills,
        // 뒤로 가면 인식 결과에서 이어서 고칠 수 있다(spec §최종 결과·공유).
        onBack = { navController.popBackStack() },
        onDetail = { candidate ->
            navController.navigate(
                NmRoute.pillDetail(candidate.pillCode, candidate.licenseStatus == LicenseStatus.REVOKED)
            )
        },
        onShare = { context.copyPillResult(pills) },
        onDone = { navController.switchTab(NmTab.Home) }
    )
}

private fun NavGraphBuilder.detail(navController: NavController) = composable(
    route = NmRoute.PILL_DETAIL,
    arguments = listOf(
        navArgument("pillCode") { type = NavType.StringType },
        navArgument("revoked") {
            type = NavType.BoolType
            defaultValue = false
        }
    )
) { entry ->
    val viewModel: PillDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pillCode = entry.arguments?.getString("pillCode").orEmpty()
    val revoked = entry.arguments?.getBoolean("revoked") == true
    LaunchedEffect(pillCode) { viewModel.load(pillCode, revoked) }

    PillDetailScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onRetry = viewModel::retry
    )
}

private fun NavGraphBuilder.preview(navController: NavController) = composable(NmRoute.PILL_PREVIEW) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    PillPreviewScreen(
        state = state,
        usage = usage,
        onRetake = {
            viewModel.discardPhoto()
            navController.popBackStack()
        },
        onConfirm = { navController.navigate(NmRoute.PILL_LOADING) }
    )
}

private fun NavGraphBuilder.loading(navController: NavController) = composable(NmRoute.PILL_LOADING) { entry ->
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

    PillLoadingScreen(state = state)

    if (state.attributes == AttributePhase.LimitReached) {
        val usage by viewModel.usage.collectAsStateWithLifecycle()
        PillLimitAlert(
            usage = usage,
            onConfirm = {
                viewModel.discardPhoto()
                navController.switchTab(NmTab.Home)
            }
        )
    }
}

private fun NavGraphBuilder.result(navController: NavController) = composable(NmRoute.PILL_RESULT) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val state by viewModel.state.collectAsStateWithLifecycle()
    PillResultScreen(
        state = state,
        onBack = { navController.restartCapture(viewModel) },
        onRemovePill = viewModel.corrections::removePill,
        onEditPill = { navController.navigate(NmRoute.pillEdit(it)) },
        onConfirm = { navController.navigate(NmRoute.PILL_FINAL) },
        // 새 키로 수정 화면을 빈 입력으로 연다. 목록에는 확인을 눌러야 들어간다(spec NM-187).
        onAddPill = { navController.navigate(NmRoute.pillEdit(viewModel.corrections.nextManualPillId())) }
    )
}

private fun NavGraphBuilder.notFound(navController: NavController) = composable(NmRoute.PILL_NOT_FOUND) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val state by viewModel.state.collectAsStateWithLifecycle()
    PillNotFoundScreen(
        state = state,
        onRetake = { navController.restartCapture(viewModel) },
        onPickFromGallery = { uri ->
            viewModel.selectPhoto(uri)
            // 촬영 화면을 거치지 않고 바로 미리보기로. 결과 없음 화면은 스택에서 뺀다.
            navController.navigate(NmRoute.PILL_PREVIEW) {
                popUpTo(NmRoute.PILL_CAPTURE) { inclusive = false }
            }
        },
        onExit = { navController.exitToHome(viewModel) }
    )
}

private fun NavGraphBuilder.failed(navController: NavController) = composable(NmRoute.PILL_FAILED) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val state by viewModel.state.collectAsStateWithLifecycle()
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
