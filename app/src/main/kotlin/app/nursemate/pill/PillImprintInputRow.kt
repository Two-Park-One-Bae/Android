package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmSymbols
import app.nursemate.core.designsystem.v2.NmV2Color
import app.nursemate.core.designsystem.v2.NmV2Type

/**
 * 각인 입력 줄 — 정본 ⑧ ~ ⑬ 「키보드 위 화면 폭」. 두 층이다 — 윗줄 `[입력 칸] [확인]`,
 * 아랫줄 `[원래 값] [모름] [각인 없음]`. 그 아래 기호 바([PillSymbolBar])가 붙는다.
 *
 * ## 왜 칸에서 바로 치지 않나
 * 각인 칩은 한 줄이라 몇 글자면 말줄임이다. 기호 바로 △ · ▽ 를 끼워 넣기도 한다. 화면 폭 줄에서 치고,
 * 칩에서는 결과만 본다.
 *
 * ## 라벨이 없다
 * 입력 칸이 비었을 때 안내 글자(`앞면 각인` · `뒷면 각인`)가 면을 알리고, 치는 동안은 카드에서 그 면의
 * 각인 칩을 강조한다(spec §각인 입력).
 *
 * ## 비면 `확인` 을 누를 수 없다
 * 각인 없는 알약은 `각인 없음`, 조건을 두지 않으려면 `모름` 을 누른다 — 둘 다 누르면 바로 줄을 닫는다.
 * 빈 칸 확인을 「없음」으로 읽던 출시된 앱은 사용자가 무심코 하드 조건을 걸었다(NM-516).
 *
 * @param original 사진에서 읽은 각인 — **고쳤을 때만** `원래 값` 을 보인다. 없으면 null
 */
@Suppress("LongParameterList")
@Composable
internal fun PillImprintInputRow(
    side: FaceSide,
    text: TextFieldValue,
    original: String?,
    onTextChange: (TextFieldValue) -> Unit,
    onConfirm: () -> Unit,
    onRevert: () -> Unit,
    onUnknown: () -> Unit,
    onNone: () -> Unit
) {
    val colors = NmTheme.semanticColors
    val focusRequester = remember { FocusRequester() }
    // 줄이 뜨는 이유가 「이제 친다」라서 키보드도 같이 올라와야 한다.
    LaunchedEffect(side) { focusRequester.requestFocus() }
    val canConfirm = text.text.isNotBlank()

    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(NmColor.Neutral.C200))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(shape)
                    .background(NmColor.Primary.C50)
                    .border(2.dp, NmColor.Primary.C500, shape)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.text.isEmpty()) {
                    Text(
                        if (side == FaceSide.Front) "앞면 각인" else "뒷면 각인",
                        style = FieldHint,
                        color = colors.textTertiary
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = FieldValue.copy(color = colors.textPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm() }),
                    cursorBrush = SolidColor(NmColor.Primary.C500),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canConfirm) NmColor.Primary.C500 else NmColor.Primary.C100)
                    .clickable(enabled = canConfirm, role = Role.Button, onClick = onConfirm)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text("확인", style = ConfirmLabel, color = NmColor.Neutral.C0)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (original != null) RowButton("원래 값", onRevert, icon = NmSymbols.RestartAlt)
            RowButton("모름", onUnknown)
            RowButton("각인 없음", onNone)
        }
    }
}

/** 아랫줄 버튼 — 볼록한 흰 판 · 모서리 8 · 글자 13/600. */
@Composable
private fun RowButton(label: String, onClick: () -> Unit, icon: Int? = null) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NmTheme.semanticColors.surface, NmV2Color.Raised.neutralBottom)))
            .border(1.dp, NmColor.Neutral.C200, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                null,
                tint = NmTheme.semanticColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(label, style = NmV2Type.chipConfirmed, color = NmTheme.semanticColors.textPrimary)
    }
}

/** 정본 16/800 — 각인은 굵게 읽혀야 한다. */
private val FieldValue = NmV2Type.button.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
private val FieldHint = NmV2Type.button.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal)
private val ConfirmLabel = NmV2Type.smallButton
