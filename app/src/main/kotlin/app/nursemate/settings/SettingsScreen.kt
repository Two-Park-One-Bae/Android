package app.nursemate.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.nursemate.core.model.AlertMode
import app.nursemate.timer.ALERT_MODE_NOTICE
import app.nursemate.timer.AlertModePicker
import app.nursemate.ui.SystemBarIcons

/**
 * 설정 — 디자인 `설정 — 타이머 섹션 (NM-308)` · `설정 — 약관 및 동의 행 (NM-548)`.
 *
 * ## 두 항목 모두 확인을 거친다
 * 로그아웃도 되돌리려면 다시 로그인해야 하고, 탈퇴는 아예 복구가 없다.
 * 스펙(`spec/feature/auth/README.md` §로그아웃·탈퇴)이 둘 다 확인 다이얼로그를 요구한다.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onConfirm: (SettingsConfirm) -> Unit,
    alertMode: AlertMode,
    onAlertMode: (AlertMode) -> Unit,
    onOpenConsents: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text(text = "타이머", style = SectionLabel, color = colors.textSecondary)

                AlertModeCard(selected = alertMode, onSelect = onAlertMode)

                Text(
                    text = ALERT_MODE_NOTICE,
                    style = CaptionStyle,
                    color = colors.textTertiary,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "약관", style = SectionLabel, color = colors.textSecondary)

                // ⚠️ 이용약관·개인정보처리방침 **링크 행을 따로 두지 않는다**(spec §약관 및 동의).
                //    전문은 이 화면 안의 「보기」로 연다 — 설정에 링크를 또 깔면 같은 문서로 가는
                //    길이 둘이 되고, 그중 하나(설정 쪽)에서는 동의를 바꿀 수가 없다.
                NmListRow(
                    icon = painterResource(R.drawable.nm_ic_file_text),
                    title = "약관 및 동의",
                    // 정본이 이 행만 부가 설명을 비워 뒀다.
                    subtitle = null,
                    iconBackground = NmColor.Primary.C50,
                    iconTint = NmColor.Primary.C500,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { if (!state.deleting) onOpenConsents() }
                )

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

/** 울림 방식 카드 — 정본 수치: radius 14 · padding 16 · gap 14. */
@Composable
private fun AlertModeCard(selected: AlertMode, onSelect: (AlertMode) -> Unit) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("울림 방식", style = CardTitle, color = colors.textPrimary)
            Text("타이머가 끝날 때 알리는 방식", style = CaptionStyle, color = colors.textTertiary)
        }
        AlertModePicker(selected = selected, onSelect = onSelect)
    }
}

private val CardShape = RoundedCornerShape(14.dp)
private val CardTitle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val CaptionStyle = NmTypography.caption.copy(
    fontSize = 12.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 17.sp
)
private val SectionLabel = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
private val Notice = NmTypography.body.copy(fontSize = 13.sp)
