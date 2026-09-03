package app.nursemate.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pretendard 패밀리.
 *
 * ⚠️ **SemiBold(600)가 반드시 등록돼 있어야 한다.** 디자인 정본이 제목·버튼·카드에 600을 쓰는데
 * 등록하지 않으면 Compose가 Bold(700)로 대체하거나 합성해서, 의도보다 굵게 나온다.
 */
val PretendardFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

/**
 * 타이포 스케일 — 정본은 `spec/design/DESIGN.pen` 의 `Foundation / Typography`.
 *
 * `lineHeight`·`letterSpacing`은 정본 스케일 표에 수치가 없어 iOS DSKit 값을 따랐다
 * (정본 텍스트 노드의 letterSpacing과는 일치한다).
 *
 * > ⚠️ **iOS를 웨이트의 근거로 삼으면 안 된다.** iOS `UIFont.MDS`는 heading2·heading3를 700,
 * > title을 400으로 두어 정본(600·600·600)과 어긋나 있다. 게다가 그 스케일을 참조하는 코드가
 * > iOS 앱 전체에 하나도 없어(폰트를 172곳에서 직접 호출) 사실상 죽은 선언이다.
 * > 여기서는 정본을 따른다.
 */
object NmTypography {
    /** 46 / 700 */
    val display = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.0).sp
    )

    /** 36 / 700 */
    val heading1 = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    )

    /** 28 / 600 */
    val heading2 = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    )

    /** 22 / 600 */
    val heading3 = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    )

    /** 18 / 600 */
    val title = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp
    )

    /** 16 / 400 */
    val bodyLarge = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )

    /** 14 / 400 */
    val body = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    )

    /** 12 / 500 */
    val caption = TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    )
}
