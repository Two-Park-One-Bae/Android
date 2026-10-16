package app.nursemate.pill

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.DividingLine
import app.nursemate.core.model.PillCandidateFace

/**
 * 후보 카드의 **면 요약** — 정본 ② `앞` 각인 · 구분선 · 마크 `|` `뒤` 각인 · 구분선 · 마크 (NM-517 · NM-588).
 *
 * ## 왜 품목명만으로는 못 고르나
 * 각인이 같은 약이 수두룩하고 이름은 비슷비슷하다("설트라정" · "셀트라정"). 사용자가 손에 든 알약과 대조할
 * 거리는 **앞뒤에 뭐가 찍혀 있나**다 — 그걸 한 줄에 모은다.
 *
 * ## 넘치면 두 줄로 — 각인은 자르지 않는다
 * 이 화면에서 가장 중요한 단서가 각인이라 말줄임하지 않는다. 한 줄에 안 들어가면 **그 카드만** `앞` / `뒤`
 * 두 줄로 내린다(같은 줄의 짝 카드는 높이를 맞춘다 — [PillCandidateCard]).
 *
 * ## 요청의 면과 반대다
 * 여기 값은 **카탈로그가 말하는 이 약의 생김새**다. null 은 「조건 제외」가 아니라 「그 면에 그게 없다」다
 * ([PillCandidateFace]). 비어 있으면 「—」 하나만 적는다.
 */
@Composable
internal fun PillCandidateFaceSummary(
    front: PillCandidateFace,
    back: PillCandidateFace,
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(
        // 칸이 그림투성이라 그냥 두면 읽어 줄 것이 하나도 없다. 줄 전체를 한 문장으로 묶는다.
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "앞면 ${front.spoken()}, 뒷면 ${back.spoken()}"
        }
    ) { constraints ->
        val oneLine = subcompose(Layout.OneLine) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(GroupGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FaceCells("앞", front)
                Box(Modifier.width(1.dp).height(12.dp).background(NmColor.Neutral.C300))
                FaceCells("뒤", back)
            }
        }.single().measure(Constraints())
        val placeable = if (oneLine.width <= constraints.maxWidth) {
            oneLine
        } else {
            subcompose(Layout.TwoLines) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FaceCells("앞", front)
                    FaceCells("뒤", back)
                }
            }.single().measure(constraints.copy(minWidth = 0))
        }
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private enum class Layout { OneLine, TwoLines }

/** 한 면 — `앞` / `뒤` 글자와 칸들. 칸 사이 3. */
@Composable
private fun FaceCells(label: String, face: PillCandidateFace) {
    val colors = NmTheme.semanticColors
    Row(horizontalArrangement = Arrangement.spacedBy(CellGap), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SideLabel, color = colors.textTertiary)
        if (face.isEmpty) {
            Text("—", style = NmV2Type.caption, color = colors.textTertiary)
            return@Row
        }
        face.imprint?.takeIf { it.isNotBlank() }?.let { imprint ->
            Box(cell().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text(imprint, style = ImprintStyle, color = colors.textPrimary, maxLines = 1, softWrap = false)
            }
        }
        // 「없음」도 그린다 — 선 없는 원판이다. 카탈로그가 **없다고 말한 것**과 모르는 것은 다르다.
        face.dividingLine?.takeIf { it != DividingLine.UNKNOWN }?.let { line ->
            Box(cell().width(CellSize), contentAlignment = Alignment.Center) { DividingLineGlyph(line) }
        }
        if (face.hasMark) {
            Box(cell().width(CellSize).padding(1.dp), contentAlignment = Alignment.Center) { MarkGlyph(face.markCode) }
        }
    }
}

/** 칸 — 18 정사각(각인 칸은 높이 18), 흰 바탕 + 연한 테두리, 모서리 4. */
@Composable
private fun cell(): Modifier {
    val shape = RoundedCornerShape(4.dp)
    return Modifier
        .height(CellSize)
        .clip(shape)
        .background(NmTheme.semanticColors.surface)
        .border(1.dp, NmColor.Neutral.C200, shape)
}

/** 구분선 그림 — 원판 12 위에 흰 선 7×1.2. `(+)형` 은 십자, `(−)형` 은 가로선 하나, 없음은 원판만. */
@Composable
private fun DividingLineGlyph(line: DividingLine) {
    val surface = NmTheme.semanticColors.surface
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(12.dp).background(NmColor.Neutral.C700, CircleShape))
        if (line != DividingLine.NONE) Box(Modifier.size(width = 7.dp, height = 1.2.dp).background(surface))
        if (line == DividingLine.PLUS) Box(Modifier.size(width = 1.2.dp, height = 7.dp).background(surface))
    }
}

/**
 * 식약처 마크 그림 — 없는 코드는 **일반 마크 아이콘**으로 「있음」만 알린다.
 *
 * 데이터가 갱신되면 앱이 모르는 마크가 생긴다. 칸을 비우면 마크가 있는 약과 없는 약이 똑같아 보여, 그게
 * 그림을 못 찾은 것보다 나쁘다(iOS 도 같은 판단이다).
 */
@Composable
private fun MarkGlyph(markCode: String?) {
    val image = PillMarkImages.of(markCode)
    if (image != null) {
        // 그림마다 여백이 이미 잘려 있어 가득 채우면 모양이 뭉개진다.
        Image(
            painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(16.dp)
        )
    } else {
        Icon(
            painterResource(R.drawable.nm_ic_badge),
            null,
            tint = NmColor.Neutral.C700,
            modifier = Modifier.size(12.dp)
        )
    }
}

private val CellSize = 18.dp
private val CellGap = 3.dp
private val GroupGap = 4.dp

/** 정본 11 — iOS 최소 글자 크기. */
private val SideLabel = NmV2Type.caption.copy(fontWeight = FontWeight.SemiBold)
private val ImprintStyle = NmV2Type.caption.copy(fontWeight = FontWeight.Bold)

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
