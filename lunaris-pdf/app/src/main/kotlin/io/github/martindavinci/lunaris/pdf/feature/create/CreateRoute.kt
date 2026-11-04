package io.github.martindavinci.lunaris.pdf.feature.create

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.feature.work.WorkView

private const val PDF_MIME = "application/pdf"
private const val PDF_SUFFIX = ".pdf"

/** Any file: a Markdown file often has no type its provider would match. What cannot be used says so in its row. */
private val ANY_FILE = arrayOf("*/*")

@Composable
fun CreateRoute(onBack: () -> Unit, onOpenReader: () -> Unit) {
    val viewModel: CreateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val target = state.items.firstOrNull()?.name?.substringBeforeLast('.').orEmpty() + PDF_SUFFIX
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        uri?.let { viewModel.onAction(CreateAction.CreateTo(it.toString(), target)) }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.onAction(CreateAction.AddFiles(uris.map { it.toString() }))
    }
    val safely = { launch: () -> Unit ->
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_for_files, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(state.close) {
        if (state.close) onBack()
    }
    LaunchedEffect(state.openReader) {
        if (state.openReader) {
            viewModel.onAction(CreateAction.ReaderOpened)
            onOpenReader()
        }
    }
    CreateScreen(
        state = state,
        thumbnail = viewModel::thumbnailOf,
        onAddFiles = { safely { files.launch(ANY_FILE) } },
        onCreate = { safely { create.launch(target) } },
        onAction = viewModel::onAction,
    )
    (state.work as? WorkState.Running)?.sources?.let { WorkView(it, viewModel.channel) }
}
