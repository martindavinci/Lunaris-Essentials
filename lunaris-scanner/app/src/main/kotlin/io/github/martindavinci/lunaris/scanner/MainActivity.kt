package io.github.martindavinci.lunaris.scanner

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.martindavinci.lunaris.scanner.feature.AppNavHost
import io.github.martindavinci.lunaris.scanner.feature.AppShortcut
import io.github.martindavinci.lunaris.scanner.settings.AppLocales
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme
import io.github.martindavinci.lunaris.scanner.ui.theme.isDark

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var shortcut by mutableStateOf<AppShortcut?>(null)

    // Below Android 13 the in-app language is applied here; above, the system does it.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (isFreshLaunchIntent(recreated = savedInstanceState != null, flags = intent?.flags ?: 0)) {
            handleShare(intent)
            shortcut = AppShortcut.of(intent?.action)
        }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val settings = state.settings ?: return@setContent
            val dark = settings.theme.isDark()
            LaunchedEffect(dark) {
                val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            LunarisScannerTheme(themeMode = settings.theme) {
                MainContent(state.sharedImage)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
        shortcut = AppShortcut.of(intent.action)
    }

    @Composable
    private fun MainContent(sharedImage: SharedImageState) {
        when (sharedImage) {
            SharedImageState.NONE, SharedImageState.FOUND ->
                AppNavHost(
                    startAtResult = sharedImage == SharedImageState.FOUND,
                    shortcut = shortcut,
                    onShortcutHandled = { shortcut = null },
                    onExit = ::finish,
                )
            SharedImageState.DECODING -> SharedImageProgress()
            SharedImageState.NOT_FOUND -> {
                SharedImageProgress()
                LaunchedEffect(Unit) {
                    Toast.makeText(this@MainActivity, R.string.shared_image_no_code, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith(IMAGE_MIME_PREFIX) != true) return
        intent.streamUri()?.let { viewModel.onAction(MainAction.ImageShared(it.toString())) }
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

    private companion object {
        const val IMAGE_MIME_PREFIX = "image/"
    }
}

/** Replaces the app while a shared image is read, so the camera does not start behind it. */
@Composable
private fun SharedImageProgress() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
