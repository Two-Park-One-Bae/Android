package app.nursemate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import app.nursemate.home.NmTab
import app.nursemate.pill.PillRecognitionViewModel

/**
 * 알약 플로우가 공유하는 ViewModel.
 *
 * 화면(`entry`)이 아니라 **`pill` 그래프**에 스코프한다. 화면마다 새로 만들면 미리보기에서
 * 고른 사진이 로딩 화면으로 넘어가지 않는다.
 */
@Composable
internal fun NavBackStackEntry.pillViewModel(navController: NavController): PillRecognitionViewModel {
    val graphEntry = remember(this) { navController.getBackStackEntry(NmRoute.PILL_GRAPH) }
    return hiltViewModel(graphEntry)
}

/** 사진을 버리고 촬영 화면으로 되돌아간다. */
internal fun NavController.restartCapture(viewModel: PillRecognitionViewModel) {
    viewModel.discardPhoto()
    popBackStack(NmRoute.PILL_CAPTURE, inclusive = false)
}

/** 알약 플로우를 접고 홈으로 나간다. 촬영이 그래프 시작점이라 단순 pop 으로는 못 나간다. */
internal fun NavController.exitToHome(viewModel: PillRecognitionViewModel) {
    viewModel.discardPhoto()
    switchTab(NmTab.Home)
}
