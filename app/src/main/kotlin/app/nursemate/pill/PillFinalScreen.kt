package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.PillCandidate
import app.nursemate.ui.SystemBarIcons
import coil3.compose.AsyncImage

/**
 * 최종 결과 — 디자인 `⑨ 최종 결과 리스트`.
 *
 * 확정한 알약만 모아 보여준다. 여기서 고칠 수 있는 것은 없다 — **뒤로** 가면 인식 결과에서
 * 이어서 고칠 수 있고(spec §최종 결과·공유), **완료**면 홈으로 나간다.
 *
 * ## 공유는 텍스트 복사다
 * MVP 는 화면을 유지한 채 클립보드에 넣는 것까지다. 공유 시트를 띄우지 않는다.
 */
@Composable
fun PillFinalScreen(
    pills: List<PillCandidate>,
    onBack: () -> Unit,
    onDetail: (PillCandidate) -> Unit,
    onCopyText: () -> Unit,
    onSavePdf: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    // 공유는 방식을 먼저 고른다 — 텍스트냐 PDF냐.
    var sharing by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        NmNavBar(title = "최종 결과", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.nm_ic_circle_check),
                    contentDescription = null,
                    tint = NmColor.Secondary.C500,
                    modifier = Modifier.size(22.dp)
                )
                Text(text = "총 ${pills.size}개 식별 완료", style = SummaryLabel, color = NmColor.Secondary.C700)
            }

            pills.forEach { pill -> FinalRow(pill = pill, onClick = { onDetail(pill) }) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgApp)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NmButtonSecondary(text = "공유", onClick = { sharing = true }, modifier = Modifier.weight(1f))
                NmButtonPrimary(text = "완료", onClick = onDone, modifier = Modifier.weight(1f))
            }
            Text(
                text = "널스메이트의 알약 식별 결과는 참고용 보조 정보입니다. 투약 전 반드시 처방 내용과 " +
                    "약품 라벨을 확인하시고, 최종 판단은 의료진의 확인을 따라 주세요.",
                style = Disclaimer,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (sharing) {
        PillShareSheet(
            onCopyText = {
                sharing = false
                onCopyText()
            },
            onSavePdf = {
                sharing = false
                onSavePdf()
            },
            onDismiss = { sharing = false }
        )
    }
}

/** 항목 전체가 세부정보로 가는 과녁이다(spec §최종 결과 — "항목 탭 시 세부정보 화면"). */
@Composable
private fun FinalRow(pill: PillCandidate, onClick: () -> Unit) {
    val colors = NmTheme.semanticColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = pill.pillThumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NmColor.Neutral.C100)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = pill.pillName ?: pill.pillCode,
                style = RowName,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            pill.companyName?.let {
                Text(text = it, style = RowCompany, color = colors.textSecondary, maxLines = 1)
            }
        }
        Icon(
            painter = painterResource(R.drawable.nm_ic_info),
            contentDescription = "세부정보",
            tint = NmColor.Primary.C500,
            modifier = Modifier.size(20.dp)
        )
    }
}

private val SummaryLabel = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
private val RowName = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
private val RowCompany = NmTypography.caption
private val Disclaimer = NmTypography.caption.copy(fontSize = 11.sp, lineHeight = 16.5.sp)
