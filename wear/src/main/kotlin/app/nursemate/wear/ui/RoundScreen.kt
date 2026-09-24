package app.nursemate.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

// 원형 화면에서 살아남기 위한 공통 값과 도구.
//
// 화면마다 흩어져 있던 것을 모았다 — 전부 「원이라서」 필요한 것들이고, 한 곳에 있어야
// 한쪽만 고치고 다른 쪽을 잊는 일이 안 생긴다. Play 「시계 모양」으로 **세 번** 거부당한
// 자리다(2026-09-21 · 09-23 · 09-24).

/**
 * 목록이 **곡면에 닿을 수 있는 구간을 아예 안 그리게** 덮는다.
 *
 * ## 왜 필요한가 — 유도
 * 행 폭을 원에 내접하는 정사각형([HORIZONTAL_PADDING_FRACTION], 지름의 70.7%)으로 두면,
 * 반폭이 `0.354 · 지름 = 0.707 · 반지름` 이다. 반지름 `R` 인 원에서 그 폭이 들어가는
 * 세로 범위는 `|dy| ≤ R·√(1 − 0.707²) = 0.707 R` — 즉 **화면 세로 가운데 70.7%** 다.
 * 바깥 **14.6%** 에서는 행이 보이면 반드시 곡면에 물린다. 폭에서 나오는 결론이지 눈대중이 아니다.
 * (14.6% 는 `androidx.wear.widget.BoxInsetLayout` 의 `FACTOR = 0.146447f` 와 같은 값이다.)
 *
 * ## 왜 변형 스펙만으로는 안 되나
 * ⚠️ **`rememberTransformationSpec()` 기본값은 그 구간에서 항목을 안 지운다.** 1.6.2 를
 * 바이트코드로 읽으면 가장자리에서 `scale 0.7 · containerAlpha 0.5` 로 **남긴다.**
 * 그 상태로 원에 잘리고, 목록 행은 `Button` — 품질요건이 말하는 **control** 이라
 * WO-V16 위반이다(「No text or controls are cut off by the screen edges」).
 * 실제로 Play 가 목록 화면 두 장을 증거로 거부했고(2026-09-24), 기본값 그대로 재 보니
 * 24 조건 중 **17 개**에서 잘렸다.
 *
 * ⚠️ **스펙 상수를 흔들어 맞추지 않는다.** 한때 전환 구간·페이드 시작·축소율 네 개를
 * 측정값이 0 이 될 때까지 조정했는데, 그 숫자엔 근거가 없어 항목 높이나 데이터가 바뀌면
 * 다시 깨진다. 여기서는 **폭에서 유도한 구간**만 덮고, 변형은 라이브러리 기본값을 그대로 쓴다.
 *
 * @param ramp 투명에서 불투명으로 올라오는 구간. 이 값만 보기 좋으라고 정한 것이고,
 *   정확성은 [SAFE_BAND_FRACTION] 바깥이 **완전히** 투명하다는 데서 온다.
 */
internal fun Modifier.safeBandFade(ramp: Float = SAFE_BAND_RAMP): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                SAFE_BAND_FRACTION to Color.Transparent,
                SAFE_BAND_FRACTION + ramp to Color.Black,
                1f - SAFE_BAND_FRACTION - ramp to Color.Black,
                1f - SAFE_BAND_FRACTION to Color.Transparent,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

/** `BoxInsetLayout.FACTOR = 0.146447f  // (1 - sqrt(2)/2)/2` — 위 유도 참고. */
private const val SAFE_BAND_FRACTION = 0.146447f

/** 그라디언트가 올라오는 길이. 짧으면 자른 것처럼, 길면 가운데까지 흐려진다. */
private const val SAFE_BAND_RAMP = 0.06f

/**
 * 화면 폭의 [fraction] 만큼을 dp 로 준다. 곡면에 먹히지 않을 가로 여백을 정하는 자리다.
 *
 * ⚠️ **고정 dp 로 두면 안 된다.** 워치 지름이 기기마다 다르다 — 이 코드를 처음 쓸 때 기준으로
 * 삼은 기기는 203dp 였고 에뮬레이터는 227dp 다. 203dp 에 맞춰 고정한 값은 더 작은 워치에서
 * 그대로 모자라고, 실제로 Play 가 그렇게 거부했다(2026-09-21 「시계 모양」).
 */
@Composable
internal fun roundSafeHorizontal(fraction: Float): Dp = ceil(LocalConfiguration.current.screenWidthDp * fraction).dp

/**
 * 목록의 가로 콘텐츠 패딩 비율 — **원에 내접하는 정사각형**(`BoxInsetLayout.FACTOR`).
 *
 * ## 왜 문서의 5.2% 가 아닌가
 * Wear Material3 기본값은 5.2% 다(`PaddingDefaults.horizontalContentPaddingPercentage`).
 * 그 값은 **가장자리 항목이 알아서 사라지는 것을 전제**로 한다. 그런데 기본 변형 스펙은
 * 가장자리에서 70% 크기·50% 불투명도로 **남기고**(바이트코드 확인), 그 상태로 원에 잘린다.
 * 목록 행은 `Button` — 품질요건이 말하는 **control** 이라 잘리면 WO-V16 위반이고,
 * 실제로 Play 가 목록 화면 두 장을 증거로 거부했다(2026-09-24, 세 번째).
 *
 * ## 왜 넓게 두고 더 지우지 않았나
 * 처음엔 폭을 8% 로 두고 변형을 세게 걸어(알파 0, 축소 0.45) 가장자리에서 **완전히**
 * 지웠다. 곡면은 피했지만 항목이 툭 사라져 어색했다. 폭이 넓으면 일찍 닿으니 그만큼
 * 빨리 지워야 하는 것이 원인이다:
 *
 * | 좌우 여백 | 행이 안 잘리는 세로 구간 |
 * |---|---|
 * | 5.2% | 화면 가운데 43% |
 * | 8% | 54% |
 * | **14.6%** | **71%** |
 *
 * 정사각형까지 좁히면 바깥 15% 에서만 페이드하면 되므로, 변형을 기본값 수준으로 되돌려도
 * 안 잘린다 — Wear 기본 목록처럼 자연스럽게 작아지고 흐려진다.
 *
 * ⚠️ **대가는 긴 라벨의 말줄임이다.** 227dp 에서 행이 191dp → 160dp 가 된다.
 * 곡면에 컨트롤이 잘리는 것보다 말줄임이 낫다는 판단이고(2026-09-23 · 09-24 재확인),
 * 이 값을 되돌리려면 그 판단부터 다시 봐야 한다.
 */
internal const val HORIZONTAL_PADDING_FRACTION = 0.146447f

/**
 * 배율의 기준이 되는 화면 지름.
 *
 * Wear 가 지원하는 **가장 작은** 화면이다(공식 적응형 문서의 지원 하한 204dp 아래, 큰 글꼴까지
 * 겹치는 스트레스 조건이 192dp). 여기를 1.0 으로 잡아야 어떤 기기에서도 값이 이 아래로
 * 내려가지 않는다 — 글자 하한(WO-V14 의 12sp)을 지키는 방법이 이것뿐이다.
 * 기준을 204dp 로 올리면 192dp 기기에서 12sp 가 11.3sp 가 된다.
 */
internal const val BASE_SCREEN_DP = 192f

/**
 * 화면 지름이 [BASE_SCREEN_DP] 보다 얼마나 큰지의 비율.
 *
 * ⚠️ **1.0 아래로 내려가지 않는다.** 기준이 이미 지원 하한이라 더 작은 화면은 없고,
 * 설령 들어와도 값을 더 줄이면 품질 하한을 깬다.
 *
 * 가운데에 링이 있던 동안은 글자를 링 안에 맞추느라 고정 sp 를 썼다. 진행 표시를 화면
 * 가장자리로 내보내면서 가운데가 통째로 비었으므로, 큰 화면에서는 그만큼 키운다.
 */
@Composable
internal fun screenScale(): Float = (LocalConfiguration.current.screenWidthDp / BASE_SCREEN_DP).coerceAtLeast(1f)

/** 화면 크기에 비례해 키운 글자 크기. [screenScale] 참고. */
@Composable
internal fun TextStyle.scaled(): TextStyle = copy(fontSize = fontSize * screenScale())

/** 화면 크기에 비례해 키운 길이. [screenScale] 참고. */
@Composable
internal fun Dp.scaled(): Dp = this * screenScale()
