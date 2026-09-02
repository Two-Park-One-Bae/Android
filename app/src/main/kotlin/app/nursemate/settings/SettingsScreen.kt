package app.nursemate.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmConfirmDialog
import app.nursemate.core.designsystem.NmListRow
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.ui.SystemBarIcons

/**
 * 설정 — 디자인 `설정 — 타이머 섹션 (NM-308)` 의 **계정 섹션만**.
 *
 * ## 타이머 섹션은 여기 없다
 * 정본에는 위쪽에 '울림 방식' 카드가 있지만 그건 처치 타이머(NM-308)에 딸린 설정이다.
 * 기능이 없는데 스위치만 그려 두면 눌러도 아무 일이 없는 UI 가 된다. 타이머가 붙을 때 함께 온다.
 *
 * ## 두 항목 모두 확인을 거친다
 * 로그아웃도 되돌리려면 다시 로그인해야 하고, 탈퇴는 아예 복구가 없다.
 * 스펙(`spec/feature/auth/README.md` §로그아웃·탈퇴)이 둘 다 확인 다이얼로그를 요구한다.
 */
@Composable
fun SettingsScreen(state: SettingsUiState, onConfirm: (SettingsConfirm) -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "설정",
                style = ScreenTitle,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = NmSpacing.sm, bottom = NmSpacing.xs)
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "계정", style = SectionLabel, color = colors.textSecondary)

                NmListRow(
                    icon = painterResource(R.drawable.nm_ic_log_out),
                    title = "로그아웃",
                    subtitle = "언제든 다시 로그인할 수 있어요",
                    iconBackground = NmColor.Primary.C50,
                    iconTint = NmColor.Primary.C500,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { if (!state.deleting) onConfirm(SettingsConfirm.SignOut) }
                )

                // 정본이 이 행만 색으로 구분한다 — 되돌릴 수 없는 동작이라 목록에서 먼저 눈에 띄어야 한다.
                NmListRow(
                    icon = painterResource(R.drawable.nm_ic_user_x),
                    title = "계정 삭제",
                    subtitle = "계정과 데이터가 모두 삭제돼요",
                    iconBackground = NmColor.Error.C50,
                    iconTint = NmColor.Error.C600,
                    modifier = Modifier.fillMaxWidth(),
                    titleColor = NmColor.Error.C600,
                    onClick = { if (!state.deleting) onConfirm(SettingsConfirm.Delete) }
                )

                // 정본에 오류 자리가 없다. 삭제 실패를 삼킬 수는 없어 섹션 아래 한 줄로만 띄운다.
                if (state.message != null) {
                    Text(
                        text = state.message,
                        style = Notice,
                        color = NmColor.Error.C600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 설정의 확인 모달.
 *
 * **화면이 아니라 셸이 그린다.** [SettingsScreen] 안에서 그리면 dim 이 탭바를 덮지 못해
 * 모달을 띄운 채 탭을 눌러 빠져나갈 수 있다 — `NmTabScaffold` 의 overlay 자리에 넣는다.
 */
@Composable
fun SettingsConfirmDialog(confirm: SettingsConfirm, onConfirmed: () -> Unit, onDismiss: () -> Unit) {
    when (confirm) {
        SettingsConfirm.SignOut -> NmConfirmDialog(
            title = "로그아웃할까요?",
            confirmLabel = "로그아웃",
            onConfirm = onConfirmed,
            onDismiss = onDismiss
        )

        SettingsConfirm.Delete -> NmConfirmDialog(
            title = "계정을 삭제할까요?",
            message = "삭제하면 되돌릴 수 없어요",
            confirmLabel = "삭제",
            onConfirm = onConfirmed,
            onDismiss = onDismiss,
            confirmContainer = NmColor.Error.C500
        )
    }
}

/** 확인이 필요한 동작. 둘 다 되돌리기 어려워 바로 실행하지 않는다. */
enum class SettingsConfirm { SignOut, Delete }

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val ScreenTitle = NmTypography.heading2.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
private val SectionLabel = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
private val Notice = NmTypography.body.copy(fontSize = 13.sp)
