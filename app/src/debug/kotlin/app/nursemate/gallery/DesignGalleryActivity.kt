package app.nursemate.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmBanner
import app.nursemate.core.designsystem.NmBannerTone
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmChip
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmFeatureCard
import app.nursemate.core.designsystem.NmIconButton
import app.nursemate.core.designsystem.NmListRow
import app.nursemate.core.designsystem.NmLoading
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTextField
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.core.designsystem.R as DsR

/**
 * 디자인시스템 컴포넌트 갤러리 — **debug 빌드 전용**.
 *
 * 정본 `Foundation / Components` 의 섹션 구성을 그대로 따라, 컴포넌트를 한 화면에서
 * 눈으로 대조하려고 만들었다. 화면에 올려 보기 전에는 규격이 맞는지 알 수 없다.
 *
 * 실행:
 * ```
 * adb shell am start -n app.nursemate.debug/app.nursemate.gallery.DesignGalleryActivity
 * ```
 *
 * ⚠️ debug 소스셋에만 있다. release APK 에는 클래스 자체가 들어가지 않는다.
 *
 * 아이콘이 chevron 뿐인 건 의도다 — DS 에는 **컴포넌트가 직접 쓰는 아이콘만** 둔다.
 * 기능 전용 아이콘(촬영·탭바·브랜드 로고 등)은 그 기능 PR 에 들어간다. 여기서 보려는 건
 * 아이콘이 아니라 **컴포넌트 규격**이다.
 */
class DesignGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NurseMateTheme { Gallery() } }
    }
}

@Composable
private fun Gallery() {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(NmSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NmSpacing.lg)
    ) {
        Text("Foundation / Components", style = NmTypography.heading2, color = colors.textPrimary)

        Section("Buttons") {
            NmButtonPrimary(text = "Primary", onClick = {}, modifier = Modifier.fillMaxWidth())
            NmButtonSecondary(text = "Secondary", onClick = {}, modifier = Modifier.fillMaxWidth())
        }

        Section("Icon Button — 40×40, 아이콘 22") {
            Row(horizontalArrangement = Arrangement.spacedBy(NmSpacing.sm)) {
                NmIconButton(painterResource(DsR.drawable.nm_ic_chevron_left), "뒤로", {})
                NmIconButton(painterResource(DsR.drawable.nm_ic_chevron_right), "닫기", {})
                NmIconButton(painterResource(DsR.drawable.nm_ic_chevron_right), "플래시", {}, tint = NmColor.Primary.C500)
                NmIconButton(painterResource(DsR.drawable.nm_ic_chevron_right), "갤러리", {}, enabled = false)
            }
        }

        Section("Nav Bar") {
            NmNavBar(title = "알약 촬영", onBack = {})
        }

        Section("Card") {
            NmFeatureCard(
                icon = painterResource(DsR.drawable.nm_ic_chevron_right),
                title = "알약 식별",
                description = "환자 지참약을 한 번에 식별하고 정보를 확인하세요.",
                iconBackground = NmColor.Primary.C50,
                iconTint = NmColor.Primary.C500,
                caption = "오늘 15회 남음",
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
        }

        Section("Chip / Tag") {
            Row(horizontalArrangement = Arrangement.spacedBy(NmSpacing.sm)) {
                NmChip(text = "흰색", icon = painterResource(DsR.drawable.nm_ic_chevron_right))
                NmChip(text = "원형", icon = painterResource(DsR.drawable.nm_ic_chevron_right))
                NmChip(text = "정제", icon = painterResource(DsR.drawable.nm_ic_chevron_right))
            }
        }

        Section("List Row") {
            NmListRow(
                icon = painterResource(DsR.drawable.nm_ic_chevron_right),
                title = "처치 타이머",
                subtitle = "투약 및 처치 시간을 체계적으로 관리하세요.",
                iconBackground = NmColor.Secondary.C50,
                iconTint = NmColor.Secondary.C500,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
        }

        Section("Text Field — 라벨 13/500 · 필드 14×16 · 입력 15") {
            var normal by remember { mutableStateOf("") }
            var filled by remember { mutableStateOf("KD-0123") }
            var error by remember { mutableStateOf("!!") }
            NmTextField(
                value = normal,
                onValueChange = { normal = it },
                label = "각인",
                placeholder = "알약에 새겨진 글자",
                modifier = Modifier.fillMaxWidth()
            )
            NmTextField(
                value = filled,
                onValueChange = { filled = it },
                label = "입력됨 (포커스하면 테두리 primary)",
                modifier = Modifier.fillMaxWidth()
            )
            NmTextField(
                value = error,
                onValueChange = { error = it },
                label = "오류",
                helperText = "영문·숫자만 입력할 수 있어요",
                isError = true,
                modifier = Modifier.fillMaxWidth()
            )
            NmTextField(
                value = "수정할 수 없음",
                onValueChange = {},
                label = "비활성",
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Section("Loading — 스피너 40 · 간격 14 · 라벨 14/500") {
            NmLoading(label = "알약을 찾고 있어요")
            NmLoading()
        }

        Section("Banner — 패딩 12×14 · 아이콘 18 · 텍스트 13/500") {
            NmBanner(
                text = "오늘 식별 횟수를 모두 사용했어요. 내일 0시에 다시 채워져요.",
                painter = painterResource(DsR.drawable.nm_ic_chevron_right)
            )
            NmBanner(
                text = "허가가 종료된 의약품이에요.",
                painter = painterResource(DsR.drawable.nm_ic_chevron_right),
                tone = NmBannerTone.Error
            )
            NmBanner(
                text = "네트워크가 불안정해요.",
                painter = painterResource(DsR.drawable.nm_ic_chevron_right),
                tone = NmBannerTone.Info
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = NmTheme.semanticColors
    Column(verticalArrangement = Arrangement.spacedBy(NmSpacing.sm)) {
        Text(
            text = title,
            style = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textTertiary
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(NmRadius.lg))
                .padding(NmSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NmSpacing.sm),
            horizontalAlignment = Alignment.Start
        ) { content() }
    }
}
