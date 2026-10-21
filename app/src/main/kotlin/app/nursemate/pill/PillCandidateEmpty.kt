package app.nursemate.pill

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2Button
import app.nursemate.core.designsystem.v2.NmV2ButtonKind
import app.nursemate.core.designsystem.v2.NmV2Type

/*
 후보 영역 상태 — 정본 ⑭ 조회 중 · ⑮ 후보 0개 · ⑯ 조회 실패 · ⑲ ⑳ 입력 전 (NM-529 · NM-588).
 안내는 후보 영역(헤더 아래부터 면책 고지 위까지)의 가운데에 둔다.

 왜 비었는지에 따라 할 말이 다르다. 조회해서 0개인 것은 조건이 좁다는 뜻이라 무엇을 풀지 알려 주고,
 아직 아무것도 안 넣은 것은 넣으라고만 한다. 둘을 한 문장으로 합치면 사용자가 자기 입력이 틀렸다고 오해한다.
*/

@Composable
internal fun CandidateEmpty(state: CandidateUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        when {
            // 첫 조회 중 — 문구 없이 스피너만(spec §후보 영역 상태). 정본 ⑭ 의 `식별 중...` 은 spec 이 뺀 말이다.
            state.loading -> CircularProgressIndicator(
                color = NmColor.Primary.C500,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )

            // 부제는 인식 실패 · 세부정보 실패와 **같은 문장**이다 — 같은 원인에 다른 말을 하면 다른 문제로 읽는다.
            // 재시도는 식별 횟수를 쓰지 않는다 — 후보 조회는 차감 대상이 아니다.
            state.failed -> StateBlock(
                icon = NmSymbols.WifiOff,
                circle = NmColor.Error.C50,
                tint = NmColor.Error.C500,
                title = "후보를 불러오지 못했어요",
                subtitle = "네트워크 연결을 확인하고 다시 시도해 주세요",
                action = "다시 시도" to onRetry
            )

            state.searched -> StateBlock(
                icon = NmSymbols.SearchOff,
                circle = NmColor.Neutral.C100,
                tint = NmTheme.semanticColors.textTertiary,
                title = "조건에 맞는 후보가 없어요",
                subtitle = "색·모양·제형·각인 중 하나를 완화하면 후보가 다시 나타나요"
            )

            // 입력 전 — 조건도 속성 토큰도 없을 때(수동 추가 · 추출 실패로 처음 들어온 경우).
            else -> Text(
                "속성·각인을 입력하면 후보가 나타나요",
                style = Body,
                color = NmTheme.semanticColors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 아이콘 원 56(아이콘 28) · 제목 15/600 · 부제 13 · (다시 시도). 간격 10. */
@Composable
private fun StateBlock(
    @DrawableRes icon: Int,
    circle: Color,
    tint: Color,
    title: String,
    subtitle: String,
    action: Pair<String, () -> Unit>? = null
) {
    val colors = NmTheme.semanticColors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).background(circle, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Text(
            title,
            style = NmV2Type.tileTitle.copy(fontWeight = NmV2Type.button.fontWeight),
            color = colors.textPrimary
        )
        Text(subtitle, style = Body, color = colors.textSecondary, textAlign = TextAlign.Center)
        action?.let { (label, onClick) ->
            NmV2Button(
                label,
                onClick,
                kind = NmV2ButtonKind.Secondary,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * 이어서 조회가 실패했을 때 **목록 끝**에 붙는 한 줄 — 정본 ⑰ (NM-529).
 *
 * 조회 실패와 다르다. 이미 보이는 후보는 그대로 두고 「다음이 안 왔다」만 말한다 — 여기서 화면을 비우면
 * 사용자가 보던 후보까지 사라진다. 자동으로 다시 시도하지 않는다.
 */
@Composable
internal fun CandidateLoadMoreFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NmRadius.md))
            .background(NmColor.Neutral.C50)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("불러오지 못했어요", style = NmV2Type.cardBody, color = NmTheme.semanticColors.textSecondary)
        Box(
            Modifier
                .clip(RoundedCornerShape(NmRadius.md))
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text("다시 시도", style = NmV2Type.chipConfirmed, color = NmColor.Primary.C600)
        }
    }
}

/** 한글은 기본 줄바꿈이 글자 단위라 어절 중간에서 끊긴다 — [LineBreak.Heading] 으로 어절 경계에서 끊는다. */
private val Body = NmV2Type.cardBody.copy(lineBreak = LineBreak.Heading)
