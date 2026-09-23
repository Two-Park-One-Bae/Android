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
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.Text
import app.nursemate.wear.ui.WearTimerType.wrapKorean

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
            title = { Text("타일에 추가할까요?", style = LocalTextStyle.current.wrapKorean()) },
            text = { Text("앱을 열지 않고 손목에서 바로 시작할 수 있어요.", style = LocalTextStyle.current.wrapKorean()) }
        )
    }
}

/**
 * 「타일 추가」 화면이 없는 워치 — **이득만 알리고 방법은 적지 않는다.**
 *
 * 목록을 여는 액션이 표준이 아니라 제조사에 따라 없다. 예전엔 그때를 대비해 손으로 찾아가는
 * 길(「길게 눌러 [+] 에서…」)을 적어 두었는데, **큰 글꼴 + 작은 워치에서 그 설명이 화면을
 * 넘겨 [확인] 을 밖으로 밀어냈다.** 안내를 읽히려다 닫지도 못하게 만든 셈이다
 * (192dp · 글꼴 1.24 에서 세 번 스크롤해도 버튼에 닿지 못했다).
 *
 * 타일 편집은 Wear 의 표준 제스처라 문구 없이도 찾을 수 있다고 보고, 위쪽 [TileAddDialog] 와
 * 같은 한 줄만 남긴다.
 *
 * ⚠️ **제목은 「타일에 추가」다 — 더 길게 쓰지 않는다.** 「타일에 추가해보세요」로 두었더니
 * 192dp · 글꼴 1.24 에서 「추가해보세요」 한 어절이 한 줄보다 넓어, 어절 단위 줄바꿈
 * ([WearTimerType.wrapKorean])으로도 못 살리고 `타일에 추 / 가해보세요` 로 끊겼다.
 * 어절이 줄보다 길면 규칙이 손쓸 수 없다 — 그때는 말을 줄이는 수밖에 없다.
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
        title = { RoundSafeText("타일에 추가") },
        text = { Text("앱을 열지 않고 손목에서 바로 시작할 수 있어요.", style = LocalTextStyle.current.wrapKorean()) }
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
 *
 * ⚠️ **제목에만 준다.** 본문까지 주면 줄이 더 접혀 내용이 길어지고, 그만큼 아래 [확인]
 * ([AlertDialogDefaults.EdgeButton])이 화면 밖으로 밀려난다. 180dp 에서 실제로 그랬다 —
 * 잘린 제목을 살리려다 버튼을 잘라먹는 맞바꿈이 된다. 잘리던 쪽은 제목이었다.
 */
@Composable
private fun RoundSafeText(text: String) {
    Text(
        text = text,
        style = LocalTextStyle.current.wrapKorean(),
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
