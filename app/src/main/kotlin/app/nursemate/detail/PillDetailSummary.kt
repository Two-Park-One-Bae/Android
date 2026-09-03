package app.nursemate.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.Ingredient
import app.nursemate.core.model.PillClassification
import app.nursemate.core.model.PillDetail
import coil3.compose.SubcomposeAsyncImage

/*
 세부정보 위·아래 고정부 — 디자인 `⑩-b 요약 헤더` · `제품정보` · `출처·고지`.

 문서 탭이 무엇이든 이 셋은 그대로다.
*/

/** 배지 · 품목명 · 업체명 · 낱알 원본 · 성상 · 성분. */
@Composable
internal fun DetailSummary(detail: PillDetail) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        detail.classification.badge()?.let { ClassificationBadge(it) }

        Text(text = detail.name, style = DetailTitle, color = colors.textPrimary)
        Text(text = detail.companyName, style = DetailCompany, color = colors.textSecondary)

        // 낱알 원본. 값은 항상 오지만 이미지가 없는 품목은 CDN 이 404 를 준다(NM-347).
        val imageShape = RoundedCornerShape(12.dp)
        SubcomposeAsyncImage(
            model = detail.pillImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            error = { PillImageFallback() },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(imageShape)
                .background(NmColor.Neutral.C100)
                .border(1.dp, colors.border, imageShape)
        )

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))

        detail.appearance?.let { LabeledRow(label = "성상", value = it, labelWidth = 36.dp) }
        detail.ingredients.takeIf { it.isNotEmpty() }?.let {
            LabeledRow(label = "성분", value = it.summary(), labelWidth = 36.dp)
        }
    }
}

@Composable
private fun PillImageFallback() {
    Box(
        modifier = Modifier.fillMaxWidth().height(140.dp).background(NmColor.Neutral.C100),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.nm_ic_image),
            contentDescription = null,
            tint = NmTheme.semanticColors.textTertiary,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun ClassificationBadge(label: String) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .background(NmColor.Info.C50, shape)
            .border(1.dp, NmColor.Info.C100, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, style = BadgeLabel, color = NmColor.Info.C700)
    }
}

/** 저장방법 · 유효기간 · 포장단위. 하나도 없으면 블록째 빼서 빈 카드를 남기지 않는다. */
@Composable
internal fun ProductInfo(detail: PillDetail) {
    val colors = NmTheme.semanticColors
    val rows = listOfNotNull(
        detail.storageMethod?.let { "저장방법" to it },
        detail.validTerm?.let { "유효기간" to it },
        detail.packUnit?.let { "포장단위" to it }
    )
    if (rows.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "제품정보", style = SectionTitle, color = colors.textPrimary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { (label, value) -> LabeledRow(label = label, value = value, labelWidth = 56.dp) }
        }
    }
}

/** 출처와 고지. 둘을 한 덩이로 둔다 — 정본이 같은 문단으로 묶어 놨다. */
@Composable
internal fun DetailNotice() {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Text(
            text = "출처: 식품의약품안전처 의약품 제품 허가정보\n" +
                "널스메이트의 알약 식별 결과는 참고용 보조 정보입니다. 투약 전 반드시 처방 내용과 " +
                "약품 라벨을 확인하시고, 최종 판단은 의료진의 확인을 따라 주세요.",
            style = NoticeText,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 라벨 폭을 고정한 한 줄. 값이 길면 줄바꿈된다. */
@Composable
private fun LabeledRow(label: String, value: String, labelWidth: androidx.compose.ui.unit.Dp) {
    val colors = NmTheme.semanticColors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            style = FieldLabel,
            color = colors.textTertiary,
            modifier = Modifier.width(labelWidth).padding(vertical = 2.dp)
        )
        Text(text = value, style = FieldValue, color = colors.textSecondary, modifier = Modifier.weight(1f))
    }
}

private fun PillClassification.badge(): String? = when (this) {
    PillClassification.ETC -> "전문의약품"

    PillClassification.OTC -> "일반의약품"

    // 서버가 모르는 값을 보냈다. 아무 말도 하지 않는 편이 틀린 말을 하는 것보다 낫다.
    PillClassification.UNKNOWN -> null
}

/** `세파클러수화물 262.2mg` 처럼 이어 붙인다. 여러 성분은 줄을 바꿔 나열한다. */
private fun List<Ingredient>.summary(): String =
    joinToString("\n") { listOf(it.name, it.amount + it.unit).joinToString(" ") }

private val DetailTitle = NmTypography.heading2.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold)
private val DetailCompany = NmTypography.body.copy(fontSize = 13.sp)
private val BadgeLabel = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
private val SectionTitle = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val FieldLabel = NmTypography.caption.copy(fontWeight = FontWeight.SemiBold)
private val FieldValue = NmTypography.body.copy(fontSize = 13.sp, lineHeight = 19.5.sp)
private val NoticeText = NmTypography.caption.copy(fontSize = 11.sp, lineHeight = 17.6.sp)
