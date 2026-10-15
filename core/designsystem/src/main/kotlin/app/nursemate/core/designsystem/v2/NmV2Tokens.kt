package app.nursemate.core.designsystem.v2

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * V2.1.0 변수 — 정본은 `spec/design/DESIGN.pen` 의 변수, 뜻은 `spec/design/README.md`「V2.1.0 변수」.
 *
 * 기존 변수([app.nursemate.core.designsystem.NmColor] 등)는 **값을 바꾸지 않고** 그대로 쓴다.
 * 여기에는 V2.1.0 이 새로 더한 것만 둔다. 이름은 디자인 변수 이름을 그대로 옮겼다
 * (`glass-fill-top` → `NmV2Color.Glass.fillTop`).
 *
 * 다크 모드 값은 V2.1.0 범위 밖이다(NM-580 결정 1) — 라이트 값만 있다.
 */
object NmV2Color {

    /** V2.1.0 경고색(살구). `warning-*` 은 MVP · V1 화면용으로 남는다(NM-580 결정 9). */
    object Caution {
        val C50 = Color(0xFFFFF8F1)
        val C300 = Color(0xFFFFDCC2)
        val C500 = Color(0xFFF28C4B)
        val C600 = Color(0xFFE0742F)
        val C700 = Color(0xFFA9541C)
    }

    /** 유리 면 — 면 색 · 무지갯빛 테두리 · 안쪽 그림자, 뜨는 면의 대체 면. */
    object Glass {
        val fillTop = Color(0x8CFFFFFF)
        val fillBottom = Color(0x4DFFFFFF)
        val highlight = Color(0xCCFFFFFF)
        val sheen = Color(0x73FFFFFF)
        val strokeWhite = Color(0xFFFFFFFF)
        val strokeSky = Color(0x80E0F2FE)
        val strokeClear = Color(0x33FFFFFF)
        val strokeAmber = Color(0x40FDE68A)
        val innerEdge = Color(0xFFFFFFFF)
        val innerGlow = Color(0xB3FFFFFF)
        val innerShade = Color(0x26475569)

        /** Android 는 뜨는 조작부를 흐림 대신 이 면으로 그린다(NM-580 결정 4). */
        val fallback = Color(0xEBFFFFFF)
        val tintPrimaryTop = Color(0xEB0EA5E9)
        val tintPrimaryBottom = Color(0xD90EA5E9)
    }

    /** 볼록 버튼의 윗면 · 아랫면 · 그림자 색. */
    object Raised {
        val neutralBottom = Color(0xFFF6F8FA)
        val highlight = Color(0x33FFFFFF)
        val primaryTop = Color(0xFF16AAEA)
        val primaryBottom = Color(0xFF0B9CDE)
        val primaryShadow = Color(0x330284C7)
        val cautionTop = Color(0xFFE27934)
        val cautionBottom = Color(0xFFD76D29)
        val cautionShadow = Color(0x33C65F1E)
        val errorTop = Color(0xFFF04A4A)
        val errorBottom = Color(0xFFE63E3E)
        val errorShadow = Color(0x33DC2626)
    }

    /** 그림자 색. y · 흐림은 [NmV2Shadow]. */
    object Shadow {
        val card = Color(0x1F0F172A)
        val cardContact = Color(0x1A0F172A)
        val list = Color(0x170F172A)
        val listContact = Color(0x140F172A)
        val small = Color(0x140F172A)
    }

    /** 촬영 화면 바탕. */
    val cameraBg = Color(0xFF000000)
}

/** 모서리. 기존 `radius-sm/md/lg/xl`(8 · 12 · 16 · 24)에 V2.1.0 이 4 · 20 · 28 · 999 를 더했다. */
object NmV2Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val card = 20.dp
    val xl = 24.dp
    val x2l = 28.dp
    val full = 999.dp
}

/**
 * 그림자 y · 흐림. 카드용과 목록 카드용을 나눈다 — 촘촘히 쌓이는 목록에 카드용을 쓰면 카드 사이가
 * 어둡게 번진다(NM-580 결정 8). `contact` 는 두 쪽 모두 아래에 깔리는 얕은 접지 그림자다.
 */
object NmV2Shadow {
    val cardY = 14.dp
    val cardBlur = 34.dp
    val listY = 8.dp
    val listBlur = 22.dp
    val contactY = 1.dp
    val contactBlur = 2.dp
    val smallY = 2.dp
    val smallBlur = 6.dp
}

/** 유리 테두리 두께 · 뜨는 면 흐림. */
object NmV2Glass {
    val strokeWidth = 1.8.dp
    val strokeWidthSmall = 1.2.dp

    /** 디자인 값. Android 는 [NmV2Color.Glass.fallback] 으로 그리므로 지금 쓰는 곳은 없다. */
    val blurFloating = 22.dp
}
