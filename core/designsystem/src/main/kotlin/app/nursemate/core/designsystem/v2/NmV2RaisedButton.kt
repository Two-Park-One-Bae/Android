package app.nursemate.core.designsystem.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmColor

/**
 * 볼록 면에 내용을 직접 놓는 버튼 — 글자 크기 · 여백 · 글자색이 [NmV2Button] 과 다른 자리가 쓴다
 * (타이머 카드 안 작은 버튼 · 분류 고르기 · 프리셋 행 등).
 *
 * [content] 는 지금 상태(기본 · 누름 · 비활성)의 글자색을 받는다. 다른 색을 쓰는 자리(`재개` 의
 * `primary-700` 등)는 그 색을 버리고 제 색을 칠한다.
 *
 * [selected] 면 테두리를 `primary-500` 1.5 로 바꾼다 — 정본 분류 고르기의 고른 칸이다.
 */
@Composable
fun NmV2RaisedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: NmV2ButtonKind = NmV2ButtonKind.Secondary,
    enabled: Boolean = true,
    selected: Boolean = false,
    shape: Shape = NmV2ButtonShape,
    contentPadding: PaddingValues = ButtonPadding,
    content: @Composable RowScope.(contentColor: Color) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = kind.look(pressed = pressed, enabled = enabled)
    val look = if (selected) base.copy(stroke = NmColor.Primary.C500, strokeWidth = SelectedStroke) else base
    Row(
        modifier = modifier
            .v2Raised(look, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content(look.content)
    }
}

private val SelectedStroke = 1.5.dp
