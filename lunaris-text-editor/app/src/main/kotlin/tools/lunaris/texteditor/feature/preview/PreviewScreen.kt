package tools.lunaris.texteditor.feature.preview

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import tools.lunaris.texteditor.R
import tools.lunaris.texteditor.ui.keepHolderVisibleToAccessibility

@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val webRef = remember { mutableStateOf<WebView?>(null) }
    val darkState = remember { mutableStateOf(true) }
    val dark = darkState.value
    val displayTitle = viewModel.title.ifBlank { stringResource(R.string.preview_title) }
    val html = viewModel.html(dark)
    val enableJs = !viewModel.isRawHtmlPreview

    val readerState by viewModel.readerState.collectAsStateWithLifecycle()
    val readingText by viewModel.readingText.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val voiceGroups by viewModel.voiceGroups.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val offlineLabel = stringResource(R.string.tts_offline)
    val networkLabel = stringResource(R.string.tts_requires_connection)

    // The ViewModel outlives the preview overlay (scoped to the editor entry), so leaving the
    // preview must stop read-aloud explicitly or the voice keeps going behind the editor.
    DisposableEffect(viewModel) { onDispose { viewModel.stopReading() } }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, context.getString(it), Toast.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    PreviewChrome(
        title = displayTitle,
        onBack = onBack,
        onPrint = { printWebView(context, webRef.value, viewModel.title) },
        isDark = dark,
        onToggleTheme = { darkState.value = !darkState.value },
        isReading = readerState.reading,
        canReadAloud = html != null,
        onReadAloud = {
            if (readerState.reading) viewModel.stopReading()
            else viewModel.startReading(offlineLabel, networkLabel)
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                readerState.reading -> ReadingPane(readingText, segments, readerState.index)
                html == null -> Text(
                    text = stringResource(R.string.preview_nothing),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.blockNetworkLoads = true
                            @Suppress("DEPRECATION")
                            settings.allowFileAccess = false
                            keepHolderVisibleToAccessibility()
                            webRef.value = this
                        }
                    },
                    update = {
                        it.setBackgroundColor(if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                        it.settings.javaScriptEnabled = enableJs
                        it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (readerState.reading) {
                ReadingController(
                    state = readerState,
                    voiceGroups = voiceGroups,
                    statusVoice = rateLabelFor(readerState.rate),
                    onPlayPause = viewModel::togglePause,
                    onPrev = viewModel::previous,
                    onNext = viewModel::next,
                    onSelectVoice = viewModel::setVoice,
                    onRate = viewModel::setRate,
                )
            }
        }
    }
}

private fun rateLabelFor(rate: Float): String = String.format(java.util.Locale.US, "%.1f×", rate)

@Composable
internal fun PreviewChrome(
    title: String,
    onBack: () -> Unit,
    onPrint: () -> Unit,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    isReading: Boolean = false,
    canReadAloud: Boolean = true,
    onReadAloud: () -> Unit = {},
    body: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(onClick = onReadAloud, enabled = canReadAloud) {
                        Icon(
                            imageVector = if (isReading) Icons.Filled.Stop else Icons.Filled.RecordVoiceOver,
                            contentDescription = stringResource(
                                if (isReading) R.string.action_stop_reading else R.string.preview_read_aloud,
                            ),
                        )
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = stringResource(R.string.preview_toggle_theme),
                        )
                    }
                    IconButton(onClick = onPrint) {
                        Icon(Icons.Filled.Print, stringResource(R.string.preview_print))
                    }
                }
            }
        },
        content = body,
    )
}

private fun printWebView(context: Context, web: WebView?, jobName: String) {
    val webView = web ?: return
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val name = jobName.ifBlank { "document" }
    val adapter = webView.createPrintDocumentAdapter(name)
    pm.print(name, adapter, PrintAttributes.Builder().build())
}
