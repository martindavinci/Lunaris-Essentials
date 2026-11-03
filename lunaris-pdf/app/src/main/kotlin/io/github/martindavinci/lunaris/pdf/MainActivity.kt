package io.github.martindavinci.lunaris.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.martindavinci.lunaris.pdf.feature.AppNavHost
import io.github.martindavinci.lunaris.pdf.open.OpenIntent
import io.github.martindavinci.lunaris.pdf.settings.AppLocales
import io.github.martindavinci.lunaris.pdf.ui.theme.LunarisPdfTheme
import io.github.martindavinci.lunaris.pdf.ui.theme.isDark

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Below Android 13 the in-app language is applied here; above, the system does it.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val fresh = OpenIntent.isFresh(recreated = savedInstanceState != null, flags = intent?.flags ?: 0)
        val startAtReader = fresh && open(intent)
        setContent {
            LaunchedEffect(viewModel) {
                viewModel.refusedDocuments.collect {
                    Toast.makeText(this@MainActivity, R.string.annotate_open_refused, Toast.LENGTH_LONG).show()
                }
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            val settings = state.settings ?: return@setContent
            val dark = settings.theme.isDark()
            LaunchedEffect(dark) {
                val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            LunarisPdfTheme(themeMode = settings.theme) {
                // A file that starts the app is prepared first, which takes moments, so that nothing
                // else shows. Once up, the screens stay: they keep the place of whatever comes later.
                var screensUp by remember { mutableStateOf(false) }
                if (!state.opening || screensUp) {
                    screensUp = true
                    AppNavHost(startAtReader, viewModel.readerRequests, onExit = ::finish)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        open(intent)
    }

    /** True if the intent asks to open a document. */
    private fun open(intent: Intent?): Boolean {
        val uri = OpenIntent.uriFrom(
            action = intent?.action,
            type = intent?.type,
            data = intent?.dataString,
            stream = intent?.streamUri()?.toString(),
        ) ?: return false
        viewModel.openFromIntent(uri)
        return true
    }

    /** Any app can send anything here: an extra of another type, or one that cannot be unpacked at all. */
    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    } catch (_: RuntimeException) {
        null
    }
}
