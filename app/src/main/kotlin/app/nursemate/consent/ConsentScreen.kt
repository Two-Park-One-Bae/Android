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
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    var confirmingCancel by remember { mutableStateOf(false) }
    // 뒤로가기로 조용히 빠져나가면 세션은 있는데 동의는 없는 상태가 된다. 취소와 같은 길로 보낸다.
    BackHandler(enabled = !state.submitting) { confirmingCancel = true }

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

        Box(modifier = Modifier.fillMaxSize().background(Dim))

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

        if (confirmingCancel) {
            CancelDialog(
                onDismiss = { confirmingCancel = false },
                onSignOut = {
                    confirmingCancel = false
                    onCancel()
                }
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

/** 취소 확인 — 디자인 `인증 / 동의 취소 확인`. 동의 없이 홈으로 가는 길은 없으므로 결론은 로그아웃이다. */
@Composable
private fun CancelDialog(onDismiss: () -> Unit, onSignOut: () -> Unit) {
    val colors = NmTheme.semanticColors

    Box(
        modifier = Modifier.fillMaxSize().background(Dim),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(NmRadius.lg))
                .background(colors.surface)
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = NmSpacing.md),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "로그인 화면으로 돌아갈까요?", style = DialogTitle, color = colors.textPrimary)
                Text(
                    text = "동의하지 않으면 이용이 제한돼요",
                    style = NmTypography.caption,
                    color = colors.textSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(
                    label = "취소",
                    container = NmColor.Neutral.C100,
                    content = colors.textPrimary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                DialogButton(
                    label = "로그아웃",
                    container = NmColor.Primary.C500,
                    content = NmColor.Neutral.C0,
                    onClick = onSignOut,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 모달 버튼 — 높이 48 · radius 12 · 라벨 15/600.
 *
 * DS 버튼(라벨 16, 패딩으로 높이 ~54)과 규격이 달라 여기서 그린다. 설정의 로그아웃·탈퇴
 * 모달도 같은 규격이라, 그쪽을 만들 때 같으면 DS 로 올린다.
 */
@Composable
private fun DialogButton(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(NmRadius.md)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = DialogLabel, color = content)
    }
}

/** 정본 y=140/844. 로고가 dim 뒤에 비치는 배경이라 정확한 위치가 중요하진 않다. */
private val LogoTop = 140.dp
private val LoadingHeight = 180.dp
private val Dim = Color(0x990F172A)

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val SheetTitle = NmTypography.heading3.copy(fontWeight = FontWeight.Bold)
private val SheetSubtitle = NmTypography.body.copy(fontSize = 13.sp, lineHeight = 20.sp)
private val SheetNotice = NmTypography.body.copy(fontSize = 13.sp)
private val AllLabel = NmTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
private val RequiredBadge = NmTypography.caption.copy(fontWeight = FontWeight.SemiBold)
private val ItemLabel = NmTypography.bodyLarge.copy(fontSize = 15.sp)
private val ViewLabel = NmTypography.body.copy(fontSize = 13.sp)
private val DialogTitle = NmTypography.bodyLarge.copy(fontWeight = FontWeight.Bold)
private val DialogLabel = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
