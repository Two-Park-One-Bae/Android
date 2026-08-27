package app.nursemate.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.nursemate.BuildConfig
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR

private const val PRIVACY_URL = "https://www.nursemate.app/privacy/"
private const val SUPPORT_EMAIL = "twoparkonebae@gmail.com"

/** 설정 — 앱 정보와 약관·문의 링크. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .verticalScroll(rememberScrollState())
            .padding(NmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NmSpacing.lg)
    ) {
        Text("설정", style = NmTypography.heading2, color = colors.textPrimary)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(NmRadius.xl))
        ) {
            SettingsRow(
                label = "개인정보처리방침",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri()))
                }
            )
            SettingsRow(
                label = "문의하기",
                value = SUPPORT_EMAIL,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, "mailto:$SUPPORT_EMAIL".toUri())
                    )
                }
            )
            SettingsRow(label = "버전", value = BuildConfig.VERSION_NAME)
        }

        Text(
            text = "널스메이트의 알약 식별 결과는 참고용 보조 정보입니다. " +
                "투약 전 반드시 처방 내용과 약품 라벨을 확인하시고, 최종 판단은 의료진의 확인을 따라 주세요.",
            style = NmTypography.caption,
            color = colors.textTertiary
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String? = null, onClick: (() -> Unit)? = null) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(NmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NmSpacing.sm)
    ) {
        Text(
            text = label,
            style = NmTypography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(text = value, style = NmTypography.body, color = colors.textSecondary)
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(DsR.drawable.nm_ic_chevron_right),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
