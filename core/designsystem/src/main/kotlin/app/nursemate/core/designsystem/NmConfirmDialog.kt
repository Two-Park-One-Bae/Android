package app.nursemate.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 확인 모달 — 디자인 `인증 / 로그아웃 확인` · `인증 / 계정 삭제 확인` · `인증 / 동의 취소 확인`.
 *
 * 폭 300 · radius 16 · 패딩 20/22/20/16 · 버튼 높이 48(r12) · 라벨 15·600.
 * dim 은 `#0F172A99`.
 *
 * ## 확인 버튼 색을 밖에서 받는다
 * 정본이 되돌릴 수 없는 동작(계정 삭제)만 `error-500` 으로 칠하고 나머지는 `primary-500` 이다.
 * 색으로 위험을 알리는 유일한 자리라 기본값에 숨기지 않고 호출부가 정하게 둔다.
 *
 * ## `Dialog` 를 쓰지 않았다
 * 안드로이드 `Dialog` 는 별도 창이라 뒤 화면과 시스템 바 처리가 갈린다. 정본은 같은 화면 위에
 * dim 을 덮는 그림이고, 스크림 탭으로 닫히지 않아야 하는 경우(동의 취소)가 있어 직접 그린다.
 * 닫는 경로는 호출부가 [onDismiss] 로 정한다.
 *
 * ## 단추가 하나뿐인 자리도 있다
 * 약관 변경 안내처럼 **고를 것이 없는 알림**은 되돌릴 길을 둘 이유가 없다 — 「취소」를 두면
 * 동의하지 않고 빠져나가는 길이 있는 것처럼 읽힌다. [dismissLabel] 을 null 로 주면 확인
 * 하나가 줄을 채운다(iOS `DSAlertCardView` 와 같은 모양이다).
 *
 * @param message 없으면 제목만 띄운다. 정본도 로그아웃 확인에는 설명이 없다(그때 간격이 16).
 * @param dismissLabel null 이면 **확인 하나만** 그린다. 그때 [onDismiss] 는 호출되지 않는다
 */
@Composable
fun NmConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmContainer: Color = NmColor.Primary.C500,
    dismissLabel: String? = "취소"
) {
    val colors = NmTheme.semanticColors

    Box(
        modifier = modifier.fillMaxSize().background(NmDim),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(NmRadius.lg))
                .background(colors.surface)
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = NmSpacing.md),
            verticalArrangement = Arrangement.spacedBy(if (message == null) NmSpacing.md else 22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = title, style = DialogTitle, color = colors.textPrimary)
                if (message != null) {
                    Text(text = message, style = NmTypography.caption, color = colors.textSecondary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dismissLabel != null) {
                    DialogButton(
                        label = dismissLabel,
                        container = NmColor.Neutral.C100,
                        content = colors.textPrimary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
                DialogButton(
                    label = confirmLabel,
                    container = confirmContainer,
                    content = NmColor.Neutral.C0,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 모달 버튼 — 높이 48 · radius 12 · 라벨 15·600. DS 버튼(라벨 16, 높이 ~54)과 규격이 다르다. */
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

/** 모달·시트가 배경을 덮는 색. 정본 `#0F172A99`(neutral-900 60%). */
val NmDim = Color(0x990F172A)

private val DialogTitle = NmTypography.bodyLarge.copy(fontWeight = FontWeight.Bold)
private val DialogLabel = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
