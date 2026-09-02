package app.nursemate.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pretendard 타이포 스케일 — iOS DSKit UIFont.MDS 8종과 1:1 일치 (size/lineHeight/letterSpacing/weight).
 */
val PretendardFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

object NmTypography {
    val display = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.0).sp
    )
    val heading1 = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    )
    val heading2 = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    )
    val heading3 = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    )
    val title = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp
    )
    val bodyLarge = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val body = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    )
    val caption = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    )
}
