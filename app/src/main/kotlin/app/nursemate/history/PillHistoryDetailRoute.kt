package app.nursemate.history

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.nursemate.core.model.LicenseStatus
import app.nursemate.navigation.NmRoute
import app.nursemate.pill.SharedPill
import app.nursemate.pill.copyPillShare
import app.nursemate.pill.sharePillPdf
import java.time.LocalDate

/**
 * 기록 상세의 길 — 조회 · 삭제 · 공유 · 세부정보, 그리고 들어온 화면으로 돌아가기.
 *
 * - 없어진 기록: 들어온 화면으로 돌아가 `기록을 찾을 수 없어요` 를 잠깐 띄운다(확인 버튼 없음). 목록 · 홈 카드는
 *   저장소가 다시 불러오게 이미 알렸다
 * - 삭제: 들어온 화면으로 돌아간다
 *
 * @param tabbed 탭 바를 두르는 셸 — 기록 상세도 알약 탭 안의 화면이다(spec §화면 공통)
 */
@Composable
fun PillHistoryDetailRoute(
    navController: NavController,
    identifiedAt: String,
    pillCount: Int,
    tabbed: @Composable (@Composable () -> Unit) -> Unit,
    viewModel: PillHistoryDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val at = remember(identifiedAt) { parseIdentifiedAt(identifiedAt) }
    val time = remember(at) { at?.let { detailTime(it, LocalDate.now(it.zone)) } }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HistoryDetailEvent.NotFound -> {
                    navController.popBackStack()
                    Toast.makeText(context, "기록을 찾을 수 없어요", Toast.LENGTH_SHORT).show()
                }

                HistoryDetailEvent.Deleted -> navController.popBackStack()

                HistoryDetailEvent.DeleteFailed ->
                    Toast.makeText(context, "기록을 삭제하지 못했어요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 공유물에는 그 기록의 식별 날짜를 적는다 — 오늘이 아니다.
    val shareDate = at?.toLocalDate() ?: LocalDate.now()
    fun shared(): List<SharedPill> = (state as? HistoryDetailState.Loaded)?.history?.pills.orEmpty().map {
        SharedPill(it.pillName, it.companyName, it.pillCode)
    }

    tabbed {
        PillHistoryDetailScreen(
            time = time,
            pillCount = pillCount,
            state = state,
            deleting = deleting,
            onBack = { navController.popBackStack() },
            onRetry = viewModel::load,
            onPillDetail = { pill ->
                navController.navigate(NmRoute.pillDetail(pill.pillCode, pill.licenseStatus == LicenseStatus.REVOKED))
            },
            onDelete = viewModel::delete,
            onCopyText = { context.copyPillShare(shared(), shareDate) },
            onSavePdf = { context.sharePillPdf(shared(), shareDate) }
        )
    }
}
