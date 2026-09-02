package app.nursemate.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 상태바·내비게이션 바 **아이콘 색**을 화면마다 정한다.
 *
 * ## iOS와 다른 점
 * iOS는 뷰컨트롤러마다 `preferredStatusBarStyle` 을 선언하면 시스템이 알아서 전환한다.
 * Android는 **창 단위 설정**이라 화면이 바뀔 때마다 직접 바꿔줘야 한다. 안 하면 어두운
 * 카메라 화면 위에 검은 상태바 아이콘이 얹혀 안 보인다.
 *
 * @param darkIcons 밝은 배경 위라면 true(아이콘을 어둡게), 어두운 배경 위라면 false.
 */
@Composable
fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    // 화면을 떠날 때 되돌리지 않는다 — 다음 화면이 자기 값을 선언하므로,
    // 복원과 선언이 겹치면 순서에 따라 엉뚱한 값이 남는다.
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
