package app.nursemate.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.ConsentDefinition
import app.nursemate.core.model.ConsentType
import app.nursemate.ui.SystemBarIcons

/**
 * 설정 / 약관 및 동의 — 디자인 `설정 / 약관 및 동의 — 동의함 · 변경 없음` · `… 선택 해제 · 저장 가능`.
 *
 * 약관 전문을 확인하고 **선택 항목의 동의 여부를 바꾸는** 화면이다(NM-548).
 *
 * ## 「전체 동의」 행이 없다
 * 정본이 그렇게 뒀다(spec §약관 및 동의). 여기 두면 필수까지 한 번에 끄는 것처럼 보이는데
 * 필수는 애초에 끌 수 없다 — 할 수 없는 일을 걸어 둔 스위치가 된다.
 *
 * ## 확인 다이얼로그가 없다
 * 철회가 동의보다 번거로우면 안 된다(개인정보 보호법 제38조 제4항). 체크를 풀고 `저장`
 * 한 번이면 끝난다.
 */
@Composable
fun ConsentSettingsScreen(
    state: ConsentSettingsUiState,
    onBack: () -> Unit,
    onToggle: (ConsentType) -> Unit,
    onSave: () -> Unit,
    onOpenPolicy: (ConsentDefinition) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        NmNavBar(title = "약관 및 동의", onBack = onBack)

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NmColor.Primary.C500, strokeWidth = 2.dp)
                }

                state.definitions.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message ?: "약관을 불러오지 못했어요",
                        style = NmTypography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Box(modifier = Modifier.height(16.dp))
                    NmButtonSecondary(text = "다시 시도", onClick = onRetry)
                }

                else -> ConsentCard(
                    state = state,
                    onToggle = onToggle,
                    onOpenPolicy = onOpenPolicy
                )
            }
        }

        // 안내는 목록이 떠 있을 때만 여기 붙는다 — 빈 상태는 위에서 같은 문구를 이미 말한다.
        val notice = state.message.takeIf { state.definitions.isNotEmpty() }
        if (notice != null) {
            Text(
                text = notice,
                style = Notice,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        }

        // 정본 Bottom: 위 12 · 좌우 20 · 아래 34. 아래 34 는 제스처 바 자리까지 포함한 값이라
        // 고정값 대신 navigationBarsPadding 으로 받는다 — 3버튼 내비게이션 기기에서는 더 두껍다.
        Box(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
            NmButtonPrimary(
                text = if (state.saving) "저장 중…" else "저장",
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 정본 수치: surface · radius 14 · padding(가로 8 · 세로 6) · 행 간격 2. */
@Composable
private fun ConsentCard(
    state: ConsentSettingsUiState,
    onToggle: (ConsentType) -> Unit,
    onOpenPolicy: (ConsentDefinition) -> Unit
) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        state.definitions.forEach { definition ->
            ConsentRow(
                definition = definition,
                checked = definition.type in state.checked,
                enabled = !state.saving,
                // 필수는 체크된 채로 굳는다 — 여기서 철회할 수 있는 것은 선택 항목뿐이다.
                locked = definition.required,
                onToggle = { onToggle(definition.type) },
                onOpenPolicy = { onOpenPolicy(definition) }
            )
        }
    }
}

private val Notice = NmTypography.body.copy(fontSize = 13.sp)
