package app.nursemate.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import app.nursemate.core.designsystem.NmChip
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmListRow
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 홈 화면 — spec/design hero-home-real.png 기준(인사말 · 상태 칩 · 기능 카드).
 *
 * 알약 식별·처치 타이머는 아직 미구현이라 카드를 누르면 [FeaturePreparingScreen]으로 간다.
 * 각 기능이 붙을 때 목적지만 교체하면 된다.
 *
 * @param onActiveTimerClick 상태 칩 → 타이머 리스트. spec/feature/care-timer의 C1 진입점 중 하나다.
 */
@Composable
fun HomeScreen(
    onPillClick: () -> Unit,
    onTimerClick: () -> Unit,
    onActiveTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    val today = remember { LocalDate.now().format(KoreanDateFormat) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .verticalScroll(rememberScrollState())
            .padding(NmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NmSpacing.lg)
    ) {
        Column {
            Text("안녕하세요,", style = NmTypography.heading2, color = colors.textSecondary)
            Text("간호사님", style = NmTypography.heading2, color = colors.textPrimary)
            Text(
                text = today,
                style = NmTypography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = NmSpacing.sm)
            )
        }

        // 타이머 기능 구현 전이라 0 고정. 기능이 붙으면 실제 개수를 넣는다.
        NmChip(
            text = "활성 타이머 0",
            icon = painterResource(DsR.drawable.nm_ic_timer),
            onClick = onActiveTimerClick
        )

        Column(verticalArrangement = Arrangement.spacedBy(NmSpacing.md)) {
            NmListRow(
                icon = painterResource(DsR.drawable.nm_ic_pill),
                title = "알약 식별",
                subtitle = "환자 지참약을 한 번에 식별하고 정보를 확인하세요.",
                iconBackground = NmColor.Primary.C50,
                iconTint = NmColor.Primary.C500,
                // 스펙상 이 자리는 "남은 횟수 n/limit"다(spec/feature/pill-recognition §식별 횟수 제한).
                // 식별 API가 붙기 전까지는 없는 값을 지어내지 않고 상태만 알린다.
                caption = "준비 중",
                modifier = Modifier.fillMaxWidth(),
                onClick = onPillClick
            )
            NmListRow(
                icon = painterResource(DsR.drawable.nm_ic_timer),
                title = "처치 타이머",
                subtitle = "투약 및 처치 시간을 체계적으로 관리하세요.",
                iconBackground = NmColor.Secondary.C50,
                iconTint = NmColor.Secondary.C500,
                caption = "준비 중",
                modifier = Modifier.fillMaxWidth(),
                onClick = onTimerClick
            )
        }
    }
}

/** "8월 27일 목요일" */
private val KoreanDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
