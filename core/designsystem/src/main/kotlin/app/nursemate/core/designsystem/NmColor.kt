package app.nursemate.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * NurseMate 컬러 램프 — iOS DSKit(Colors.xcassets) 정의값과 1:1 일치.
 * 시맨틱 색(배경·텍스트·보더)은 [NmSemanticColors] 참고.
 */
object NmColor {

    object Primary {
        val C50 = Color(0xFFF0F9FF)
        val C100 = Color(0xFFE0F2FE)
        val C300 = Color(0xFF7DD3FC)
        val C500 = Color(0xFF0EA5E9)
        val C600 = Color(0xFF0284C7)
        val C700 = Color(0xFF0369A1)
        val C900 = Color(0xFF0C4A6E)
    }

    object Secondary {
        val C50 = Color(0xFFF0FDFA)
        val C100 = Color(0xFFCCFBF1)
        val C300 = Color(0xFF5EEAD4)
        val C500 = Color(0xFF14B8A6)
        val C600 = Color(0xFF0D9488)
        val C700 = Color(0xFF0F766E)
        val C900 = Color(0xFF134E4A)
    }

    object Neutral {
        val C0 = Color(0xFFFFFFFF)
        val C50 = Color(0xFFF8FAFC)
        val C100 = Color(0xFFF1F5F9)
        val C200 = Color(0xFFE2E8F0)
        val C300 = Color(0xFFCBD5E1)
        val C400 = Color(0xFF94A3B8)
        val C500 = Color(0xFF64748B)
        val C600 = Color(0xFF475569)
        val C700 = Color(0xFF334155)
        val C900 = Color(0xFF0F172A)
    }

    object Success {
        val C50 = Color(0xFFF0FDF4)
        val C100 = Color(0xFFDCFCE7)
        val C300 = Color(0xFF86EFAC)
        val C500 = Color(0xFF22C55E)
        val C600 = Color(0xFF16A34A)
        val C700 = Color(0xFF15803D)
        val C900 = Color(0xFF14532D)
    }

    object Warning {
        val C50 = Color(0xFFFEF3C7)
        val C100 = Color(0xFFFDE68A)
        val C300 = Color(0xFFFCD34D)
        val C500 = Color(0xFFF59E0B)
        val C600 = Color(0xFFD97706)
        val C700 = Color(0xFFB45309)
        val C900 = Color(0xFF78350F)
    }

    object Error {
        val C50 = Color(0xFFFEF2F2)
        val C100 = Color(0xFFFEE2E2)
        val C300 = Color(0xFFFCA5A5)
        val C500 = Color(0xFFEF4444)
        val C600 = Color(0xFFDC2626)
        val C700 = Color(0xFFB91C1C)
        val C900 = Color(0xFF7F1D1D)
    }

    object Info {
        val C50 = Color(0xFFEFF6FF)
        val C100 = Color(0xFFDBEAFE)
        val C300 = Color(0xFF93C5FD)
        val C500 = Color(0xFF3B82F6)
        val C600 = Color(0xFF2563EB)
        val C700 = Color(0xFF1D4ED8)
        val C900 = Color(0xFF1E3A8A)
    }
}

/**
 * 시맨틱 색 — iOS DSColor의 Light/Dark 자동 전환 6종 대응.
 */
data class NmSemanticColors(
    val bgApp: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color
) {
    companion object {
        val Light = NmSemanticColors(
            bgApp = Color(0xFFEEF2F7),
            surface = NmColor.Neutral.C0,
            textPrimary = Color(0xFF1E293B),
            textSecondary = NmColor.Neutral.C500,
            textTertiary = NmColor.Neutral.C400,
            border = NmColor.Neutral.C200
        )

        val Dark = NmSemanticColors(
            bgApp = NmColor.Neutral.C900,
            surface = Color(0xFF1E293B),
            textPrimary = NmColor.Neutral.C50,
            textSecondary = NmColor.Neutral.C400,
            textTertiary = NmColor.Neutral.C500,
            border = NmColor.Neutral.C700
        )
    }
}
