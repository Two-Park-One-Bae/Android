package app.nursemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.nursemate.core.designsystem.NurseMateTheme
import app.nursemate.navigation.NurseMateApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템 바 뒤까지 그린다. 없으면 상·하단에 회색 띠가 남는다.
        enableEdgeToEdge()
        setContent { NurseMateTheme { NurseMateApp() } }
    }
}
