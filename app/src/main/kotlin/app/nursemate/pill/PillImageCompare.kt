package app.nursemate.pill

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.PillCandidate
import coil3.compose.SubcomposeAsyncImage

/*
 후보 이미지 비교 — 디자인 `⑧-i 비교` · `⑧-j 크롭 없음` · `⑧-k 원본 폴백`.

 썸네일(장변 256)만으로는 실물 대조가 어려워, 탭하면 후보의 **원본** 이미지를 크게 띄우고
 촬영한 크롭과 나란히 본다(spec NM-354).

 정본과 스펙이 못박은 제약을 그대로 지킨다 — 모드 토글 없음 · 화면 폭 맞춤 · 핀치 줌 없음 ·
 **딤을 눌러도 닫히지 않는다**(✕ 또는 아래로 스와이프만). 뒤에 있는 수정 화면을 잘못 건드려
 조건이 바뀌는 일을 막으려는 것이다.
*/

@Composable
fun PillImageCompare(candidate: PillCandidate, crop: Bitmap?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Dim)
            // 딤 탭으로는 닫지 않는다(spec). 여기서 입력을 먹어 뒤 화면으로 흘려보내지도 않는다.
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > SWIPE_DOWN_THRESHOLD) onClose()
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            ImageCard(label = "후보 이미지") { CandidateImage(candidate.pillImageUrl) }

            // 수동 추가 알약은 사진에 대응 영역이 없다 — 빈 칸을 두지 않고 후보만 보여준다.
            if (crop != null) {
                ImageCard(label = "촬영한 알약") {
                    Image(
                        bitmap = crop.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(ImageHeight).background(NmColor.Neutral.C100)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            CloseButton(onClick = onClose)
        }
    }
}

/** 흰 카드에 이미지 하나 + 좌상단 라벨 칩. */
@Composable
private fun ImageCard(label: String, image: @Composable () -> Unit) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
    ) {
        image()
        Box(
            modifier = Modifier
                .padding(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(ChipBackground)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(text = label, style = ChipLabel, color = Color.White)
        }
    }
}

/**
 * 후보 원본.
 *
 * 낱알 이미지가 없는 품목은 CDN 이 404 를 준다(NM-347). 그때는 카드를 비우지 않고
 * 플레이스홀더로 채운다 — 빈 흰 칸은 로딩 실패인지 이미지가 없는 것인지 구분되지 않는다.
 */
@Composable
private fun CandidateImage(url: String?) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        loading = { ImagePlaceholder(text = null) },
        error = { ImagePlaceholder(text = "원본 이미지 없음") },
        modifier = Modifier.fillMaxWidth().height(ImageHeight).background(NmColor.Neutral.C100)
    )
}

@Composable
private fun ImagePlaceholder(text: String?) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier.fillMaxWidth().height(ImageHeight).background(NmColor.Neutral.C100),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (text == null) return@Column
        Icon(
            painter = painterResource(R.drawable.nm_ic_image),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(42.dp)
        )
        Text(text = text, style = PlaceholderLabel, color = colors.textTertiary)
    }
}

/** 44 짜리 닫기 과녁. 아이콘은 26 이라 손가락으로 누르기엔 작다. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.nm_ic_close),
            contentDescription = "닫기",
            tint = NmTheme.semanticColors.textPrimary,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** 정본 Dim. 어두운 막이 아니라 앱 배경색을 거의 불투명하게 덮는다. */
private val Dim = Color(0xF2EEF2F7)
private val ChipBackground = Color(0xCC0F172A)
private val ShadowColor = Color(0x240F172A)
private val ImageHeight = 186.dp

/** 아래로 스와이프해 닫는 문턱. 목록을 훑다 살짝 흔들리는 정도로는 닫히지 않게 둔다. */
private const val SWIPE_DOWN_THRESHOLD = 40f

private val ChipLabel = NmTypography.caption
private val PlaceholderLabel = NmTypography.body.copy(fontSize = 13.sp)
