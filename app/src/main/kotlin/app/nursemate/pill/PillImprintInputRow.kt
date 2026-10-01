package app.nursemate.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 각인 입력 줄 — 정본 ⑦ 「키보드 위 화면 폭」.
 *
 * ## 왜 면 카드 칸에서 바로 치지 않나
 * 면 카드의 각인 칸은 약 8자다. 각인은 `ALX3`·`CKD` 같은 짧은 것도 있지만 여섯 자를 넘는
 * 것도 있고, 기호 바로 △·▽ 같은 글자를 끼워 넣기도 한다. 좁은 칸에서 치면 앞이 밀려 사라진다.
 *
 * 그래서 칸을 누르면 **화면 폭 줄**이 키보드 위로 올라온다. 어느 면을 치는 중인지 왼쪽
 * 라벨이 말하고(「앞면 각인」), 다 치면 오른쪽 확인으로 닫는다.
 *
 * ## 비우고 확인하면 **없음**이다
 * 조건을 푸는 것이 아니다 — 「각인이 없는 알약만」이라는 하드 조건이 된다. 조건을 풀려면
 * 면 카드 드롭다운의 「전체」를 고른다. 둘을 헷갈리게 만들면 사용자가 무심코 정답을 지운다
 * (NM-516).
 *
 * @param onConfirm 다 쳤다. 빈 문자열이면 「없음」으로 나간다
 */
@Composable
internal fun PillImprintInputRow(
    side: FaceSide,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onConfirm: () -> Unit
) {
    val colors = NmTheme.semanticColors
    val focusRequester = remember { FocusRequester() }

    // 줄이 뜨는 이유가 「이제 친다」라서 키보드도 같이 올라와야 한다.
    LaunchedEffect(side) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (side == FaceSide.Front) "앞면 각인" else "뒷면 각인",
            style = SideLabel,
            color = colors.textTertiary
        )
        val shape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(shape)
                .background(NmColor.Warning.C50)
                // 정본은 이 칸을 **열린 상태**로만 그린다 — 테두리가 primary-500 이다.
                .border(1.dp, NmColor.Primary.C500, shape)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = FieldValue.copy(color = colors.textPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                cursorBrush = SolidColor(NmColor.Primary.C500),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(NmColor.Primary.C500)
                .clickable(onClick = onConfirm)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(text = "확인", style = ConfirmLabel, color = NmColor.Neutral.C0)
        }
    }
}

// 정본 스케일에 없는 크기다. 화면이 요구하는 값이라 여기 명시한다.
private val SideLabel = NmTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
private val FieldValue = NmTypography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
private val ConfirmLabel = NmTypography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
