package app.nursemate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.nursemate.home.NmTab
import app.nursemate.pill.DetectionPhase
import app.nursemate.pill.PillAnalysisFailedScreen
import app.nursemate.pill.PillCaptureRoute
import app.nursemate.pill.PillLoadingScreen
import app.nursemate.pill.PillNotFoundScreen
import app.nursemate.pill.PillPreviewScreen
import app.nursemate.pill.PillRecognitionViewModel
import app.nursemate.pill.PillResultScreen

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

private fun NavGraphBuilder.preview(navController: NavController) = composable(NmRoute.PILL_PREVIEW) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val state by viewModel.state.collectAsStateWithLifecycle()
    PillPreviewScreen(
        state = state,
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
    LaunchedEffect(state.detection) {
        val destination = when (state.detection) {
            is DetectionPhase.Success -> NmRoute.PILL_RESULT
            DetectionPhase.Empty -> NmRoute.PILL_NOT_FOUND
            is DetectionPhase.Failed -> NmRoute.PILL_FAILED
            else -> null
        }
        if (destination != null) {
            navController.navigate(destination) {
                popUpTo(NmRoute.PILL_LOADING) { inclusive = true }
            }
        }
    }

    PillLoadingScreen(state = state)
}

private fun NavGraphBuilder.result(navController: NavController) = composable(NmRoute.PILL_RESULT) { entry ->
    val viewModel = entry.pillViewModel(navController)
    val state by viewModel.state.collectAsStateWithLifecycle()
    PillResultScreen(state = state, onBack = { navController.restartCapture(viewModel) })
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
        message = (state.detection as? DetectionPhase.Failed)?.message,
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

/**
 * 알약 플로우가 공유하는 ViewModel.
 *
 * 화면(`entry`)이 아니라 **`pill` 그래프**에 스코프한다. 화면마다 새로 만들면 미리보기에서
 * 고른 사진이 로딩 화면으로 넘어가지 않는다.
 */
@Composable
private fun NavBackStackEntry.pillViewModel(navController: NavController): PillRecognitionViewModel {
    val graphEntry = remember(this) { navController.getBackStackEntry(NmRoute.PILL_GRAPH) }
    return hiltViewModel(graphEntry)
}

/** 사진을 버리고 촬영 화면으로 되돌아간다. */
private fun NavController.restartCapture(viewModel: PillRecognitionViewModel) {
    viewModel.discardPhoto()
    popBackStack(NmRoute.PILL_CAPTURE, inclusive = false)
}

/** 알약 플로우를 접고 홈으로 나간다. 촬영이 그래프 시작점이라 단순 pop 으로는 못 나간다. */
private fun NavController.exitToHome(viewModel: PillRecognitionViewModel) {
    viewModel.discardPhoto()
    switchTab(NmTab.Home)
}
