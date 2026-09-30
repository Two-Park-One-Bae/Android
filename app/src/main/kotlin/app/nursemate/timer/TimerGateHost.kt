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
    onDismiss: () -> Unit,
    /**
     * 알림 권한 팝업의 응답. 지표만 쓴다.
     *
     * 정확 알람·전체화면은 팝업이 아니라 **설정 화면 왕복**이라 결과를 알 수 없다
     * (Android 제약). 그래서 집계되는 것은 알림 권한 하나뿐이다.
     */
    onPermissionResult: (Boolean) -> Unit = {}
) {
    // 설정에서 돌아오는 것 자체가 신호다 — 결과 코드는 의미가 없다.
    val settings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onAdvance() }

    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionResult(granted)
        onAdvance()
    }

    when (gate) {
        is TimerGate.None -> Unit

        is TimerGate.Permission -> PermissionSheet(
            step = gate.step,
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
 * ## 단계마다 문구를 가른다 (NM-530)
 * 예전에는 [step] 을 받지 않아 **세 단계가 글자 하나 다르지 않은 같은 시트**를 그렸다.
 * 알림을 허용하고 돌아오면 `denied=false` 인 새 단계라 똑같은 화면이 다시 떴고, 사용자에게는
 * 「허용했는데 또 물어본다」로 읽혔다.
 *
 * 문구 규칙은 둘이다.
 * - **제목은 무엇을 켜야 하는지** 말한다. 이때 **OS 설정 화면에 적힌 항목 이름을 그대로 쓴다** —
 *   설정으로 나간 사용자가 찾아야 할 글자와 같아야 한다. 단계마다 이름이 다르니 반복 느낌도
 *   함께 사라진다. 실기기에서 확인한 One UI 표기다(S24 · Android 16).
 * - **본문은 왜 필요한지 한 줄.** 어디서 켜는지는 버튼과 열린 화면이 이미 말한다 —
 *   인텐트에 `package:` 가 붙어 **앱 전용 페이지로 바로 떨어지므로**(토글 하나뿐) 「설정에서
 *   널스메이트를 찾아」같은 안내는 없는 절차를 시키는 셈이다.
 *
 * [denied] 는 "한 번 요청했는데도 권한이 없다"는 뜻이다.
 */
@Composable
private fun PermissionSheet(step: PermissionStep, denied: Boolean, onRequest: () -> Unit, onDismiss: () -> Unit) {
    TimerNoticeSheet(
        icon = if (denied) DsR.drawable.nm_ic_bell_off else DsR.drawable.nm_ic_bell,
        iconTint = if (denied) NmColor.Error.C600 else NmColor.Primary.C600,
        iconBackground = if (denied) NmColor.Error.C50 else NmColor.Primary.C50,
        title = step.title(denied),
        body = step.body(denied),
        primaryLabel = if (denied) "설정에서 켜기" else step.primaryLabel(),
        onPrimary = onRequest,
        secondaryLabel = if (denied) "닫기" else "나중에 할게요",
        onSecondary = onDismiss,
        onDismiss = onDismiss
    )
}

internal fun PermissionStep.title(denied: Boolean): String = when (this) {
    PermissionStep.NOTIFICATION -> if (denied) "알림이 꺼져 있어요" else "알림을 켜 주세요"

    PermissionStep.EXACT_ALARM ->
        if (denied) "알람 및 리마인더가 꺼져 있어요" else "알람 및 리마인더를 켜 주세요"

    PermissionStep.FULL_SCREEN ->
        if (denied) "전체 화면 알림이 꺼져 있어요" else "전체 화면 알림을 켜 주세요"
}

internal fun PermissionStep.body(denied: Boolean): String = when (this) {
    PermissionStep.NOTIFICATION ->
        if (denied) "알림을 켜야 타이머를 시작할 수 있어요." else "타이머가 끝나면 알림으로 알려드려요."

    PermissionStep.EXACT_ALARM ->
        if (denied) "이 설정이 없으면 정한 시각에 울리지 못해요." else "이 설정이 있어야 정한 시각에 울려요."

    // 권한이 없어도 알람이 사라지지는 않는다 — 헤드업 알림으로 낮춰 표시된다(AndroidManifest 주석).
    // 「안 울려요」로 쓰면 틀린다.
    PermissionStep.FULL_SCREEN ->
        if (denied) "잠금화면에는 안 뜨고 알림으로만 와요." else "잠금화면에서도 알람 화면이 바로 떠요."
}

/** 알림만 앱 안에서 팝업으로 받고, 나머지 둘은 설정 화면으로 나간다. 버튼이 그 차이를 말한다. */
internal fun PermissionStep.primaryLabel(): String = if (this == PermissionStep.NOTIFICATION) "알림 켜기" else "설정 열기"

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
