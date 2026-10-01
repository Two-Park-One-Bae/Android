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
 * 라디오 20 · 썸네일 48×28 · 품목명 14/600 + 업체명 11 · **면 요약** · 세부정보 버튼 28.
 *
 * ## 행은 카드가 아니다
 * 정본에서 **목록 전체가 한 덩어리 카드**(`$surface` · r14 · 테두리)이고 행은 아래 구분선으로만
 * 갈린다. 행마다 카드를 두르면 200개가 낱장으로 흩어져 훑기 어렵다 —
 * 껍데기와 구분선은 [candidateSection] 이 그린다.
 *
 * ## 둘째 줄이 면 요약이다 (NM-517)
 * 각인이 같은 약이 수두룩하고 이름도 비슷비슷하다("설트라정" · "셀트라정"). 손에 든 알약과
 * 대조할 거리는 **앞뒤에 뭐가 찍혀 있나**라, 업체명을 품목명 옆으로 올리고 아랫줄을
 * [PillCandidateFaceSummary] 에 내줬다.
 *
 * 과녁이 셋이다 — **카드**는 선택, **썸네일**은 이미지 비교, **ⓘ**는 세부정보.
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
    onThumbnailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            // 고른 행만 바탕으로 알린다. 테두리를 두르면 통짜 카드 안에서 그 줄만 상자가 돼
            // 목록이 끊겨 보인다 — 카드 껍데기는 목록이 쥐고 있다.
            .background(if (selected) NmColor.Primary.C50 else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Radio(selected = selected)

        AsyncImage(
            model = candidate.pillThumbnailUrl,
            contentDescription = null,
            // 정본이 fill 이다. CDN 낱알은 256×140(1.83), 자리는 48×28(1.71)이라 잘려 나가는 게 적다.
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(NmColor.Neutral.C100)
                // 썸네일만 비교 뷰어를 연다 — 카드 탭(선택)·세부정보는 그대로다(spec NM-354).
                .clickable(onClick = onThumbnailClick)
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = candidate.pillName,
                    style = NameStyle,
                    color = colors.textPrimary,
                    // 품목명은 길다("○○정 100밀리그램(염산○○○)"). 줄바꿈을 허용하면 카드마다
                    // 높이가 달라져 목록이 들쭉날쭉해진다 — 정본도 한 줄이다.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 배지가 먼저 잘리지 않게 이름 쪽이 줄어든다.
                    modifier = Modifier.weight(1f, fill = false)
                )
                // 업체명은 **같은 줄**이다(정본 ②) — 동명이약을 가르는 값이라 품목명 옆에 붙어야
                // 한눈에 비교된다. 아랫줄은 면 요약이 가져갔다.
                Text(text = candidate.companyName.short(), style = SubStyle, color = colors.textTertiary)
                if (candidate.licenseStatus == LicenseStatus.REVOKED) RevokedBadge()
            }
            PillCandidateFaceSummary(front = candidate.front, back = candidate.back)
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
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .background(NmColor.Warning.C50, shape)
            .border(1.dp, NmColor.Warning.C100, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        // 경고(warning)지 오류(error)가 아니다 — 고를 수 있는 품목이라 빨강으로 막아 세우지 않는다.
        Text(text = "허가 종료", style = BadgeStyle, color = NmColor.Warning.C700)
    }
}

private val NameStyle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val SubStyle = NmTypography.caption.copy(fontSize = 11.sp)
private val BadgeStyle = NmTypography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium)

/**
 * 업체명은 **4자를 넘으면 말줄임** — 품목명에 자리를 내준다(spec 후보 목록 · iOS 와 같다).
 *
 * 폭이 아니라 **글자 수**로 자른다. 폭으로 두면 기기·글꼴 크기에 따라 어디서 끊길지 달라져
 * 품목명이 먹는 자리도 함께 흔들린다 — 목록을 훑는 동안 줄마다 길이가 들쭉날쭉해진다.
 *
 * ⚠️ `(주)` 도 글자로 센다. 「(주)한국로슈」가 「(주)한…」이 되어 읽히지 않는데, 정본이
 * 접두·접미 처리를 적지 않았고 iOS 도 같다 — 바꾸려면 스펙부터다.
 */
private fun String.short(): String = if (length > COMPANY_MAX) take(COMPANY_MAX) + "…" else this

private const val COMPANY_MAX = 4
