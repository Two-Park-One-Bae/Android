package app.nursemate.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * NurseMate 테마 자리 — S4(NM-390)에서 iOS DSKit 토큰(Pretendard·컬러 램프·spacing·radius)으로 채운다.
 */
@Composable
fun NurseMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
