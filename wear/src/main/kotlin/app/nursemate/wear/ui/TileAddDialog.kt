package app.nursemate.wear.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.Text

/**
 * 「타일에 추가할까요?」 — 워치에서 처음 타이머를 시작한 직후 한 번 묻는다.
 *
 * ## 왜 물어만 보는가
 * 앱이 타일을 대신 꽂을 수 없다(`TileInstallation` 참고). 할 수 있는 건 「타일 추가」 목록을
 * 열어 주는 것까지라, [추가]를 눌러도 마지막 한 번은 사용자가 목록에서 널스메이트를 고른다.
 *
 * ## 왜 [AlertDialog] 인가
 * 손으로 짠 오버레이(`NoticeOverlay`)를 두 버튼으로 늘리면 **아래 버튼이 곡면에 먹힌다** —
 * 이 워치는 203dp 원형이라 중심에서 멀어질수록 쓸 수 있는 폭이 급히 줄고, W2 에서 같은 문제를
 * 겪었다. 이 컴포넌트는 원형에 맞춘 버튼 배치와 넘칠 때의 스크롤을 이미 갖고 있다.
 */
@Composable
fun TileAddDialog(prompt: TilePrompt, onDismiss: () -> Unit) {
    val context = LocalContext.current
    if (prompt.intent == null) {
        ManualGuide(onDismiss = onDismiss)
    } else {
        AlertDialog(
            true,
            onDismissRequest = onDismiss,
            confirmButton = {
                AlertDialogDefaults.ConfirmButton(
                    onClick = {
                        openTileList(context, prompt)
                        onDismiss()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = WearTimerColors.Primary,
                        contentColor = WearTimerColors.OnWarning
                    )
                )
            },
            dismissButton = {
                AlertDialogDefaults.DismissButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = WearTimerColors.Card,
                        contentColor = WearTimerColors.OnBackground
                    )
                )
            },
            title = { Text("타일에 추가할까요?") },
            text = { Text("앱을 열지 않고 손목에서 바로 시작할 수 있어요.") }
        )
    }
}

/**
 * 「타일 추가」 화면이 없는 워치 — 직접 추가하는 방법만 알린다.
 *
 * 목록을 여는 액션이 표준이 아니라 제조사에 따라 없다. 그때 버튼만 없애고 말면 무엇을 하라는
 * 건지 알 수 없어, 손으로 찾아가는 길을 대신 적는다.
 */
@Composable
private fun ManualGuide(onDismiss: () -> Unit) {
    AlertDialog(
        true,
        onDismissRequest = onDismiss,
        edgeButton = {
            AlertDialogDefaults.EdgeButton(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WearTimerColors.Card,
                    contentColor = WearTimerColors.OnBackground
                )
            ) { Text("확인") }
        },
        title = { RoundSafeText("타일에 추가해보세요") },
        text = { RoundSafeText("타일 화면을 길게 누르고 [+] 에서 널스메이트를 고르면, 앱을 열지 않고 바로 시작할 수 있어요.") }
    )
}

/**
 * 다이얼로그 안의 글을 곡면에서 물러나게 한다.
 *
 * ⚠️ [AlertDialog] 가 원형 여백을 이미 갖고 있지만 **긴 한국어 문장에는 모자란다.**
 * 180dp 워치에서 제목 「타일에 추가해보세요」의 첫 글자가 왼쪽 곡면에 잘렸다.
 * 영문 기준으로 잡힌 여백이라 어절이 긴 한글에서는 줄이 폭을 꽉 채우기 때문이다.
 *
 * 화면 폭에 비례한 값을 한 겹 더 준다 — 큰 워치에서는 차이가 거의 없고, 작은 워치에서만 는다.
 */
@Composable
private fun RoundSafeText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = roundSafeHorizontal(HORIZONTAL_PADDING_FRACTION))
    )
}

/**
 * 「타일 추가」 목록을 연다.
 *
 * 시스템 화면이라 우리 앱이 사라진다. 여기서 돌아왔는지·실제로 붙였는지는 확인하지 않는다 —
 * 어차피 다시 묻지 않기로 했고, 붙였는지는 다음에 열 때 조회로 알 수 있다.
 */
private fun openTileList(context: Context, prompt: TilePrompt) {
    prompt.intent?.let { runCatching { context.startActivity(it) } }
}
