package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 키보드 위 기호 바 — 디자인 `⑧-e / 기호 바`.
 *
 * 알약 각인에는 △▽○ 같은 기호가 흔한데 안드로이드 기본 키보드에서 이걸 꺼내려면 몇 단계를 들어가야
 * 한다. 그래서 자주 쓰는 것만 한 줄로 꺼내 둔다.
 *
 * 좁은 기기에서는 11개가 한 줄에 안 들어가 가로로 흐른다 — 줄바꿈을 하면 키보드가 밀려 올라간다.
 */
@Composable
fun PillSymbolBar(onSymbol: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.semanticColors
    Box(modifier = modifier.fillMaxWidth().background(BarBackground)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Symbols.forEach { symbol ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.surface)
                        .clickable { onSymbol(symbol) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = symbol, style = SymbolText, color = colors.textPrimary)
                }
            }
        }
    }
}

/**
 * 커서 자리에 기호를 끼워 넣는다.
 *
 * 끝에 붙이지 않는다 — 사용자가 `MK` 를 적고 앞에 `△` 를 넣으려 커서를 옮겼을 수 있다.
 */
fun TextFieldValue.insert(symbol: String): TextFieldValue {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(start, text.length)
    val next = text.replaceRange(start, end, symbol)
    return TextFieldValue(
        text = next,
        selection = TextRange(start + symbol.length)
    )
}

/** 정본이 고른 11개. 순서도 정본 그대로다. */
private val Symbols = listOf("△", "▽", "○", "∧", "∨", "∩", "∪", "★", "☆", "↑", "♡")

private val BarBackground = Color(0xFFE4E7EC)
private val SymbolText = NmTypography.bodyLarge.copy(fontSize = 16.sp)
