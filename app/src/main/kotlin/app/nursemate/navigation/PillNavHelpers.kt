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

/**
 * 알약 플로우를 **끝내고** 홈으로 나간다. 촬영이 그래프 시작점이라 단순 pop 으로는 못 나간다.
 *
 * ⚠️ **스택을 촬영까지 되감은 뒤에 나간다.** [switchTab] 은 탭마다 화면 스택을 저장·복원하므로
 * (`saveState`/`restoreState`), 결과 화면을 띄운 채 나가면 알약 탭을 다시 눌렀을 때 카메라가
 * 아니라 **그 결과 화면이 그대로 복원된다.** 되감기를 [switchTab] 앞에 두어야 한다 — 저장되는
 * 스택은 `popUpTo { saveState = true }` 가 도는 그 시점의 것이다.
 *
 * 탭 전환 자체가 스택을 보존하는 것은 의도한 동작이다. 사진을 고르다 잠깐 다른 탭에 다녀와도
 * 하던 일이 남아 있어야 한다. 끝냈거나(⑤ 완료) 버린(⑥ 나가기) 플로우만 여기서 접는다.
 */
internal fun NavController.exitToHome(viewModel: PillRecognitionViewModel) {
    viewModel.discardPhoto()
    popBackStack(NmRoute.PILL_CAPTURE, inclusive = false)
    switchTab(NmTab.Home)
}
