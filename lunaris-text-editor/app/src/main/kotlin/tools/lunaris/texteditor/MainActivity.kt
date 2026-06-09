package tools.lunaris.texteditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import tools.lunaris.texteditor.feature.AppNavHost
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LunarisEditorTheme {
                AppNavHost()
            }
        }
    }
}
