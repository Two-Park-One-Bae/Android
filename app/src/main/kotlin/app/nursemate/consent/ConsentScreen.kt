package app.nursemate.consent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmConfirmDialog
import app.nursemate.core.designsystem.NmDim
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.ConsentDefinition
import app.nursemate.core.model.ConsentType
import app.nursemate.ui.SystemBarIcons

/**
 * 동의 온보딩 — 디자인 `인증 / 동의 온보딩`.
 *
 * 로고가 있는 흰 배경 위에 dim 을 깔고 아래에서 시트가 올라온다.
 *
 * ## `ModalBottomSheet` 를 쓰지 않았다
 * 스펙(`spec/feature/auth/README.md` §동의 온보딩)이 "화면에서 가능한 행동은 둘뿐"이라고 못박는다 —
 * **동의하고 계속** 아니면 **취소(로그아웃)**. `ModalBottomSheet` 는 스크림 탭·아래로 스와이프로
 * 닫히는데, 그러면 동의도 로그아웃도 하지 않은 채 빠져나온다. 정본에도 드래그 핸들이 없다.
 * 그래서 시트 모양만 직접 그리고 닫히는 경로를 만들지 않았다. 뒤로가기도 취소 확인으로 보낸다.
 *
 * ## 항목 문구를 앱이 갖고 있지 않다
 * 제목·URL·버전 전부 서버가 준다. 디자인의 "이용약관"·"개인정보처리방침"과 지금은 같은 값이지만,
 * 약관을 개정하면 서버만 바꿔서 반영되어야 한다(스펙: 클라 버전 하드코딩 금지).
 *
 * ## 개정 재동의면 시트 앞에 안내를 세운다
 * 이미 동의하고 쓰던 사람에게 같은 화면이 예고 없이 뜨면 **앱이 동의를 잃어버린 것으로
 * 읽힌다**(spec §개정 재동의). 최초 가입자는 흐름상 동의가 당연한 단계라 안내 없이 곧장 시트다.
 *
 * @param needsReconsent 개정 재동의인가 — **안내를 띄울지만** 정한다. 들여보낼지 말지는
 *   이 화면에 오기 전에 서버의 `onboardingRequired` 가 이미 정했다
 */
@Composable
fun ConsentScreen(
    state: ConsentUiState,
    onToggle: (ConsentType) -> Unit,
    onToggleAll: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onOpenPolicy: (ConsentDefinition) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    needsReconsent: Boolean = false
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    var confirmingCancel by remember { mutableStateOf(false) }
    // 한 번 확인하면 다시 띄우지 않는다. 화면 회전으로 되살아나면 같은 말을 두 번 듣는다.
    var noticeDismissed by rememberSaveable { mutableStateOf(false) }
    val showingNotice = needsReconsent && !noticeDismissed

    // 뒤로가기로 조용히 빠져나가면 세션은 있는데 동의는 없는 상태가 된다. 취소와 같은 길로 보낸다.
    // 안내가 떠 있는 동안에는 삼킨다 — 안내를 건너뛰고 취소 확인이 뜨면 무엇을 묻는지 알 수 없다.
    BackHandler(enabled = !state.submitting) { if (!showingNotice) confirmingCancel = true }

    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        Icon(
            painter = painterResource(DsR.drawable.nm_logo),
            contentDescription = null,
            tint = NmColor.Primary.C500,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = LogoTop)
                .size(width = 140.dp, height = 124.dp)
        )

        Box(modifier = Modifier.fillMaxSize().background(NmDim))

        Sheet(
            state = state,
            colors = colors,
            onToggle = onToggle,
            onToggleAll = onToggleAll,
            onSubmit = onSubmit,
            onCancel = { confirmingCancel = true },
            onOpenPolicy = onOpenPolicy,
            onRetry = onRetry,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 안내가 먼저다. 취소 확인과 겹칠 일은 없다 — 안내가 떠 있는 동안은 시트도 뒤로가기도
        // 닿지 않는다(모달이 화면 전체를 덮는다).
        if (showingNotice) {
            NmConfirmDialog(
                title = "약관이 변경되었어요",
                message = "서비스를 계속 이용하려면 변경된 약관에 다시 동의해 주세요.",
                confirmLabel = "확인",
                // 고를 것이 없는 알림이다 — 「취소」를 두면 동의하지 않고 빠져나가는 길이
                // 있는 것처럼 읽힌다. iOS 도 확인 하나다.
                dismissLabel = null,
                onConfirm = { noticeDismissed = true },
                onDismiss = {}
            )
        }

        if (confirmingCancel) {
            NmConfirmDialog(
                title = "로그인 화면으로 돌아갈까요?",
                message = "동의하지 않으면 이용이 제한돼요",
                confirmLabel = "로그아웃",
                onConfirm = {
                    confirmingCancel = false
                    onCancel()
                },
                onDismiss = { confirmingCancel = false }
            )
        }
    }
}

@Composable
private fun Sheet(
    state: ConsentUiState,
    colors: app.nursemate.core.designsystem.NmSemanticColors,
    onToggle: (ConsentType) -> Unit,
    onToggleAll: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onOpenPolicy: (ConsentDefinition) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = NmRadius.xl, topEnd = NmRadius.xl))
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = NmSpacing.lg, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(NmSpacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "약관 동의", style = SheetTitle, color = colors.textPrimary)
            Text(
                text = "널스메이트 이용을 위해 아래 약관 동의가 필요해요",
                style = SheetSubtitle,
                color = colors.textSecondary,
                modifier = Modifier.width(280.dp)
            )
        }

        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxWidth().height(LoadingHeight),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NmColor.Primary.C500, strokeWidth = 2.dp)
            }

            state.definitions.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().height(LoadingHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.message ?: "약관을 불러오지 못했어요",
                    style = NmTypography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(NmSpacing.md))
                NmButtonSecondary(text = "다시 시도", onClick = onRetry)
            }

            else -> ConsentGroup(
                state = state,
                colors = colors,
                onToggle = onToggle,
                onToggleAll = onToggleAll,
                onOpenPolicy = onOpenPolicy
            )
        }

        // 로딩·오류가 아닐 때만 안내를 띄운다. 위의 빈 상태가 이미 같은 문구를 보여 준다.
        val notice = state.message.takeIf { state.definitions.isNotEmpty() }
        if (notice != null) {
            Text(
                text = notice,
                style = SheetNotice,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(NmSpacing.sm)) {
            NmButtonPrimary(
                text = if (state.submitting) "저장 중…" else "동의하고 계속",
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth()
            )
            NmButtonSecondary(
                text = "취소",
                onClick = onCancel,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConsentGroup(
    state: ConsentUiState,
    colors: app.nursemate.core.designsystem.NmSemanticColors,
    onToggle: (ConsentType) -> Unit,
    onToggleAll: () -> Unit,
    onOpenPolicy: (ConsentDefinition) -> Unit
) {
    val allChecked = state.definitions.isNotEmpty() &&
        state.definitions.all { it.type in state.checked }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.submitting, onClick = onToggleAll)
                .padding(vertical = NmSpacing.md, horizontal = NmSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CheckBox(checked = allChecked, size = 26.dp, radius = NmRadius.sm, mark = 16.dp, colors = colors)
            Text(text = "전체 동의", style = AllLabel, color = colors.textPrimary)
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.definitions.forEach { definition ->
                ConsentRow(
                    definition = definition,
                    checked = definition.type in state.checked,
                    enabled = !state.submitting,
                    colors = colors,
                    onToggle = { onToggle(definition.type) },
                    onOpenPolicy = { onOpenPolicy(definition) }
                )
            }
        }
    }
}

@Composable
private fun ConsentRow(
    definition: ConsentDefinition,
    checked: Boolean,
    enabled: Boolean,
    colors: app.nursemate.core.designsystem.NmSemanticColors,
    onToggle: () -> Unit,
    onOpenPolicy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 체크 토글은 행 전체가 받고, '보기'만 따로 가로챈다. 체크박스만 누르게 하면
            // 22dp 과녁이라 손가락으로 맞추기 어렵다.
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 14.dp, horizontal = NmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CheckBox(checked = checked, size = 22.dp, radius = 7.dp, mark = 14.dp, colors = colors)

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (definition.required) {
                Text(text = "필수", style = RequiredBadge, color = NmColor.Primary.C600)
            }
            // 서버가 준 표시용 항목명. 앱에 문구를 갖고 있지 않다.
            Text(text = definition.title, style = ItemLabel, color = colors.textPrimary)
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(NmRadius.sm))
                .clickable(enabled = enabled, onClick = onOpenPolicy)
                .padding(horizontal = NmSpacing.xs, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = "보기", style = ViewLabel, color = colors.textSecondary)
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_chevron_right),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 체크박스 — DS 컴포넌트 목록에 없어 이 화면에서 만든다. 크기만 두 가지(26·22)라 인자로 받는다. */
@Composable
private fun CheckBox(
    checked: Boolean,
    size: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    mark: androidx.compose.ui.unit.Dp,
    colors: app.nursemate.core.designsystem.NmSemanticColors
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (checked) NmColor.Primary.C500 else colors.surface)
            .let { if (checked) it else it.border(1.5.dp, colors.border, shape) },
        contentAlignment = Alignment.Center
    ) {
        // 미체크 상태에서도 정본은 체크 표시를 두되 배경과 같은 색으로 감춘다.
        // 그리지 않으면 체크할 때 아이콘이 튀어나오는 느낌이라, 색만 바꾼다.
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.nm_ic_check),
                contentDescription = null,
                tint = NmColor.Neutral.C0,
                modifier = Modifier.size(mark)
            )
        }
    }
}

/** 정본 y=140/844. 로고가 dim 뒤에 비치는 배경이라 정확한 위치가 중요하진 않다. */
private val LogoTop = 140.dp
private val LoadingHeight = 180.dp

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val SheetTitle = NmTypography.heading3.copy(fontWeight = FontWeight.Bold)
private val SheetSubtitle = NmTypography.body.copy(fontSize = 13.sp, lineHeight = 20.sp)
private val SheetNotice = NmTypography.body.copy(fontSize = 13.sp)
private val AllLabel = NmTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
private val RequiredBadge = NmTypography.caption.copy(fontWeight = FontWeight.SemiBold)
private val ItemLabel = NmTypography.bodyLarge.copy(fontSize = 15.sp)
private val ViewLabel = NmTypography.body.copy(fontSize = 13.sp)
