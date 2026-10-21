package app.nursemate.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors

/** 화면에서 [ReviewRequester] 를 꺼낸다 — 뷰모델을 거칠 일이 아니라 진입점으로 바로 받는다. */
@Composable
fun rememberReviewRequester(): ReviewRequester {
    val app = LocalContext.current.applicationContext
    return remember(app) { EntryPointAccessors.fromApplication(app, ReviewEntryPoint::class.java).reviewRequester() }
}
