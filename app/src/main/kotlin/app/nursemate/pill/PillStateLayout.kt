@file:Suppress("MatchingDeclarationName") // 틀 함수 두 개와 그 버튼 묶음 — 파일 이름은 틀을 따른다

package app.nursemate.pill

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nursemate.core.designsystem.NmTheme
import app.nursemate.core.designsystem.v2.NmV2Button
import app.nursemate.core.designsystem.v2.NmV2ButtonKind
import app.nursemate.core.designsystem.v2.NmV2NavBar
import app.nursemate.ui.SystemBarIcons

/** 상태 화면 아래 버튼 하나 — 이름과 동작. */
internal class PillStateAction(val label: String, val onClick: () -> Unit)

/**
 * 알약 식별 상태 화면의 틀 — 정본 「알약 식별 · 촬영 ~ 로딩 · V2.1.0 (NM-586)」 ③ 권한 거부 · ⑥ 결과 없음 · ⑦ 분석 실패,
 * 「세부정보 · 이미지 비교 (NM-589)」 ⑩-e 데이터 없음 · ⑩-f 오류 · ⑩-g 허가 종료.
 *
 * 세 화면이 같은 뼈대다 — 내비 바 / 가운데 그림 + 제목 · 설명 / 아래 주 버튼 · 보조 버튼(모서리 12, 세로로 쌓음).
 * 하나로 두어야 셋의 여백 · 글자가 갈리지 않는다.
 *
 * @param navTitle 내비 바 제목. 정본은 ⑥ 만 `인식 결과` 이고 ③ · ⑦ 은 비어 있다
 * @param gap 그림과 글자 사이 — 정본 ③ 20 · ⑥ ⑦ 24
 * @param primary 주 버튼. 없으면 보조 버튼 하나만 둔다(세부정보 없음 · 허가 종료 — 다시 눌러도 같다)
 * @param visual 가운데 그림(아이콘 원 또는 사진 카드)
 * @param extra 설명 아래에 붙일 것 — 디버그 사유 등
 */
@Composable
internal fun PillStateLayout(
    navTitle: String,
    onBack: () -> Unit,
    title: String,
    description: String,
    primary: PillStateAction?,
    secondary: PillStateAction,
    modifier: Modifier = Modifier,
    gap: Dp = 24.dp,
    visual: @Composable () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = NmTheme.semanticColors
    SystemBarIcons(darkIcons = true)
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        NmV2NavBar(title = navTitle, onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            visual()
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = PillOutcomeTitle, color = colors.textPrimary, textAlign = TextAlign.Center)
                Text(
                    description,
                    style = PillOutcomeDescription,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                extra()
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            primary?.let { NmV2Button(it.label, it.onClick, Modifier.fillMaxWidth()) }
            NmV2Button(secondary.label, secondary.onClick, Modifier.fillMaxWidth(), NmV2ButtonKind.Secondary)
        }
    }
}

/** 가운데 아이콘 원 — 지름 96, 아이콘 42. */
@Composable
internal fun PillStateIcon(@DrawableRes icon: Int, background: Color, tint: Color) {
    Box(Modifier.size(96.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(42.dp))
    }
}
