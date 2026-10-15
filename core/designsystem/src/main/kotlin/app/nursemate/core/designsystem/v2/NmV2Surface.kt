package app.nursemate.core.designsystem.v2

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor

/**
 * V2.1.0 면 — 재질이 쓰임을 말한다(NM-580 결정 3).
 *
 * | 재질 | 이 파일 | 쓰는 곳 |
 * |---|---|---|
 * | 유리 | [Card] · [ListCard] · [CardCaution] · [ListCardSuccess] · [CardSelected] | 카드 |
 * | 유리(뜨는 면) | [Floating] | 탭 바 · 모달 판 |
 * | 볼록 | `NmV2Buttons.kt` | 버튼 · 아이콘 버튼 · 드롭다운 칩 |
 * | 평면 | `NmV2Controls.kt` | 태그 · 칩 · 배너 · 메뉴 · 입력 칸 |
 *
 * ## 카드는 흐림 없이 가장자리를 직접 그린다
 * 화면 배경이 흰색이라 카드 뒤에 흐려질 것이 없다(NM-580 결정 4 — 3배 비교에서 최대 3/255).
 * 면 그라데이션 · 무지갯빛 테두리 · 바깥 그림자 2겹 · 안쪽 그림자 2~3겹을 그린다.
 *
 * ## 뜨는 면은 `glass-fallback` 으로 그린다
 * Android 에는 시스템 유리가 없다. 정본의 `Surface Floating`(배경 흐림 22) 대신
 * `Surface Floating Fallback` — 거의 불투명한 흰 면 + 흰 테두리 + 카드 그림자 — 을 쓴다(NM-608).
 *
 * ⚠️ **안쪽 그림자는 내용 아래 층에 그린다.** `innerShadow` 를 바깥 Modifier 사슬에 걸면 자식
 *    위에 그려져 `glass-inner-glow`(흰 70%)가 글자를 덮는다. 그래서 [NmV2Surface] 가 면을
 *    [Spacer] 한 장에 따로 그리고 그 위에 내용을 놓는다.
 */
enum class NmV2SurfaceStyle {
    /** `Surface Card` — 카드. */
    Card,

    /** `Surface List Card` — 촘촘히 쌓이는 목록 카드. 그림자가 얕고 광택 띠가 없다. */
    ListCard,

    /** `Surface Card Caution` — 만료 타이머처럼 주의 상태인 카드. */
    CardCaution,

    /** `Surface List Card Success` — 완료 상태인 목록 카드. */
    ListCardSuccess,

    /** `Surface Card Selected` — 선택된 카드(`primary-500` 2). */
    CardSelected,

    /** `Surface Floating Fallback` — 뜨는 조작부. */
    Floating
}

/** 카드 모서리 20. */
val NmV2CardShape = RoundedCornerShape(NmV2Radius.card)

/**
 * 재질 면 위에 [content] 를 놓는다. [modifier] 의 크기 · 패딩은 바깥 상자에 그대로 걸린다 —
 * 내용 여백은 [content] 쪽에서 준다.
 */
@Composable
fun NmV2Surface(
    style: NmV2SurfaceStyle,
    modifier: Modifier = Modifier,
    shape: Shape = if (style == NmV2SurfaceStyle.Floating) CircleShape else NmV2CardShape,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.v2OuterShadows(style.outer, shape)) {
        Spacer(Modifier.matchParentSize().v2SurfaceLayer(style, shape))
        content()
    }
}

// ── 그림자 ──────────────────────────────────────────────────────────

/** 바깥 그림자 한 겹. 정본 `effect` 의 `shadowType: outer` 와 1:1. */
internal data class V2Shadow(val color: Color, val y: Dp, val blur: Dp, val x: Dp = 0.dp)

internal val ShadowCard = listOf(
    V2Shadow(NmV2Color.Shadow.card, NmV2Shadow.cardY, NmV2Shadow.cardBlur),
    V2Shadow(NmV2Color.Shadow.cardContact, NmV2Shadow.contactY, NmV2Shadow.contactBlur)
)

internal val ShadowList = listOf(
    V2Shadow(NmV2Color.Shadow.list, NmV2Shadow.listY, NmV2Shadow.listBlur),
    V2Shadow(NmV2Color.Shadow.listContact, NmV2Shadow.contactY, NmV2Shadow.contactBlur)
)

internal fun Modifier.v2OuterShadows(shadows: List<V2Shadow>, shape: Shape): Modifier = shadows.fold(this) { m, s ->
    m.dropShadow(shape, Shadow(radius = s.blur, color = s.color, offset = DpOffset(s.x, s.y)))
}

internal fun Modifier.v2InnerShadows(shadows: List<V2Shadow>, shape: Shape): Modifier = shadows.fold(this) { m, s ->
    m.innerShadow(shape, Shadow(radius = s.blur, color = s.color, offset = DpOffset(s.x, s.y)))
}

/** 카드 안쪽 그림자 3겹 — 왼위 가장자리 빛 · 안쪽 빛 · 오른아래 그늘. */
private val InnerCard = listOf(
    V2Shadow(NmV2Color.Glass.innerEdge, y = 2.dp, blur = 1.dp, x = 1.5.dp),
    V2Shadow(NmV2Color.Glass.innerGlow, y = 0.dp, blur = 14.dp),
    V2Shadow(NmV2Color.Glass.innerShade, y = (-3).dp, blur = 8.dp, x = (-2).dp)
)

/** 목록 카드 안쪽 그림자 2겹 — 그늘이 없고 빛이 얕다. */
private val InnerList = listOf(
    V2Shadow(NmV2Color.Glass.innerEdge, y = 2.dp, blur = 1.dp, x = 1.5.dp),
    V2Shadow(NmV2Color.Glass.innerGlow, y = 0.dp, blur = 12.dp)
)

private val NmV2SurfaceStyle.outer: List<V2Shadow>
    get() = when (this) {
        NmV2SurfaceStyle.ListCard, NmV2SurfaceStyle.ListCardSuccess -> ShadowList
        else -> ShadowCard
    }

// ── 면 ──────────────────────────────────────────────────────────────

/** 무지갯빛 테두리 — 135° 로 흰 → 하늘 → 투명 → 호박 → 흰. */
internal val GlassStrokeStops = arrayOf(
    0f to NmV2Color.Glass.strokeWhite,
    0.25f to NmV2Color.Glass.strokeSky,
    0.45f to NmV2Color.Glass.strokeClear,
    0.7f to NmV2Color.Glass.strokeAmber,
    1f to NmV2Color.Glass.strokeWhite
)

internal val GlassStrokeBrush: Brush = Brush.linearGradient(colorStops = GlassStrokeStops)

/** 유리 면 — 위에서 아래로 밝기가 줄고, 왼위에 둥근 빛, 카드에는 사선 광택 띠. */
private fun Modifier.glassFill(sheen: Boolean): Modifier = drawWithCache {
    val vertical = Brush.verticalGradient(listOf(NmV2Color.Glass.fillTop, NmV2Color.Glass.fillBottom))
    // 정본 radial 은 크기 0.9 × 0.9(타원), 중심 (0.08, 0.05). 원으로 그리고 세로를 눌러 타원을 만든다.
    val radius = size.width * 0.45f
    val ratio = highlightRatio(size.width, size.height)
    val center = Offset(size.width * 0.08f, size.height * 0.05f / ratio)
    val highlight = Brush.radialGradient(
        listOf(NmV2Color.Glass.highlight, Color.Transparent),
        center = center,
        radius = radius
    )
    val sheenBrush = Brush.linearGradient(
        0.35f to Color.Transparent,
        0.48f to NmV2Color.Glass.sheen,
        0.6f to Color.Transparent,
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    onDrawBehind {
        // 반투명 면 아래에 흰 바탕을 깐다 — 없으면 바깥 그림자가 면을 비쳐 회색으로 탁해진다.
        // Pencil 은 그림자를 면 바깥에만 그리고 Compose `dropShadow` 는 모양 전체에 그린다.
        // 화면 배경이 흰색이라(NM-580) 정본이 보이는 색과 같다.
        drawRect(GlassBase)
        drawRect(vertical)
        scale(scaleX = 1f, scaleY = ratio, pivot = Offset.Zero) {
            drawRect(
                highlight,
                size = size.copy(
                    height =
                        size.height / ratio
                )
            )
        }
        if (sheen) drawRect(sheenBrush)
    }
}

/**
 * 둥근 빛을 타원으로 누를 비율(세로/가로). 한쪽이 0 이면 1 로 둔다 — 0 으로 나누면 중심이 NaN 이 되고
 * 눌림 행렬이 특이해진다(내용이 비어 높이 0 인 첫 측정).
 */
private fun highlightRatio(width: Float, height: Float): Float = if (width > 0f && height > 0f) height / width else 1f

/** 상태 면 — 옅은 상태색 위에 왼위 둥근 빛 하나(70% 흰). */
private fun Modifier.tintFill(base: Color): Modifier = drawWithCache {
    val ratio = highlightRatio(size.width, size.height)
    val highlight = Brush.radialGradient(
        listOf(TintHighlight, Color.Transparent),
        center = Offset(size.width * 0.08f, size.height * 0.05f / ratio),
        radius = size.width * 0.45f
    )
    onDrawBehind {
        drawRect(base)
        scale(scaleX = 1f, scaleY = ratio, pivot = Offset.Zero) {
            drawRect(
                highlight,
                size = size.copy(
                    height =
                        size.height / ratio
                )
            )
        }
    }
}

/** 유리 면 바탕 — V2.1.0 화면 배경(`surface`)과 같은 흰색. */
private val GlassBase = NmColor.Neutral.C0

/** 상태 카드의 왼위 빛. 정본에 변수 없이 `#FFFFFFB3` 로 적혀 있다 — `glass-inner-glow` 와 같은 값이다. */
private val TintHighlight = NmV2Color.Glass.innerGlow

private fun Modifier.v2SurfaceLayer(style: NmV2SurfaceStyle, shape: Shape): Modifier {
    val clipped = clip(shape)
    return when (style) {
        NmV2SurfaceStyle.Card -> clipped.glassFill(sheen = true).v2InnerShadows(InnerCard, shape)
            .border(NmV2Glass.strokeWidth, GlassStrokeBrush, shape)

        NmV2SurfaceStyle.ListCard -> clipped.glassFill(sheen = false).v2InnerShadows(InnerList, shape)
            .border(NmV2Glass.strokeWidth, GlassStrokeBrush, shape)

        NmV2SurfaceStyle.CardSelected -> clipped.glassFill(sheen = true).v2InnerShadows(InnerCard, shape)
            .border(2.dp, NmColor.Primary.C500, shape)

        NmV2SurfaceStyle.CardCaution -> clipped.tintFill(NmV2Color.Caution.C50).v2InnerShadows(InnerCard, shape)
            .border(1.dp, NmV2Color.Caution.C300, shape)

        NmV2SurfaceStyle.ListCardSuccess -> clipped.tintFill(NmColor.Success.C50).v2InnerShadows(InnerList, shape)
            .border(1.dp, NmColor.Success.C300, shape)

        NmV2SurfaceStyle.Floating -> clipped.drawWithCache {
            onDrawBehind {
                drawRect(GlassBase)
                drawRect(NmV2Color.Glass.fallback)
            }
        }
            .border(1.dp, NmV2Color.Glass.strokeWhite, shape)
    }
}
