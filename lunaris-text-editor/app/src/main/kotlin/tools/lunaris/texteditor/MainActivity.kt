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
import tools.lunaris.texteditor.feature.OpenRequest
import tools.lunaris.texteditor.feature.settings.SettingsViewModel
import tools.lunaris.texteditor.ui.theme.LunarisEditorTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // The file the app was asked to open (VIEW/EDIT intent), or null for a normal launch.
    private val openRequest = mutableStateOf<OpenRequest?>(null)

    // Bumped per VIEW/EDIT intent so re-opening the same file (open-with, app already running)
    // produces a distinct OpenRequest and re-triggers the editor's open effect.
    private var intentNonce = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (isFreshLaunchIntent(recreated = savedInstanceState != null, flags = intent?.flags ?: 0)) {
            openRequest.value = requestFromIntent(intent)
        }
        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(settings.fullscreen) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (settings.fullscreen) controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            LunarisEditorTheme(appTheme = settings.appTheme) {
                AppNavHost(openRequest = openRequest.value)
            }
        }
    }

    // Already-running app asked to open another file.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest.value = requestFromIntent(intent)
    }

    private fun requestFromIntent(intent: Intent?): OpenRequest? = when (intent?.action) {
        Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data?.let { fileRequest(intent, it) }
        Intent.ACTION_SEND -> {
            // Shared file → open it; shared plain text (no stream) → a new tab with that text.
            val stream = intent.getStreamUri()
            when {
                // A file shared *into* the editor is meant to be worked on: open it editable even
                // when the sender only granted read (if it can't be written back, the user Saves As).
                stream != null -> fileRequest(intent, stream, forceEditable = true)
                else -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?.let { OpenRequest(sharedText = it, nonce = ++intentNonce) }
            }
        }
        else -> null
    }

    private fun fileRequest(intent: Intent, uri: android.net.Uri, forceEditable: Boolean = false): OpenRequest {
        val canWrite = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
        // Best-effort: persist the offered grant so the file stays reachable later (many openers
        // don't offer a persistable grant — then this no-ops; the per-launch grant still applies).
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (canWrite) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
            .onFailure {
                if (canWrite) runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        // No write grant → open read-only so the user isn't surprised by a failing save, unless
        // the caller forces editable (a file shared into the editor).
        return OpenRequest(uri.toString(), readOnly = !canWrite && !forceEditable, nonce = ++intentNonce)
    }

    @Suppress("DEPRECATION")
    private fun Intent.getStreamUri(): android.net.Uri? =
        if (android.os.Build.VERSION.SDK_INT >= 33)
            getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        else getParcelableExtra(Intent.EXTRA_STREAM)
}
