package app.nursemate.pill

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme

/**
 * 촬영 사진 카드 — 디자인 `Photo Card` (④ 로딩 · ⑤ 인식 결과 공용).
 *
 * 흰 카드(radius 20, 그림자) 안에 [PhotoSize] 정사각 사진(radius 14). iOS도 같은 뷰를
 * 두 화면이 공유한다(`PhotoCardView`).
 *
 * @param dimAlpha 사진을 덮는 어둠의 진하기. 정본 값은 로딩 15%(`#0F172A26`),
 *                 결과 없음 55%(`#0F172A8C`). 0이면 덮지 않는다.
 * @param overlay 사진 위에 얹을 것(BBOX·번호 배지). 좌표는 [PhotoSize] 기준으로 계산한다.
 */
@Composable
fun PillPhotoCard(
    photo: Bitmap?,
    modifier: Modifier = Modifier,
    dimAlpha: Float = 0f,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val colors = NmTheme.semanticColors
    Box(
        modifier = modifier
            // 카드는 사진(고정 300) + 안쪽 여백. 폭만 잡고 높이는 내용이 정한다.
            .width(PhotoSize + CardPadding * 2)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = NmColor.Neutral.C900,
                spotColor = NmColor.Neutral.C900
            )
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(CardPadding)
    ) {
        Box(
            modifier = Modifier
                // ⚠️ `fillMaxWidth().aspectRatio(1f)` 로 두면 실기기에서 세로가 짧게 나왔다
                //    (300x255). 정본도 사진을 300x300 **고정**으로 정의하므로 크기를 못박는다.
                //    BBOX 좌표가 [PhotoSize] 를 기준으로 계산되므로 여기가 정확해야 한다.
                .size(PhotoSize)
                .clip(RoundedCornerShape(14.dp))
                .background(NmColor.Neutral.C100)
        ) {
            photo?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "촬영한 알약 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (dimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NmColor.Neutral.C900.copy(alpha = dimAlpha))
                )
            }
            overlay()
        }
    }
}

/**
 * 사진 한 변의 길이. 정본 값이며 **BBOX 좌표 계산의 기준**이다.
 *
 * 검출 좌표는 0~1 정규화라 여기에 곱해서 배치한다. 사진이 정사각이므로 가로·세로 같은 값을 쓴다.
 */
val PhotoSize: Dp = 300.dp

private val CardPadding = 8.dp
