package app.nursemate.consent

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import app.nursemate.core.designsystem.NmColor

/**
 * 약관 전문을 **앱 안에서** 연다 — Custom Tab.
 *
 * ## WebView 를 쓰지 않는다
 * 여기서 여는 것은 법정 고지 문서다. WebView 로 그리면 렌더링·쿠키·TLS 오류 처리·뒤로가기
 * 히스토리·문서 안의 외부 링크를 전부 우리가 책임지게 되는데, 얻는 것은 상단 바를 직접
 * 그릴 수 있다는 것뿐이다. Custom Tab 은 기본 브라우저의 엔진을 그대로 쓰면서 앱 위에
 * 덮여 열려, **앱을 떠나지 않는다**는 목적만 정확히 채운다.
 *
 * ## 띄우지 못하면 외부 브라우저로 떨어진다
 * `launchUrl` 은 Custom Tab 을 지원하는 브라우저가 없으면 평범한 `ACTION_VIEW` 로 알아서
 * 내려간다. 다만 브라우저가 **아예 없는** 기기에서는 그마저 던지므로 감싼다 — 약관을 못
 * 열어도 동의 자체는 끝낼 수 있어야 한다(전문 확인은 「보기」를 누른 사람만의 선택이다).
 */
internal fun Context.openPolicy(url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    // https 가 아닌 주소는 열지 않는다. policyUrl 은 서버가 주는 값이고, 그 값이 어쩌다
    // intent:// 같은 스킴이 되면 「보기」가 임의의 앱을 띄우는 입구가 된다.
    if (!uri.scheme.equals("https", ignoreCase = true)) return

    // 상단 바 색은 **부탁**이지 지시가 아니다. 브라우저가 자체 다크 모드를 쓰고 있으면
    // 자기 색을 쓴다(삼성 인터넷에서 확인). 사용자가 고른 브라우저 설정을 덮지 않는 쪽이 맞다.
    val colors = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(NmColor.Primary.C500.toArgb())
        .build()

    val intent = CustomTabsIntent.Builder()
        // 문서 제목을 상단 바에 띄운다 — 「개인정보 국외 이전 및 제3자 제공」처럼 항목 이름과
        // 문서 이름이 다를 수 있어, 무엇을 보고 있는지는 문서가 말하게 둔다.
        .setShowTitle(true)
        // 주소 바를 스크롤로 감추지 않는다(기본값이지만 명시한다). 법정 고지 문서라 **어느
        // 도메인에서 온 글인지** 읽는 사람이 언제든 확인할 수 있어야 한다.
        .setUrlBarHidingEnabled(false)
        .setDefaultColorSchemeParams(colors)
        .build()

    runCatching { intent.launchUrl(this, uri) }
        .onFailure {
            // Custom Tab 도 못 띄우는 경우의 마지막 길. 이것도 실패하면 조용히 둔다.
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
}
