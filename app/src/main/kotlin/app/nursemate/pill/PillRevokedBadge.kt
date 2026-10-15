package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.v2.NmV2Color
import app.nursemate.core.designsystem.v2.NmV2Type

/**
 * `허가 종료` 배지 — 살구 평면(`caution-50` 면 · `caution-300` 테두리 · 글자 `caution-700` 10/600).
 * 취하 · 취소 · 유효기간만료 · 폐업을 묶은 통칭 라벨이다(NM-341). V2.1.0 화면이 같이 쓴다 — 지금은 식별 기록이고,
 * 후보 카드(`PillCandidateRow` 의 옛 호박색 배지)와 결과 카드는 그 화면을 V2.1.0 으로 옮길 때(NM-612) 이것으로 바꾼다.
 *
 * 정본은 모서리 6 으로 그렸지만 NM-580 「6 은 8 로 맞춘다」를 따라 8(`radius-sm`)이다.
 */
@Composable
fun PillRevokedBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(NmRadius.sm)
    Text(
        "허가 종료",
        style = NmV2Type.badge,
        color = NmV2Color.Caution.C700,
        maxLines = 1,
        modifier = modifier
            .background(NmV2Color.Caution.C50, shape)
            .border(1.dp, NmV2Color.Caution.C300, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
