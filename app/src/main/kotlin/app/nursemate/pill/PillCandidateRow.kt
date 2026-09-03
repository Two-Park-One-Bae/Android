package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
import coil3.compose.AsyncImage

/**
 * 후보 한 줄 — 디자인 `⑧ 후보 리스트 / Row`.
 *
 * 라디오 20 · 썸네일 72×38 · 품목명 14/600 + 업체명 12 · 세부정보 버튼 28.
 *
 * ## 허가 종료 배지
 * `REVOKED` 는 취하·취소·유효기간만료·폐업을 묶은 값이라 통칭 '허가 종료'로만 알린다
 * (spec NM-341). **선택을 막지 않는다** — 지참약이 허가 종료 품목일 수 있고, 허가상태는
 * 식별을 막는 조건이 아니라 판단 보조 정보다.
 *
 * ## 썸네일이 없는 품목이 있다
 * 서버는 pillCode 로 URL 을 **항상** 조립해 주지만, 낱알 이미지가 없는 품목은 CDN 이
 * 404 를 준다(NM-347). 그때는 회색 자리만 남긴다 — 깨진 아이콘을 보여주면 오류로 읽힌다.
 */
@Composable
fun PillCandidateRow(
    candidate: PillCandidate,
    selected: Boolean,
    onClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, if (selected) NmColor.Primary.C500 else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Radio(selected = selected)

        AsyncImage(
            model = candidate.pillThumbnailUrl,
            contentDescription = null,
            // 낱알이 잘리면 대조가 안 되므로 채우지 않고 맞춘다.
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 72.dp, height = 38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NmColor.Neutral.C100)
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = candidate.pillName ?: candidate.pillCode,
                style = NameStyle,
                color = colors.textPrimary,
                // 품목명은 길다("○○정 100밀리그램(염산○○○)"). 줄바꿈을 허용하면 카드마다
                // 높이가 달라져 목록이 들쭉날쭉해진다 — 정본도 한 줄이다.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                candidate.companyName?.let {
                    Text(
                        text = it,
                        style = SubStyle,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (candidate.licenseStatus == LicenseStatus.REVOKED) RevokedBadge()
            }
        }

        // 선택과 독립이다 — 고르기 전에 상세를 먼저 확인할 수 있어야 한다(spec §세부정보 조회).
        Icon(
            painter = painterResource(R.drawable.nm_ic_info),
            contentDescription = "세부정보",
            tint = NmColor.Primary.C500,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onDetailClick)
                .padding(4.dp)
        )
    }
}

@Composable
private fun Radio(selected: Boolean) {
    val colors = NmTheme.semanticColors
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) NmColor.Primary.C500 else colors.textTertiary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(modifier = Modifier.size(10.dp).background(NmColor.Primary.C500, CircleShape))
    }
}

/** 통칭 라벨 하나로만 알린다 — 취하·취소·만료·폐업을 구분해 봐야 사용자의 판단이 달라지지 않는다. */
@Composable
private fun RevokedBadge() {
    Box(
        modifier = Modifier
            .background(NmColor.Error.C50, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text = "허가 종료", style = BadgeStyle, color = NmColor.Error.C600)
    }
}

private val NameStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val SubStyle = NmTypography.caption
private val BadgeStyle = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
