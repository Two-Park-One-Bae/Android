package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/*
 후보가 없을 때의 자리 — 디자인 `⑧-g 후보 0개 (조건 과협)` · `⑧-h 수동 추가 (빈 입력)`.

 왜 비었는지에 따라 할 말이 다르다. 조회해서 0개인 것은 조건이 좁다는 뜻이라 무엇을 풀지
 알려 주고(⑧-g), 아직 아무것도 안 넣은 것은 넣으라고만 한다(⑧-h). 둘을 한 문장으로 합치면
 사용자가 자기 입력이 틀렸다고 오해한다.
*/

@Composable
internal fun CandidateEmpty(state: CandidateUiState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            // 첫 조회 중. 정본에 없는 상태라 인디케이터만 둔다 — 다시 그릴 문구를 지어내지 않는다.
            state.loading -> CircularProgressIndicator(
                color = NmColor.Primary.C500,
                strokeWidth = 2.dp,
                modifier = Modifier.padding(vertical = 40.dp).size(24.dp)
            )

            // 통신 실패도 정본에 없다. ⑧-g 와 같은 틀에 아이콘·문구만 바꿔 얹는다.
            state.failed -> EmptyBlock(
                icon = R.drawable.nm_ic_wifi_off,
                circle = 64.dp,
                iconSize = 30.dp,
                gap = 14.dp,
                padding = 24.dp,
                title = "후보를 불러오지 못했어요",
                subtitle = "잠시 후 다시 시도해 주세요"
            )

            state.searched -> EmptyBlock(
                icon = R.drawable.nm_ic_search_x,
                circle = 64.dp,
                iconSize = 30.dp,
                gap = 14.dp,
                padding = 24.dp,
                title = "조건에 맞는 후보가 없어요",
                subtitle = "색·모양·제형·각인 중 하나를 완화하면 후보가 다시 나타나요"
            )

            else -> EmptyBlock(
                icon = R.drawable.nm_ic_search,
                circle = 56.dp,
                iconSize = 26.dp,
                gap = 10.dp,
                padding = 28.dp,
                title = null,
                subtitle = "속성·각인을 입력하면 후보가 나타나요"
            )
        }
    }
}

/**
 * 원형 아이콘 + 안내.
 *
 * @param title 굵은 한 줄. ⑧-h 처럼 안내만 있는 자리에서는 null 이고, 그때 [subtitle] 은
 *              240 폭에 13/500 으로 그린다(정본이 그 자리만 굵기를 올려 둔다).
 */
@Composable
private fun EmptyBlock(icon: Int, circle: Dp, iconSize: Dp, gap: Dp, padding: Dp, title: String?, subtitle: String) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(circle).background(NmColor.Neutral.C100, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(iconSize)
            )
        }
        Column(
            // ⚠️ 정본은 280 이지만 300 으로 넓혔다. 280 에서는 `하나를 완 / 화하면` 으로 어절
            // 중간이 끊긴다 — 정본 도구와 안드로이드의 글자 폭이 달라 한 글자가 모자란다.
            // 화면이 좁으면 어차피 바깥 폭에 맞춰 줄어들고, 그때는 어절 경계에서 끊긴다.
            modifier = Modifier.width(if (title == null) 240.dp else 300.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title != null) Text(text = title, style = EmptyTitle, color = colors.textPrimary)
            Text(
                text = subtitle,
                style = if (title == null) EmptyHint else EmptyBody,
                color = colors.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val EmptyTitle = NmTypography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)

/**
 * 안내 문구.
 *
 * 한글은 기본 줄바꿈이 글자 단위라 `다시 나타 / 나요` 처럼 어절 중간에서 끊긴다.
 * [LineBreak.WordBreak.Phrase] 를 주면 어절 경계에서 끊는다.
 */
private val EmptyBody = NmTypography.body.copy(
    fontSize = 13.sp,
    lineHeight = 18.2.sp,
    lineBreak = LineBreak.Heading
)
private val EmptyHint = EmptyBody.copy(fontWeight = FontWeight.Medium)
