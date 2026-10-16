package app.nursemate.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
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
 * 알약 탭 스택에 띄운 화면(촬영 · 기록 상세)을 닫고 **들어온 화면**으로 돌아간다.
 *
 * 알약 화면은 홈에서 들어와도 알약 탭 스택에 쌓인다. 그 **한 화면만** 빼고, 홈에서 왔으면 홈 탭으로 옮긴다 —
 * 남겨 두면 알약 탭을 다시 눌렀을 때 그 화면이 복원된다. 목록까지 되감지 않는다: 그 아래에 하던 식별이
 * 있으면(탭 바로 잠깐 나갔던 것) 그대로 남아야 한다.
 */
internal fun NavController.leavePillScreen(fromHome: Boolean) {
    popBackStack()
    if (fromHome) switchTab(NmTab.Home)
}

/**
 * 홈 `사진 찍기` — 알약 탭으로 옮겨 촬영을 연다.
 *
 * 하던 식별이 있으면(탭 바로 다른 탭에 나갔다 온 것) **새로 시작하지 않고 그 화면을 보인다.** 위에 촬영을
 * 쌓으면 플로우가 공유하는 ViewModel 의 사진 · 결과가 새 사진으로 덮여 하던 것이 말없이 사라진다.
 */
internal fun NavController.startCaptureFromHome() {
    switchTab(NmTab.Pill)
    val inProgress = runCatching { getBackStackEntry(NmRoute.PILL_CAPTURE) }.isSuccess
    if (!inProgress) navigate(NmRoute.pillCapture(fromHome = true))
}

/** 이 항목이 홈에서 들어왔는가 — 경로의 `from` 값. */
internal fun NavBackStackEntry.cameFromHome(): Boolean = arguments?.getString("from") == NmRoute.FROM_HOME

/**
 * 알약 플로우를 **끝내고** 들어온 화면(홈 / 알약 탭)으로 나간다 — 저장 성공 · 저장 없이 나가기 · 이탈 확인 ·
 * 결과 없음의 나가기(spec §완료 · 공유 · §이탈 확인).
 *
 * ⚠️ **스택을 알약 탭 목록까지 되감은 뒤에 나간다.** [switchTab] 은 탭마다 화면 스택을 저장·복원하므로
 * (`saveState`/`restoreState`), 결과 화면을 띄운 채 나가면 알약 탭을 다시 눌렀을 때 목록이
 * 아니라 **그 결과 화면이 그대로 복원된다.** 되감기를 [switchTab] 앞에 두어야 한다 — 저장되는
 * 스택은 `popUpTo { saveState = true }` 가 도는 그 시점의 것이다.
 *
 * 탭 전환 자체가 스택을 보존하는 것은 의도한 동작이다. 사진을 고르다 잠깐 다른 탭에 다녀와도
 * 하던 일이 남아 있어야 한다. 끝냈거나 버린 플로우만 여기서 접는다.
 */
internal fun NavController.exitPillFlow(viewModel: PillRecognitionViewModel, fromHome: Boolean) {
    viewModel.discardPhoto()
    popBackStack(NmRoute.PILL_TAB, inclusive = false)
    if (fromHome) switchTab(NmTab.Home)
}

/**
 * 이 플로우가 홈에서 시작했는가 — 촬영 화면 경로의 `from` 값. 촬영 화면은 결과까지 늘 스택에 남는다
 * (결과 없음에서 갤러리로 다시 골라도 촬영 화면 위로 되감는다).
 */
internal fun NavController.pillFlowFromHome(): Boolean =
    runCatching { getBackStackEntry(NmRoute.PILL_CAPTURE).cameFromHome() }
        // 촬영 화면 없이 결과까지 오는 길이 생기면 드러나게 남긴다 — 그때는 알약 탭으로 돌아간다.
        .onFailure { Log.w("PillNav", "촬영 화면이 스택에 없다 — 알약 탭으로 돌아간다", it) }
        .getOrDefault(false)

/**
 * 후보에서 세부정보로 — **선택과 독립**이다. 고르기 전에 상세를 먼저 확인할 수 있어야 한다.
 *
 * 허가 종료 여부를 함께 넘긴다. 그 품목은 세부정보 조회를 건너뛰고 배지만 보여 주기 때문이다.
 */
internal fun openDetail(navController: NavController, tracking: EditTracking, candidate: PillCandidate) {
    tracking.buttonTapped("pill_detail")
    navController.navigate(
        NmRoute.pillDetail(candidate.pillCode, candidate.licenseStatus == LicenseStatus.REVOKED)
    )
}
