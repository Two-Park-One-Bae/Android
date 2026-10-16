package app.nursemate.pill

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.v2.NmV2Surface
import app.nursemate.core.designsystem.v2.NmV2SurfaceStyle

/**
 * 촬영 사진 카드 — 정본 `Photo Card` (④ 로딩 · ⑥ 결과 없음). 유리 카드(모서리 20) 안에 300 정사각 사진(모서리 14).
 *
 * @param dimAlpha 사진을 덮는 어둠의 진하기. 정본 값은 로딩 15%(`#0F172A26`),
 *                 결과 없음 55%(`#0F172A8C`). 0이면 덮지 않는다.
 * @param overlay 사진 위에 얹을 것(결과 없음의 아이콘).
 */
@Composable
fun PillPhotoCard(
    photo: Bitmap?,
    modifier: Modifier = Modifier,
    dimAlpha: Float = 0f,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    NmV2Surface(NmV2SurfaceStyle.Card, modifier) {
        Box(
            Modifier
                .padding(8.dp)
                // ⚠️ `fillMaxWidth().aspectRatio(1f)` 로 두면 실기기에서 세로가 짧게 나왔다(300x255).
                //    정본도 사진을 300x300 **고정**으로 정의하므로 크기를 못박는다.
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
                Box(Modifier.fillMaxSize().background(NmColor.Neutral.C900.copy(alpha = dimAlpha)))
            }
            overlay()
        }
    }
}

/** 사진 한 변 — 정본 300. */
private val PhotoSize = 300.dp
