package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2Color
import app.nursemate.core.designsystem.v2.NmV2IconButtonGlass
import app.nursemate.core.designsystem.v2.NmV2Surface
import app.nursemate.core.designsystem.v2.NmV2SurfaceStyle
import app.nursemate.core.designsystem.v2.NmV2Type
import app.nursemate.core.model.LicenseStatus
import app.nursemate.core.model.PillCandidate
import coil3.compose.AsyncImage

/**
 * 후보 카드 — 정본 ② 2열 격자의 한 칸 (NM-588).
 *
 * 낱알 사진(원래 비율, 카드 폭 — 자르지 않는다) · 품목명(한 줄 말줄임) · 세부정보(ⓘ) / 면 요약.
 * **업체명은 두지 않는다** — 고르는 단서는 사진과 각인 · 마크이고, 업체는 세부정보와 결과 카드에서 본다.
 * 허가 종료 배지는 사진 왼쪽 위에 얹는다 — 카드 높이를 같게 두려고. 사진 오른쪽 위 `크게 보기` 는 비교 뷰어다.
 *
 * 카드를 누르면 고른다(파란 테두리 2). 세부정보 · 크게 보기는 선택과 독립이다(spec §세부정보 조회 · NM-354).
 */
@Composable
internal fun PillCandidateCard(
    candidate: PillCandidate,
    selected: Boolean,
    onClick: () -> Unit,
    onDetail: () -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NmTheme.semanticColors
    NmV2Surface(if (selected) NmV2SurfaceStyle.CardSelected else NmV2SurfaceStyle.Card, modifier) {
        Column(
            Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 10.dp, top = 7.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(THUMB_RATIO)
                    .clip(RoundedCornerShape(NmRadius.md))
                    .background(NmColor.Neutral.C100)
            ) {
                AsyncImage(
                    model = candidate.pillThumbnailUrl.ifBlank { null },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(THUMB_RATIO)
                )
                if (candidate.licenseStatus == LicenseStatus.REVOKED) {
                    PillRevokedBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                // 버튼은 28 이지만 누르는 영역은 48 이다 — 보이는 원이 오른쪽 위 4 에 오게 바깥으로 민다.
                NmV2IconButtonGlass(
                    icon = NmSymbols.OpenInFull,
                    contentDescription = "크게 보기",
                    onClick = onCompare,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = GlassOverhang, y = -GlassOverhang)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    candidate.pillName,
                    style = NmV2Type.menu.copy(
                        fontSize = NmV2Type.chip.fontSize,
                        fontWeight = NmV2Type.chipConfirmed.fontWeight
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                DetailButton(onDetail)
            }
            PillCandidateFaceSummary(candidate.front, candidate.back)
        }
    }
}

/** 세부정보 — 볼록한 흰 원 24 · ⓘ 16(`primary-600`). 누르는 영역은 48. */
@Composable
private fun DetailButton(onClick: () -> Unit) {
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(24.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(NmTheme.semanticColors.surface, NmV2Color.Raised.neutralBottom)))
            .border(1.dp, NmColor.Neutral.C200, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(NmSymbols.Info),
            contentDescription = "세부정보",
            tint = NmColor.Primary.C600,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 후보 두 장을 한 줄에 — 간격 8, **높이를 맞춘다**. 면 요약이 넘쳐 두 줄이 된 카드가 있으면 짝 카드도
 * 그 높이로 늘린다(spec §후보 격자 — 넘칠 때).
 *
 * 짝이 없으면(마지막 홀수) 왼쪽 칸만 채우고 오른쪽은 비운다 — 카드 폭이 줄마다 같아야 한다.
 */
@Composable
internal fun PillCandidatePair(
    left: @Composable (Modifier) -> Unit,
    right: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp
) {
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val gapPx = gap.roundToPx()
        val cellWidth = (constraints.maxWidth - gapPx) / 2
        val loose = Constraints(minWidth = cellWidth, maxWidth = cellWidth)
        val heights = buildList {
            add(subcompose(Slot.MeasureLeft) { left(Modifier) }.single().measure(loose).height)
            if (right != null) add(subcompose(Slot.MeasureRight) { right(Modifier) }.single().measure(loose).height)
        }
        val height = heights.max()
        val fixed = Constraints.fixed(cellWidth, height)
        val placedLeft = subcompose(Slot.Left) { left(Modifier) }.single().measure(fixed)
        val placedRight = right?.let { subcompose(Slot.Right) { it(Modifier) }.single().measure(fixed) }
        layout(constraints.maxWidth, height) {
            placedLeft.place(0, 0)
            placedRight?.place(cellWidth + gapPx, 0)
        }
    }
}

private enum class Slot { MeasureLeft, MeasureRight, Left, Right }

/** 정본 사진 칸 151×78 — CDN 낱알 이미지(약 1.83:1)가 거의 그대로 들어간다. */
private const val THUMB_RATIO = 151f / 78f

/** 누르는 영역 48 과 보이는 원 28 의 차이 절반 — 보이는 원이 오른쪽 위 4 에 오도록 밀어낸다. */
private val GlassOverhang = 6.dp
