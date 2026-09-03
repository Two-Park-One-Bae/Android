package app.nursemate.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 로딩 표시 — 정본 `Foundation / Components` §Loading.
 *
 * 스피너 40 · 간격 14 · 라벨 14/500(text-secondary) · 패딩 24.
 *
 * @param label 없으면 스피너만 보인다. 무엇을 기다리는지 알 수 있으면 넣는 편이 낫다.
 */
@Composable
fun NmLoading(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier.padding(NmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GAP),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = NmColor.Primary.C500,
            strokeWidth = STROKE,
            modifier = Modifier.size(SPINNER_SIZE)
        )
        if (label != null) {
            Text(text = label, style = LabelStyle, color = NmTheme.semanticColors.textSecondary)
        }
    }
}

/** 정본 14/500. 스케일에 500 이 없어 body(14/400)에서 웨이트만 올린다. */
private val LabelStyle = NmTypography.body.copy(fontWeight = FontWeight.Medium)

private val SPINNER_SIZE = 40.dp
private val GAP = 14.dp

/** 정본에 굵기 지정이 없다. 40dp 스피너에 3dp 가 시각적으로 맞는다. */
private val STROKE = 3.dp
