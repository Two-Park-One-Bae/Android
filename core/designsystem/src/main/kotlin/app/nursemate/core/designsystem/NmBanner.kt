package app.nursemate.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 안내 배너 — 정본 `Foundation / Components` §Banner.
 *
 * 아이콘 18 · 간격 10 · 패딩 12×14 · 텍스트 13/500(lineHeight 1.4).
 * 기본은 경고(warning) 톤이다. 정본이 그 색으로 그려져 있다.
 *
 * 쓰는 곳: 홈 식별 한도 소진 안내, ⑩-g 허가 종료 안내.
 *
 * @param tone 색 조합. 정본에 있는 건 [NmBannerTone.Warning] 뿐이고, 나머지는
 *             같은 램프 규칙(50 배경 · 100 테두리 · 600 아이콘 · 700 텍스트)을 따른다.
 */
@Composable
fun NmBanner(text: String, painter: Painter, modifier: Modifier = Modifier, tone: NmBannerTone = NmBannerTone.Warning) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tone.container, SHAPE)
            .border(1.dp, tone.outline, SHAPE)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tone.icon,
            modifier = Modifier.size(18.dp)
        )
        Text(text = text, style = BannerText, color = tone.text)
    }
}

/**
 * 배너 색 조합.
 *
 * 램프에서 **50 배경 · 100 테두리 · 600 아이콘 · 700 텍스트**를 뽑는 규칙이다.
 * 정본의 warning 배너가 정확히 그 조합이라, 다른 톤도 같은 규칙으로 맞춘다.
 */
enum class NmBannerTone(val container: Color, val outline: Color, val icon: Color, val text: Color) {
    Warning(NmColor.Warning.C50, NmColor.Warning.C100, NmColor.Warning.C600, NmColor.Warning.C700),
    Error(NmColor.Error.C50, NmColor.Error.C100, NmColor.Error.C600, NmColor.Error.C700),
    Info(NmColor.Info.C50, NmColor.Info.C100, NmColor.Info.C600, NmColor.Info.C700),
    Success(NmColor.Success.C50, NmColor.Success.C100, NmColor.Success.C600, NmColor.Success.C700)
}

private val SHAPE = RoundedCornerShape(NmRadius.md)

// 정본 13/500 lineHeight 1.4. 스케일에 13 이 없어 여기서 명시한다.
private val BannerText = NmTypography.body.copy(
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.Medium
)
