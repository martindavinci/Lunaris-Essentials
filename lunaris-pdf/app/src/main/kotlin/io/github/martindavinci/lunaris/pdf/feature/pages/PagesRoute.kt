package io.github.martindavinci.lunaris.pdf.feature.pages

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.feature.work.WorkView

private const val PDF_MIME = "application/pdf"
private const val PDF_SUFFIX = ".pdf"
private val IMAGE_MIMES = arrayOf("image/jpeg", "image/png", "image/webp")

@Composable
fun PagesRoute(onBack: () -> Unit, onOpenReader: () -> Unit) {
    val viewModel: PagesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val base = state.name.removeSuffix(PDF_SUFFIX)
    val savedName = stringResource(R.string.pages_edited_name, base)
    val extractedName = stringResource(R.string.pages_extracted_name, base)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        uri?.let { viewModel.onAction(PagesAction.SaveTo(it.toString(), savedName)) }
    }
    val extract = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        uri?.let { viewModel.onAction(PagesAction.ExtractTo(it.toString(), extractedName)) }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onAction(PagesAction.SplitInto(it.toString())) }
    }
    val otherPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onAction(PagesAction.AddOtherPdf(it.toString())) }
    }
    val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.onAction(PagesAction.AddImages(uris.map { it.toString() }))
    }

    fun <T> launch(launcher: ActivityResultLauncher<T>, input: T) {
        try {
            launcher.launch(input)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_for_files, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(state.close) {
        if (state.close) onBack()
    }
    LaunchedEffect(state.openReader) {
        if (state.openReader) {
            viewModel.onAction(PagesAction.ReaderOpened)
            onOpenReader()
        }
    }

    PagesScreen(
        state = state,
        thumbnail = viewModel::thumbnailOf,
        pickThumbnail = viewModel::pickThumbnail,
        onBack = onBack,
        onSave = { launch(save, savedName) },
        onExtract = { launch(extract, extractedName) },
        onSplit = { launch(folder, null) },
        onOtherPdf = { launch(otherPdf, arrayOf(PDF_MIME)) },
        onImages = { launch(images, IMAGE_MIMES) },
        onAction = viewModel::onAction,
    )
    (state.work as? WorkState.Running)?.sources?.let { WorkView(it, viewModel.channel) }
}
