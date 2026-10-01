package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.PillCandidateFace

/**
 * 후보 카드의 **면 요약** — 정본 ②·⑫ 의 후보 행 둘째 줄 (NM-517).
 *
 * ## 왜 품목명만으로는 못 고르나
 * 각인이 같은 약이 수두룩하고 이름은 비슷비슷하다("설트라정" · "셀트라정"). 사용자가
 * 손에 든 알약과 대조할 거리는 **앞뒤에 뭐가 찍혀 있나**다 — 그걸 한 줄에 모아 준다.
 *
 * ## 요청의 면과 반대다
 * 여기 값은 **카탈로그가 말하는 이 약의 생김새**다. null 은 「조건 제외」가 아니라
 * 「그 면에 그게 없다」다([PillCandidateFace]). 비어 있으면 「—」 하나만 적는다.
 */
@Composable
internal fun PillCandidateFaceSummary(front: PillCandidateFace, back: PillCandidateFace) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FaceGroup(label = "앞", face = front)
        Box(modifier = Modifier.width(1.dp).height(10.dp).background(NmColor.Neutral.C300))
        FaceGroup(label = "뒤", face = back)
    }
}

@Composable
private fun FaceGroup(label: String, face: PillCandidateFace) {
    val colors = NmTheme.semanticColors
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = SideLabel, color = colors.textTertiary)

        if (face.isEmpty) {
            Text(text = "—", style = EmptyMark, color = colors.textTertiary)
            return@Row
        }

        face.imprint?.takeIf { it.isNotBlank() }?.let { imprint ->
            Box(modifier = cellModifier().padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = imprint,
                    style = ImprintStyle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        face.dividingLine?.takeIf { it == DividingLine.PLUS || it == DividingLine.MINUS }?.let { line ->
            Box(modifier = cellModifier().width(22.dp), contentAlignment = Alignment.Center) {
                DividingLineGlyph(line)
            }
        }

        // 마크 **그림**은 아직 없다 — 수급 경로와 이름 규칙을 NM-506 이 정한다.
        // 그때까지는 자리만 두어 「이 면에 마크가 있다」는 사실은 전한다.
        if (face.hasMark) {
            Box(modifier = cellModifier().width(22.dp), contentAlignment = Alignment.Center) {
                MarkPlaceholder()
            }
        }
    }
}

/** 각인·구분선·마크가 같은 칸을 쓴다 — 높이 22, r4, `$neutral-100` 에 `$neutral-300` 테두리. */
@Composable
private fun cellModifier(): Modifier {
    val shape = RoundedCornerShape(4.dp)
    return Modifier
        .height(22.dp)
        .clip(shape)
        .background(NmColor.Neutral.C100)
        .border(1.dp, NmColor.Neutral.C300, shape)
}

/**
 * 구분선 그림 — 원판 위에 흰 선.
 *
 * `(+)형` 은 십자, `(−)형` 은 가로선 하나다. 정본은 16 자리에 원 14, 선 9×1.5 다.
 */
@Composable
private fun DividingLineGlyph(line: DividingLine) {
    val colors = NmTheme.semanticColors
    Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(14.dp).background(NmColor.Neutral.C700, CircleShape))
        Box(modifier = Modifier.size(width = 9.dp, height = 1.5.dp).background(colors.surface))
        if (line == DividingLine.PLUS) {
            Box(modifier = Modifier.size(width = 1.5.dp, height = 9.dp).background(colors.surface))
        }
    }
}

/**
 * 마크 자리 — 그림이 들어오기 전까지의 모습.
 *
 * 정본은 여기에 식약처 마크 그림을 넣는다. 자산이 없는 마크의 대체 화면은 두지 않기로
 * 했지만(NM-490), **그림이 하나도 없는 지금** 칸을 통째로 비우면 마크가 있는 약과 없는
 * 약이 똑같아 보인다 — 그게 더 나쁘다. 점 세 개로 「무언가 찍혀 있다」만 말한다.
 */
@Composable
private fun MarkPlaceholder() {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(MARK_DOTS) {
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .offset(y = if (it == 1) (-2).dp else 0.dp)
                    .background(NmColor.Neutral.C500, CircleShape)
            )
        }
    }
}

private const val MARK_DOTS = 3

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val SideLabel = NmTypography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
private val ImprintStyle = NmTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
private val EmptyMark = NmTypography.caption.copy(fontSize = 11.sp)
