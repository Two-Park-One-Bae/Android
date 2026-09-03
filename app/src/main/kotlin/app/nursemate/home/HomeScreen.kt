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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmChip
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmFeatureCard
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.Usage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 홈 화면 — 디자인 정본 `spec/design/DESIGN.pen` 의 `홈 화면` 프레임.
 *
 * Content 패딩 20 · 항목 간격 20. 인사말 / 상태 칩 / 기능 카드 3단이다.
 * 하단 탭바는 [app.nursemate.navigation.NmTabScaffold] 가 두른다 — 이 화면은 그리지 않는다.
 */
@Composable
fun HomeScreen(
    usage: Usage?,
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
            .padding(ContentPadding),
        verticalArrangement = Arrangement.spacedBy(ContentPadding)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("안녕하세요,", style = GreetingStyle, color = colors.textSecondary)
            Text("간호사님", style = GreetingStyle, color = colors.textPrimary)
            Text(
                text = today,
                style = DateStyle,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 타이머 기능 구현 전이라 0 고정. 기능이 붙으면 실제 개수를 넣는다.
        NmChip(
            text = "활성 타이머 0",
            icon = painterResource(DsR.drawable.nm_ic_timer),
            contentColor = NmColor.Secondary.C600,
            containerColor = NmColor.Secondary.C50,
            borderColor = NmColor.Secondary.C100,
            accentColor = NmColor.Secondary.C500,
            onClick = onActiveTimerClick
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NmFeatureCard(
                icon = painterResource(DsR.drawable.nm_ic_pill),
                title = "알약 식별",
                description = "환자 지참약을 한 번에 식별하고 정보를 확인하세요.",
                iconBackground = NmColor.Primary.C50,
                iconTint = NmColor.Primary.C500,
                // 아직 조회 전이면 비워 둔다 — 0/0 같은 값을 지어내면 한도에 걸린 것처럼 보인다.
                caption = usage?.let { "오늘 남은 횟수 ${it.remaining}/${it.limit}" },
                // 소진되면 정본이 색으로 알린다(primary-600 → error-600).
                captionColor = if (usage?.exhausted == true) NmColor.Error.C600 else NmColor.Primary.C600,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPillClick
            )
            NmFeatureCard(
                icon = painterResource(DsR.drawable.nm_ic_timer),
                title = "처치 타이머",
                description = "투약 및 처치 시간을 체계적으로 관리하세요.",
                iconBackground = NmColor.Secondary.C50,
                iconTint = NmColor.Secondary.C500,
                // 디자인상 이 카드에는 캡션이 없다.
                modifier = Modifier.fillMaxWidth(),
                onClick = onTimerClick
            )
        }
    }
}

/** 정본 Content 패딩·간격. spacing 토큰(16/24) 사이 값이라 명시한다. */
private val ContentPadding = 20.dp

/**
 * 인사말 28·**700**.
 *
 * ⚠️ **디자인 파일 안에서 값이 갈린다.** `Foundation / Typography` 의 Type Scale 은
 * Heading 2 를 28·600 으로 정의하는데, `홈 화면` 프레임의 인사말은 28·**700** 이다.
 * 사람이 눈으로 대조하는 건 렌더된 화면이므로 화면 값을 따랐다.
 * 어느 쪽이 맞는지는 디자인 담당 확인이 필요하다 — 정리되면 [NmTypography.heading2] 로 되돌린다.
 */
private val GreetingStyle = NmTypography.heading2.copy(fontWeight = FontWeight.Bold)

/** 정본 13·400. 스케일에 없는 크기라 명시한다. */
private val DateStyle = NmTypography.body.copy(fontSize = 13.sp, lineHeight = 20.sp)

/** "8월 27일 목요일" */
private val KoreanDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
