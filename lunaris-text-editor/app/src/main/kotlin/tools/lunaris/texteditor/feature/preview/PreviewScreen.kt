package tools.lunaris.texteditor.feature.preview

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import tools.lunaris.texteditor.R

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
    // JS is needed only by our generated docs (which run the bundled highlight.js and have their
    // content sanitized). Raw-HTML previews render an untrusted page as-is, so keep JS off there.
    val enableJs = !viewModel.isRawHtmlPreview

    PreviewChrome(
        title = displayTitle,
        onBack = onBack,
        onPrint = { printWebView(context, webRef.value, viewModel.title) },
        isDark = dark,
        onToggleTheme = { darkState.value = !darkState.value },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (html == null) {
                Text(
                    text = stringResource(R.string.preview_nothing),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // Sandbox: no network, no file access. JS is toggled per-load below —
                            // on only for our sanitized docs (highlight.js), off for raw HTML.
                            settings.blockNetworkLoads = true
                            @Suppress("DEPRECATION")
                            settings.allowFileAccess = false
                            webRef.value = this
                        }
                    },
                    update = {
                        it.settings.javaScriptEnabled = enableJs
                        it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Stateless preview scaffold (top bar + body slot). The WebView lives in [PreviewScreen];
 *  this chrome is what gets previewed. */
@Composable
internal fun PreviewChrome(
    title: String,
    onBack: () -> Unit,
    onPrint: () -> Unit,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = stringResource(R.string.preview_toggle_theme),
                        )
                    }
                    IconButton(onClick = onPrint) {
                        Icon(
                            imageVector = Icons.Filled.Print,
                            contentDescription = stringResource(R.string.preview_print),
                        )
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
