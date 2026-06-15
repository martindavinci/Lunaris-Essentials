package tools.lunaris.texteditor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import tools.lunaris.texteditor.feature.AppNavHost
import tools.lunaris.texteditor.feature.settings.SettingsViewModel
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // The uri of a file the app was asked to open (VIEW/EDIT intent), or null for a normal launch.
    private val openUri = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openUri.value = uriFromIntent(intent)
        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(settings.fullscreen) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (settings.fullscreen) controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            LunarisEditorTheme(appTheme = settings.appTheme) {
                AppNavHost(openUri = openUri.value)
            }
        }
    }

    // Already-running app asked to open another file.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openUri.value = uriFromIntent(intent)
    }

    private fun uriFromIntent(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_EDIT) {
            intent.data?.toString()
        } else null
}
