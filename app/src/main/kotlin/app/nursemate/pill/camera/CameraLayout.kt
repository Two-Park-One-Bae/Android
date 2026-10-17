package app.nursemate.pill.camera

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min

/**
 * 촬영 화면 배치 — 화면 크기에 따라 미리보기 정사각의 자리와 크기, 배율 캡슐 · 안내 문구를 둘 곳을 정한다.
 *
 * 기준은 갤럭시 기본 카메라(S24 · One UI, 1:1 비율) 실측이다(1dp = 2.625px).
 *
 * | 요소 | 기본 카메라 |
 * |---|---|
 * | 상태 표시줄 | 숨김 |
 * | 아이콘 줄 | 화면 위 31 부터 높이 36 |
 * | 미리보기 | 화면 위 136 부터 폭을 채운 정사각 |
 * | 배율 캡슐 | 내비 바 위 189 가 가운데, 높이 39 |
 * | 셔터 줄 | 내비 바 위 104 가 가운데, 높이 100 |
 *
 * ## 화면이 짧으면 겹친다 — 기본 카메라와 같다
 * 이 수치가 다 들어가려면 화면 높이가 대략 「폭 + 400dp」는 돼야 한다(S24 는 891 > 411 + 400).
 * 16:9 폰이나 태블릿 가로처럼 모자라면 [roomy] 가 false 다. 그때는 미리보기를 아이콘 줄과 셔터 줄 사이에
 * 들어가는 만큼만 키우고(폭보다 작아질 수 있다), 배율 캡슐과 안내 문구를 **미리보기 위에 얹는다** —
 * 기본 카메라도 4:3 · 16:9 에서는 배율 캡슐을 미리보기 위에 얹는다.
 *
 * 미리보기가 폭보다 작아져도 정사각이므로 「보이는 것 = 모델이 받는 것」은 그대로다(`PillCameraScreen` KDoc).
 */
internal data class CameraLayout(
    val iconsTop: Dp,
    val previewTop: Dp,
    val previewLeft: Dp,
    val previewSide: Dp,
    /** 배율 캡슐 · 안내 문구가 미리보기 밖에 들어가는가. false 면 미리보기 위에 얹는다. */
    val roomy: Boolean
) {
    val previewBottom: Dp get() = previewTop + previewSide

    companion object {
        val ICONS_TOP = 31.dp
        val ICONS_HEIGHT = 36.dp
        val PREVIEW_TOP = 136.dp

        /** 내비 바 윗변에서 잰 배율 캡슐 가운데. */
        val ZOOM_CENTER = 189.dp
        val ZOOM_HEIGHT = 39.dp

        /** 내비 바 윗변에서 잰 셔터 줄 가운데. */
        val SHUTTER_CENTER = 104.dp
        val SHUTTER_HEIGHT = 100.dp

        /** 미리보기 아래 안내 문구 자리 — 위 여백 18 + 알약 모양 38. */
        val HINT_BLOCK = 56.dp

        /** 겹쳐 놓을 때 미리보기 안쪽 여백 · 아이콘 줄과 미리보기 사이. */
        val INSET = 12.dp
        private val GAP = 8.dp

        /**
         * @param width 화면 폭
         * @param height 화면 높이(상태 표시줄을 숨기고 끝에서 끝까지 그린다)
         * @param navBottom 내비 바 높이
         * @param cutoutTop 화면 위 카메라 구멍 · 노치가 차지하는 높이
         */
        fun of(width: Dp, height: Dp, navBottom: Dp, cutoutTop: Dp): CameraLayout {
            val iconsTop = max(ICONS_TOP, cutoutTop)
            val topBound = iconsTop + ICONS_HEIGHT + GAP
            val zoomTop = height - navBottom - ZOOM_CENTER - ZOOM_HEIGHT / 2
            val previewTop = max(PREVIEW_TOP, topBound)

            if (previewTop + width + HINT_BLOCK <= zoomTop) {
                return CameraLayout(iconsTop, previewTop, 0.dp, width, roomy = true)
            }

            val shutterTop = height - navBottom - SHUTTER_CENTER - SHUTTER_HEIGHT / 2
            val room = shutterTop - INSET - topBound
            val side = min(width, room)
            return CameraLayout(
                iconsTop = iconsTop,
                previewTop = topBound + (room - side) / 2,
                previewLeft = (width - side) / 2,
                previewSide = side,
                roomy = false
            )
        }
    }
}
