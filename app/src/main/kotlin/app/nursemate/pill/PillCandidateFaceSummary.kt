package app.nursemate.pill

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
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
    Row(
        // 칸이 그림투성이라 그냥 두면 읽어 줄 것이 하나도 없다. 줄 전체를 한 문장으로 묶는다.
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "앞면 ${front.spoken()}, 뒷면 ${back.spoken()}"
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        faceCells(label = "앞", face = front)
        Spacer(modifier = Modifier.width(GROUP_GAP))
        Box(modifier = Modifier.width(1.dp).height(10.dp).background(NmColor.Neutral.C300))
        Spacer(modifier = Modifier.width(GROUP_GAP))
        faceCells(label = "뒤", face = back)
    }
}

/**
 * 한 면의 칸들을 **바깥 줄에 바로** 쏟아 넣는다.
 *
 * ## 왜 면마다 묶지 않나
 * 묶으면 `weight` 가 그 묶음 안에서만 돈다 — 앞면 각인이 길 때 앞 묶음이 줄 전체를 먹고
 * **뒷면이 통째로 밀려 나간다.** 규칙은 「구분선·마크 칸은 지키고 넘치면 **각인만** 말줄임」
 * 이라(spec 후보 목록), 각인 둘이 **같은 줄에서** 남는 폭을 나눠야 한다.
 *
 * 그래서 간격도 `spacedBy` 가 아니라 [Spacer] 로 둔다 — 면 안은 3, 면 사이는 6 이다.
 */
@Composable
private fun RowScope.faceCells(label: String, face: PillCandidateFace) {
    val colors = NmTheme.semanticColors
    Text(text = label, style = SideLabel, color = colors.textTertiary)

    if (face.isEmpty) {
        Spacer(modifier = Modifier.width(CELL_GAP))
        Text(text = "—", style = EmptyMark, color = colors.textTertiary)
        return
    }

    face.imprint?.takeIf { it.isNotBlank() }?.let { imprint ->
        Spacer(modifier = Modifier.width(CELL_GAP))
        Box(
            // ⚠️ 줄이 넘치면 **각인만** 줄어든다. 구분선·마크는 22 고정이라 함께 밀리면 통째로
            // 잘려 나가는데, 그 둘은 줄여서 보여 줄 수가 없는 그림이다.
            modifier = cellModifier().weight(1f, fill = false).padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = imprint,
                style = ImprintStyle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // 「없음」도 그린다 — 선 없는 원판이다. 카탈로그가 **없다고 말한 것**과 모르는 것은
    // 다르고, 앞면만 쪼개지는 약을 가릴 때 그 차이가 쓰인다. iOS 도 같다.
    face.dividingLine?.takeIf { it != DividingLine.UNKNOWN }?.let { line ->
        Spacer(modifier = Modifier.width(CELL_GAP))
        Box(modifier = cellModifier().width(22.dp), contentAlignment = Alignment.Center) {
            DividingLineGlyph(line)
        }
    }

    if (face.hasMark) {
        Spacer(modifier = Modifier.width(CELL_GAP))
        Box(
            // 마크 칸만 흰 바탕이다(정본 `$surface`) — 그림이 들어앉을 자리라 회색을 깔면
            // 흑백 마크가 묻힌다.
            modifier = cellModifier(fill = colors.surface).width(22.dp),
            contentAlignment = Alignment.Center
        ) {
            MarkGlyph(markCode = face.markCode)
        }
    }
}

/** 면 안의 칸 사이 3, 면과 면 사이 6 — 정본 값이다. */
private val CELL_GAP = 3.dp
private val GROUP_GAP = 6.dp

/**
 * 각인·구분선·마크가 같은 칸을 쓴다 — 높이 22, r4, `$neutral-300` 테두리.
 *
 * 바탕만 다르다. 각인·구분선은 `$neutral-100`, **마크는 흰색**이다(정본).
 */
@Composable
private fun cellModifier(fill: Color = NmColor.Neutral.C100): Modifier {
    val shape = RoundedCornerShape(4.dp)
    return Modifier
        .height(22.dp)
        .clip(shape)
        .background(fill)
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
 * 식약처 마크 그림 — 없는 코드는 **일반 마크 아이콘**으로 「있음」만 알린다.
 *
 * 데이터가 갱신되면 앱이 모르는 마크가 생긴다. 칸을 비우면 마크가 있는 약과 없는 약이
 * 똑같아 보여, 그게 그림을 못 찾은 것보다 나쁘다(iOS 도 같은 판단이다).
 */
@Composable
private fun MarkGlyph(markCode: String?) {
    val image = PillMarkImages.of(markCode)
    if (image != null) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            // 그림마다 여백이 이미 잘려 있어 가득 채우면 모양이 뭉개진다.
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(18.dp)
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.nm_ic_badge),
            contentDescription = null,
            tint = NmColor.Neutral.C700,
            modifier = Modifier.size(13.dp)
        )
    }
}

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val SideLabel = NmTypography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
private val ImprintStyle = NmTypography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
private val EmptyMark = NmTypography.caption.copy(fontSize = 11.sp)

/** 화면 낭독기가 읽을 말. 그림은 소리가 안 나므로 여기서 말로 옮긴다. */
private fun PillCandidateFace.spoken(): String = buildList {
    imprint?.takeIf { it.isNotBlank() }?.let { add("각인 $it") }
    when (dividingLine) {
        DividingLine.PLUS -> add("십자 구분선")
        DividingLine.MINUS -> add("일자 구분선")
        DividingLine.NONE -> add("구분선 없음")
        DividingLine.UNKNOWN, null -> Unit
    }
    if (hasMark) add("마크 있음")
}.ifEmpty { listOf("정보 없음") }.joinToString(" ")
