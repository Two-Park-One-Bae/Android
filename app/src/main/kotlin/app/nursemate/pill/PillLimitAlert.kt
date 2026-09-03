package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmDim
import app.nursemate.core.designsystem.NmRadius
import app.nursemate.core.designsystem.NmSpacing
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.Usage

/**
 * 한도 안내 알럿 — 디자인 `홈 — 식별 한도 팝업`.
 *
 * ## `NmConfirmDialog` 가 아니다
 * 버튼이 하나뿐이고(확인) 라벨 굵기도 700 이다. 고를 것이 없는 **안내**라 확인 모달과
 * 성격이 다르다 — 취소 자리를 만들면 "안 하면 되나?" 하고 읽히게 된다.
 *
 * @param usage 리셋 시각을 안내하려고 받는다. 모르면(null) 시각 없이 안내만 한다.
 */
@Composable
fun PillLimitAlert(usage: Usage?, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors

    Box(
        modifier = modifier.fillMaxSize().background(NmDim),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(NmRadius.lg))
                .background(colors.surface)
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = NmSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NmSpacing.sm)
        ) {
            Text(
                text = "오늘 식별 횟수를 모두 사용했어요",
                style = AlertTitle,
                color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = usage.resetGuide(),
                style = AlertMessage,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.fillMaxWidth().padding(top = NmSpacing.sm)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(NmRadius.md))
                        .background(NmColor.Primary.C500)
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "확인", style = AlertButton, color = NmColor.Neutral.C0)
                }
            }
        }
    }
}

/**
 * 리셋 안내 문구.
 *
 * 정본은 "내일 00:00에 초기화돼요" 다. 서버가 주는 [Usage.resetAt] 은 KST 자정이라
 * 시각이 늘 00:00 이지만, **그 값을 파싱해 다시 조립하지는 않는다** — 시간대·형식 처리를
 * 들이는 값어치가 없고, 서버가 정책을 바꾸면 문구만 고치면 된다.
 * 값을 모를 때만 시각을 뺀다.
 */
private fun Usage?.resetGuide(): String = if (this == null) "잠시 후 다시 확인해 주세요" else "내일 00:00에 초기화돼요"

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val AlertTitle = NmTypography.bodyLarge.copy(fontWeight = FontWeight.Bold)
private val AlertMessage = NmTypography.body.copy(fontSize = 13.sp)
private val AlertButton = NmTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
