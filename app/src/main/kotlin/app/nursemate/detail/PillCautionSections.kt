package app.nursemate.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.R
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.model.Block
import app.nursemate.core.model.plainText

/*
 사용상 주의사항 — 디자인 `⑩-d 주의사항 (아코디언)`.

 이 문서만 접어서 보여준다. 원문이 길어(경고·금기·이상반응·상호작용…) 한 번에 펼치면
 스크롤만 수백 줄이 되고, 정작 찾으려던 항목에 닿기까지 오래 걸린다.

 접는 단위는 `HEADING` 이다 — 계약이 "HEADING 을 접기 단위로 쓸 수 있다"고 알려 준다.
*/

/** 제목 하나와 거기 딸린 본문. */
private data class CautionSection(val title: String, val body: List<Block>)

@Composable
internal fun ColumnScope.CautionSections(blocks: List<Block>) {
    val sections = blocks.toSections()

    // 제목 없이 본문만 오는 문서도 있다(폴백 문단 하나). 그때는 접지 않고 그대로 그린다.
    if (sections.isEmpty()) {
        blocks.forEach { DocBlock(it) }
        return
    }

    // 한 번에 하나만 펼친다. 여럿을 열어 두면 접어 놓은 뜻이 없어진다.
    var openIndex by rememberSaveable { mutableIntStateOf(0) }

    sections.forEachIndexed { index, section ->
        CautionCard(
            title = section.title,
            open = index == openIndex,
            onToggle = { openIndex = if (openIndex == index) NONE_OPEN else index },
            body = section.body
        )
    }
}

@Composable
private fun CautionCard(title: String, open: Boolean, onToggle: () -> Unit, body: List<Block>) {
    val colors = NmTheme.semanticColors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(
                onClick = onToggle
            ).padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = if (open) CardTitleOpen else CardTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(R.drawable.nm_ic_chevron_down),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f)
            )
        }

        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                body.forEach { block ->
                    if (block is Block.Paragraph) {
                        Text(
                            text = block.content.annotated(),
                            style = CardBody,
                            color = colors.textSecondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        DocBlock(block)
                    }
                }
            }
        }
    }
}

/**
 * 제목을 만날 때마다 새 칸을 열고, 다음 제목까지의 블록을 그 칸에 담는다.
 *
 * 첫 제목보다 앞에 오는 블록은 버리지 않고 **첫 칸에 얹는다** — 계약이 "원문 순서·내용
 * 무손실"이라 앱이 조용히 빠뜨리면 안 된다.
 */
private fun List<Block>.toSections(): List<CautionSection> {
    val sections = mutableListOf<CautionSection>()
    var title: String? = null
    var body = mutableListOf<Block>()

    forEach { block ->
        if (block is Block.Heading) {
            title?.let { sections += CautionSection(it, body) }
            title = block.content.plainText()
            body = mutableListOf()
        } else {
            body += block
        }
    }
    title?.let { sections += CautionSection(it, body) }

    val orphans = takeWhile { it !is Block.Heading }
    return when {
        sections.isEmpty() -> emptyList()

        orphans.isEmpty() -> sections

        else -> sections.mapIndexed { index, section ->
            if (index == 0) section.copy(body = orphans + section.body) else section
        }
    }
}

/** 아무 칸도 펼치지 않은 상태. */
private const val NONE_OPEN = -1

private val CardTitle = NmTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val CardTitleOpen = CardTitle.copy(fontWeight = FontWeight.Bold)
private val CardBody = NmTypography.body.copy(fontSize = 13.sp, lineHeight = 21.45.sp)
