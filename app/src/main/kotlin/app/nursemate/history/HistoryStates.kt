package app.nursemate.history

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2Type

/**
 * 빈 상태 · 불러오기 실패 — 회색 원(96) 안 아이콘(44) + 제목, 실패면 `다시 시도`.
 * 알약 탭 목록(정본 ② · ③)과 기록 상세(⑩)가 같은 모양이다.
 */
@Composable
internal fun HistoryStateBlock(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    // 남은 자리 가운데에 둔다 — 정본 ② · ③ · ⑩ 의 「상태」 칸이 세로 가운데 정렬이다.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(96.dp).background(NmColor.Neutral.C100, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = NmColor.Neutral.C400, modifier = Modifier.size(44.dp))
        }
        Text(
            title,
            style = NmV2Type.stateTitle,
            color = NmTheme.semanticColors.textPrimary,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) RetryButton(onRetry)
    }
}

/** `기록을 불러오지 못했어요` · `다시 시도`. */
@Composable
internal fun HistoryLoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) =
    HistoryStateBlock(NmSymbols.CloudOff, "기록을 불러오지 못했어요", modifier, onRetry)

/**
 * `다시 시도` — 옅은 파랑 평면(`primary-50` · 글자 `primary-600` 15/600). 정본 ③ · ⑩ 의 버튼이다.
 * V2.1.0 공용 컴포넌트에 없는 모양이라 여기 둔다.
 */
@Composable
internal fun RetryButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(NmRadius.md)
    Text(
        "다시 시도",
        style = NmV2Type.textButton,
        color = NmColor.Primary.C600,
        modifier = Modifier
            .clip(shape)
            .background(NmColor.Primary.C50, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}
