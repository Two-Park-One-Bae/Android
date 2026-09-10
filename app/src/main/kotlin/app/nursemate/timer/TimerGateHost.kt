package app.nursemate.timer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography
import app.nursemate.core.designsystem.R as DsR
import app.nursemate.core.model.AlertMode
import app.nursemate.timer.alarm.TimerPermissions

/**
 * 시작 관문 시트들을 띄우고, OS 쪽 절차를 대신 밟아 준다.
 *
 * ## 왜 화면이 권한을 받는가
 * 런타임 권한 팝업과 설정 화면 이동은 **Activity 가 있어야** 한다. ViewModel 은 "무엇이
 * 필요한지"만 [TimerGate] 로 말하고, 실제 요청은 여기서 한다.
 *
 * ## 돌아오면 다시 확인한다
 * 정확 알람은 팝업이 없어 시스템 설정으로 나갔다 와야 한다(Android 제약). 나갔다 온 결과는
 * 알 수 없으므로, 돌아온 순간 [TimerListViewModel.advance] 를 불러 **실제 권한을 다시 읽는다.**
 */
@Composable
fun TimerGateHost(
    gate: TimerGate,
    permissions: TimerPermissions,
    onAdvance: () -> Unit,
    onAsked: (PermissionStep) -> Unit,
    onConfirmAlertMode: (AlertMode) -> Unit,
    onDismiss: () -> Unit
) {
    // 설정에서 돌아오는 것 자체가 신호다 — 결과 코드는 의미가 없다.
    val settings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onAdvance() }

    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onAdvance() }

    when (gate) {
        is TimerGate.None -> Unit

        is TimerGate.Permission -> PermissionSheet(
            denied = gate.denied,
            onRequest = {
                onAsked(gate.step)
                when {
                    // 거부한 뒤에는 팝업을 다시 띄울 수 없다 — 앱 상세 설정이 유일한 길이다.
                    gate.step == PermissionStep.NOTIFICATION && !gate.denied ->
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)

                    gate.step == PermissionStep.EXACT_ALARM ->
                        settings.launch(permissions.exactAlarmSettings())

                    gate.step == PermissionStep.FULL_SCREEN ->
                        settings.launch(permissions.fullScreenIntentSettings())

                    else -> settings.launch(permissions.appDetailsSettings())
                }
            },
            onDismiss = onDismiss
        )

        is TimerGate.AlertMode -> {
            var picked by remember { mutableStateOf(AlertMode.SOUND) }
            TimerNoticeSheet(
                icon = DsR.drawable.nm_ic_bell_ring,
                iconTint = NmColor.Primary.C600,
                iconBackground = NmColor.Primary.C50,
                title = "알람을 어떻게 알릴까요?",
                body = "타이머가 끝날 때 알리는 방식이에요.\n설정에서 언제든 바꿀 수 있어요.",
                primaryLabel = "이대로 시작하기",
                onPrimary = { onConfirmAlertMode(picked) },
                secondaryLabel = "나중에 할게요",
                onSecondary = onDismiss,
                onDismiss = onDismiss,
                content = {
                    AlertModePicker(selected = picked, onSelect = { picked = it })
                    AlertModeNotice()
                }
            )
        }
    }
}

/**
 * A3 안내·거부 — 정본 두 프레임이 문구와 색만 다르고 뼈대가 같아 한 컴포저블로 둔다.
 *
 * [denied] 는 "한 번 요청했는데도 권한이 없다"는 뜻이다. 같은 안내를 반복하면 사용자가
 * 무엇이 잘못됐는지 모르므로, 문구를 바꾸고 설정으로 유도한다.
 */
@Composable
private fun PermissionSheet(denied: Boolean, onRequest: () -> Unit, onDismiss: () -> Unit) {
    TimerNoticeSheet(
        icon = if (denied) DsR.drawable.nm_ic_bell_off else DsR.drawable.nm_ic_bell,
        iconTint = if (denied) NmColor.Error.C600 else NmColor.Primary.C600,
        iconBackground = if (denied) NmColor.Error.C50 else NmColor.Primary.C50,
        title = if (denied) "알람이 꺼져 있어요" else "알람 권한이 필요해요",
        body = if (denied) DENIED_BODY else NOTICE_BODY,
        primaryLabel = if (denied) "설정에서 켜기" else "허용하고 시작하기",
        onPrimary = onRequest,
        secondaryLabel = if (denied) "닫기" else "나중에 할게요",
        onSecondary = onDismiss,
        onDismiss = onDismiss
    )
}

/** 정본 `무음 안내` — 12 / text-tertiary / 가운데 정렬. */
@Composable
private fun AlertModeNotice() {
    Text(
        text = ALERT_MODE_NOTICE,
        style = NmTypography.caption.copy(lineHeight = 17.sp),
        color = NmTheme.semanticColors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp)
    )
}

private const val NOTICE_BODY =
    "타이머가 끝나면 시계 알람처럼 울려요.\n알람을 허용해야 타이머를 시작할 수 있어요."
private const val DENIED_BODY =
    "알람이 꺼져 있으면 타이머를 시작할 수 없어요.\n설정에서 널스메이트 알람을 허용해주세요."
