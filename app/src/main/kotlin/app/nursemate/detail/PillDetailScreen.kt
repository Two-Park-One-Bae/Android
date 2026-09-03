package app.nursemate.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmButtonSecondary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmNavBar
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.DocType
import app.nursemate.core.model.PillDetail
import app.nursemate.ui.SystemBarIcons

/**
 * 세부정보 — 디자인 `⑩-a 로딩` · `⑩-b~d 본문` · `⑩-e 없음` · `⑩-f 오류` · `⑩-g 허가 종료`.
 *
 * ## 후보 선택과 완전히 독립이다
 * 고르기 전에 미리 보라고 만든 화면이라 여기서 무엇을 해도 후보 선택은 바뀌지 않는다
 * (spec §세부정보 조회). 그래서 확인 버튼도 없고 뒤로 가면 그대로 돌아간다.
 *
 * ## '없음'과 '오류'를 가른다
 * 미적재 품목은 다시 눌러도 없다 — 재시도 버튼을 두지 않는다. 허가 종료 품목은 아예
 * 조회하지 않고 안내만 한다(NM-369).
 */
@Composable
fun PillDetailScreen(state: DetailUiState, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        NmNavBar(title = "세부정보", onBack = onBack)

        when (state) {
            DetailUiState.Loading -> DetailLoading()

            is DetailUiState.Ready -> DetailBody(state.detail)

            DetailUiState.Empty -> DetailMessage(
                icon = R.drawable.nm_ic_file_search,
                iconTint = NmColor.Neutral.C400,
                circle = NmColor.Neutral.C100,
                title = "등록된 세부정보가 없어요",
                description = "이 약은 허가정보가 아직 준비되지 않았어요\n후보 선택과 식별 결과에는 영향이 없어요",
                onBack = onBack,
                onRetry = null
            )

            DetailUiState.Failed -> DetailMessage(
                icon = R.drawable.nm_ic_circle_alert,
                iconTint = NmColor.Error.C500,
                circle = NmColor.Error.C50,
                title = "세부정보를 불러오지 못했어요",
                description = "네트워크 연결을 확인하고\n다시 시도해 주세요",
                onBack = onBack,
                onRetry = onRetry
            )

            DetailUiState.Revoked -> DetailMessage(
                icon = R.drawable.nm_ic_file_x,
                iconTint = NmColor.Warning.C600,
                circle = NmColor.Warning.C50,
                title = "허가가 종료된 약이에요",
                description = "허가가 취소되거나 취하된 품목이라\n세부정보가 제공되지 않아요\n" +
                    "후보 선택과 식별 결과에는 영향이 없어요",
                onBack = onBack,
                onRetry = null
            )
        }
    }
}

@Composable
private fun DetailLoading() {
    val colors = NmTheme.semanticColors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(NmColor.Primary.C50, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = NmColor.Primary.C500,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "세부정보를 불러오고 있어요", style = MessageTitle, color = colors.textPrimary)
            Text(text = "잠시만 기다려 주세요", style = MessageHint, color = colors.textTertiary)
        }
    }
}

/** 없음·오류·허가 종료가 같은 틀을 쓴다 — 아이콘·색·문구·버튼만 다르다. */
@Composable
private fun DetailMessage(
    icon: Int,
    iconTint: Color,
    circle: Color,
    title: String,
    description: String,
    onBack: () -> Unit,
    onRetry: (() -> Unit)?
) {
    val colors = NmTheme.semanticColors
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(96.dp).background(circle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(42.dp)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = title, style = NoticeTitle, color = colors.textPrimary)
                Text(
                    text = description,
                    style = NoticeDesc,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 없는 것은 다시 눌러도 없다 — 오류일 때만 재시도를 준다.
            onRetry?.let { NmButtonPrimary(text = "다시 시도", onClick = it, modifier = Modifier.fillMaxWidth()) }
            NmButtonSecondary(text = "뒤로", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DetailBody(detail: PillDetail) {
    var tab by rememberSaveable { mutableStateOf(DocType.EFFECT) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DetailSummary(detail)
        DocTabs(selected = tab, onSelect = { tab = it })

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (tab == DocType.CAUTION) 8.dp else 12.dp)
        ) {
            val blocks = detail.document(tab)?.blocks.orEmpty()
            when {
                blocks.isEmpty() -> EmptyDoc()

                // 주의사항만 접어서 보여준다 — 원문이 길어 한 번에 펼치면 찾기 어렵다.
                tab == DocType.CAUTION -> CautionSections(blocks)

                else -> blocks.forEach { DocBlock(it) }
            }
        }

        ProductInfo(detail)
        DetailNotice()
    }
}

@Composable
private fun EmptyDoc() {
    Text(
        text = "이 항목은 허가정보에 없어요",
        style = MessageHint,
        color = NmTheme.semanticColors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)
    )
}

@Composable
private fun DocTabs(selected: DocType, onSelect: (DocType) -> Unit) {
    val colors = NmTheme.semanticColors
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Tabs.forEach { (type, label) ->
                DocTab(
                    label = label,
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun DocTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = if (selected) TabLabelOn else TabLabel,
            color = if (selected) NmColor.Primary.C600 else colors.textTertiary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) NmColor.Primary.C600 else Color.Transparent)
        )
    }
}

private val Tabs = listOf(
    DocType.EFFECT to "효능효과",
    DocType.DOSAGE to "용법용량",
    DocType.CAUTION to "사용상 주의사항"
)

private val MessageTitle = NmTypography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)
private val MessageHint = NmTypography.body.copy(fontSize = 13.sp)
private val NoticeTitle = NmTypography.heading2.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
private val NoticeDesc = NmTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.5.sp)
private val TabLabel = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
private val TabLabelOn = TabLabel.copy(fontWeight = FontWeight.Bold)
