package app.nursemate.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2ButtonKind
import app.nursemate.core.designsystem.v2.NmV2ListRow
import app.nursemate.core.designsystem.v2.NmV2ModalOverlay
import app.nursemate.core.designsystem.v2.NmV2Surface
import app.nursemate.core.designsystem.v2.NmV2SurfaceStyle
import app.nursemate.core.designsystem.v2.NmV2Type
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

    // 바탕(흰색)은 탭 셸이 칠한다.
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "설정",
                style = NmV2Type.screenTitle,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
            )

            Column(
                // 카드 그림자(아래 14 · 흐림 34)가 잘리지 않게 스크롤 칸 안쪽 여백으로 둔다.
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionLabel("타이머")

                AlertModeCard(selected = alertMode, onSelect = onAlertMode)

                Text(
                    text = ALERT_MODE_NOTICE,
                    style = Caption,
                    color = colors.textTertiary,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("약관")

                // ⚠️ 이용약관·개인정보처리방침 **링크 행을 따로 두지 않는다**(spec §약관 및 동의).
                //    전문은 이 화면 안의 「보기」로 연다 — 설정에 링크를 또 깔면 같은 문서로 가는
                //    길이 둘이 되고, 그중 하나(설정 쪽)에서는 동의를 바꿀 수가 없다.
                NmV2ListRow(
                    title = "약관 및 동의",
                    icon = NmSymbols.Description,
                    iconTint = colors.textSecondary,
                    onClick = { if (!state.deleting) onOpenConsents() }
                )

                SectionLabel("계정")

                NmV2ListRow(
                    title = "로그아웃",
                    subtitle = "언제든 다시 로그인할 수 있어요",
                    icon = NmSymbols.Logout,
                    iconTint = colors.textSecondary,
                    onClick = { if (!state.deleting) onConfirm(SettingsConfirm.SignOut) }
                )

                // 정본이 이 행만 색으로 구분한다 — 되돌릴 수 없는 동작이라 목록에서 먼저 눈에 띄어야 한다.
                NmV2ListRow(
                    title = "계정 삭제",
                    subtitle = "계정과 데이터가 모두 삭제돼요",
                    icon = NmSymbols.PersonRemove,
                    iconTint = NmColor.Error.C600,
                    titleColor = NmColor.Error.C600,
                    onClick = { if (!state.deleting) onConfirm(SettingsConfirm.Delete) }
                )

                // 정본에 오류 자리가 없다. 삭제 실패를 삼킬 수는 없어 섹션 아래 한 줄로만 띄운다.
                if (state.message != null) {
                    Text(
                        text = state.message,
                        style = NmV2Type.cardBody,
                        color = NmColor.Error.C600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text = text, style = NmV2Type.chipConfirmed, color = NmTheme.semanticColors.textSecondary)

/**
 * 설정의 확인 모달 — V2 `Modal`.
 *
 * **화면이 아니라 셸이 그린다.** [SettingsScreen] 안에서 그리면 막이 탭바를 덮지 못해
 * 모달을 띄운 채 탭을 눌러 빠져나갈 수 있다 — `NmTabScaffold` 의 overlay 자리에 넣는다.
 */
@Composable
fun SettingsConfirmDialog(confirm: SettingsConfirm, onConfirmed: () -> Unit, onDismiss: () -> Unit) {
    when (confirm) {
        SettingsConfirm.SignOut -> NmV2ModalOverlay(
            title = "로그아웃할까요?",
            confirmText = "로그아웃",
            onConfirm = onConfirmed,
            dismissText = "취소",
            onDismiss = onDismiss
        )

        SettingsConfirm.Delete -> NmV2ModalOverlay(
            title = "계정을 삭제할까요?",
            message = "삭제하면 되돌릴 수 없어요",
            confirmText = "삭제",
            onConfirm = onConfirmed,
            dismissText = "취소",
            onDismiss = onDismiss,
            confirmKind = NmV2ButtonKind.Destructive
        )
    }
}

/** 확인이 필요한 동작. 둘 다 되돌리기 어려워 바로 실행하지 않는다. */
enum class SettingsConfirm { SignOut, Delete }

/** 울림 방식 카드 — 정본 유리 카드 · 여백 16 · 간격 14. 칸은 볼록이다. */
@Composable
private fun AlertModeCard(selected: AlertMode, onSelect: (AlertMode) -> Unit) {
    val colors = NmTheme.semanticColors
    NmV2Surface(NmV2SurfaceStyle.Card, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("울림 방식", style = CardTitle, color = colors.textPrimary)
                Text("타이머가 끝날 때 알리는 방식", style = NmV2Type.rowSubtitle, color = colors.textTertiary)
            }
            AlertModePicker(selected = selected, onSelect = onSelect, raised = true)
        }
    }
}

/** 정본 14/600. */
private val CardTitle = NmV2Type.menu.copy(fontWeight = FontWeight.SemiBold)

/** 정본 12 · 줄 높이 1.4. */
private val Caption = NmV2Type.rowSubtitle
