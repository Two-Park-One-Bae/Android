package app.nursemate.remoteconfig

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nursemate.BuildConfig
import app.nursemate.R
import app.nursemate.core.designsystem.NmButtonPrimary
import app.nursemate.core.designsystem.NmColor
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.NmTypography

/**
 * 원격 값에 따라 앱 전체를 덮는 차단 화면 (NM-448).
 *
 * ## 정본이 없다
 * `DESIGN.pen` 86개 프레임에 이 화면이 **없다**(2026-09-19 전수 확인). spec 에도 요구사항이
 * 없다 — 티켓 NM-448 이 그 점을 문제로 적고 「운영 절차를 spec 에 기재」를 완료조건에 두고 있다.
 * 지금은 iOS 구현(`RemoteConfigGate/`)을 옮기고 문구를 그대로 쓴다. 정본이 생기면 그쪽이 이긴다.
 *
 * ## 우선순위: 강제 업데이트 > 점검
 * 둘 다 켜져 있으면 업데이트를 먼저 보여 준다. 구버전을 쓰는 사용자에게 「점검 중」이라고만
 * 알리면 점검이 끝난 뒤에도 그 버전으로 계속 들어오게 된다.
 *
 * ## 뒤로가기를 먹는다
 * 차단이 목적이라 [BackHandler] 로 막는다. 막지 않으면 게이트 뒤의 화면이 그대로 드러난다.
 */
@Composable
fun RemoteConfigGate(state: RemoteConfigState) {
    when {
        state.forceUpdateRequired -> ForceUpdateScreen(state.playStoreUrl)
        state.maintenanceMode -> MaintenanceScreen(state.maintenanceMessage)
    }
}

@Composable
private fun ForceUpdateScreen(playStoreUrl: String) {
    val context = LocalContext.current
    GateScaffold(
        iconRes = R.drawable.nm_ic_zap,
        iconTint = NmColor.Primary.C500,
        iconBackground = NmColor.Primary.C50,
        title = "업데이트가 필요해요",
        description = "원활한 사용을 위해\n최신 버전으로 업데이트해주세요.",
        action = "업데이트하기" to {
            // 원격 값이 있으면 그 주소로, 없으면 `market://` 로 우리 앱을 연다.
            // 스토어 앱이 없는 기기(에뮬레이터 등)를 대비해 웹 주소로 한 번 더 떨어진다.
            val target = playStoreUrl.ifEmpty { "market://details?id=${BuildConfig.APPLICATION_ID}" }
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                .recoverCatching {
                    if (it !is ActivityNotFoundException) throw it
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_WEB_URL + BuildConfig.APPLICATION_ID))
                    )
                }
        }
    )
}

@Composable
private fun MaintenanceScreen(message: String) {
    GateScaffold(
        iconRes = R.drawable.nm_ic_triangle_alert,
        iconTint = NmColor.Warning.C500,
        iconBackground = NmColor.Warning.C50,
        title = "서비스 점검 중이에요",
        // 콘솔에서 문구를 안 내려줄 수도 있다. 그때 빈 화면을 보여 주면 안 된다.
        description = message.ifEmpty { "더 나은 서비스를 위해 점검 중입니다.\n잠시 후 다시 이용해주세요." },
        action = null
    )
}

/** 두 화면이 같은 뼈대를 쓴다 — `⑦ 분석 실패`(`PillAnalysisFailedScreen`)의 배치를 따른다. */
@Composable
private fun GateScaffold(
    iconRes: Int,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    description: String,
    action: Pair<String, () -> Unit>?
) {
    val colors = NmTheme.semanticColors

    // 차단이 목적이다. 뒤로가기로 빠져나가면 게이트가 무의미해진다.
    BackHandler(enabled = true) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(96.dp).background(iconBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(42.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = title, style = GateTitle, color = colors.textPrimary)
                Text(
                    text = description,
                    style = GateDescription,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (action != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp)
            ) {
                NmButtonPrimary(
                    text = action.first,
                    onClick = action.second,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private const val PLAY_WEB_URL = "https://play.google.com/store/apps/details?id="

/**
 * 결과 없음(⑥)·분석 실패(⑦)가 쓰는 값과 같게 맞춘다 — 전체화면 안내는 한 톤이어야 한다.
 * 정본에 이 화면이 없어 새 스케일을 만들지 않고 기존 값을 따른다.
 */
private val GateTitle: TextStyle =
    NmTypography.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)

private val GateDescription: TextStyle =
    NmTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.5.sp)
