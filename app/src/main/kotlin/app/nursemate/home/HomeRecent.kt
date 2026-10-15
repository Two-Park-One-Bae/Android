package app.nursemate.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2CardShape
import app.nursemate.core.designsystem.v2.NmV2Surface
import app.nursemate.core.designsystem.v2.NmV2SurfaceStyle
import app.nursemate.core.designsystem.v2.NmV2Type

/** 홈 최근 식별 카드에 보일 것. 식별 기록(NM-555)이 붙기 전에는 [None] 만 온다. */
sealed interface HomeRecent {
    /** 기록이 없다 — `아직 식별 기록이 없어요`. */
    data object None : HomeRecent

    /** 불러오지 못했다 — `기록을 불러오지 못했어요`. */
    data object Failed : HomeRecent

    /** 가장 최근 한 건. [time] 은 「오후 2:14」처럼 이미 다듬은 식별 시각이다. */
    data class Item(val historyId: String, val time: String, val title: String) : HomeRecent
}

// ── 최근 식별 ────────────────────────────────────────────────────────

/** 가장 최근 기록 한 건. 기록이 없거나 불러오지 못하면 카드를 누르지 않는다. */
@Composable
internal fun RecentCard(recent: HomeRecent, onRecent: (String) -> Unit) {
    val colors = NmTheme.semanticColors
    val item = recent as? HomeRecent.Item
    NmV2Surface(NmV2SurfaceStyle.ListCard, Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(NmV2CardShape)
                .clickable(enabled = item != null, role = Role.Button) { item?.let { onRecent(it.historyId) } }
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(NmSymbols.History), null, tint = NmColor.Neutral.C700, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    if (item != null) "최근 식별 · ${item.time}" else "최근 식별",
                    style = NmV2Type.rowSubtitle,
                    color = colors.textSecondary
                )
                when (recent) {
                    is HomeRecent.Item -> RecentTitle(recent.title)
                    HomeRecent.None -> Text("아직 식별 기록이 없어요", style = NmV2Type.field, color = colors.textTertiary)
                    HomeRecent.Failed -> Text("기록을 불러오지 못했어요", style = NmV2Type.field, color = colors.textTertiary)
                }
            }
            Icon(
                painterResource(NmSymbols.ChevronRight),
                null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 첫 품목명과 `외 N`. 넘치면 **품목명만** 말줄임하고 `외 N` 은 남긴다(spec home §최근 식별 카드).
 * [title] 은 「타이레놀정 500mg 외 3」 꼴로 온다.
 */
@Composable
private fun RecentTitle(title: String) {
    val colors = NmTheme.semanticColors
    val split = title.lastIndexOf(" 외 ")
    Row {
        Text(
            if (split > 0) title.substring(0, split) else title,
            style = NmV2Type.rowTitle,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (split > 0) Text(title.substring(split), style = NmV2Type.rowTitle, color = colors.textPrimary, maxLines = 1)
    }
}
