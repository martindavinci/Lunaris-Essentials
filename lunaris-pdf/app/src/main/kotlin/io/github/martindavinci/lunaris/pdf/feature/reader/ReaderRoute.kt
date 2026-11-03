package io.github.martindavinci.lunaris.pdf.feature.reader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.print.PrintManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.actions.PdfPrintAdapter

private const val PDF_MIME = "application/pdf"

@Composable
fun ReaderRoute(onBack: () -> Unit) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { target ->
        target?.let { viewModel.onAction(ReaderAction.SaveCopyTo(it.toString())) }
    }

    val saveEdits = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { target ->
        target?.let { viewModel.onAction(EditAction.SaveTo(it.toString())) }
    }
    val leave = { if (state.hasUnsavedEdits) viewModel.onAction(EditAction.RequestLeave) else onBack() }

    BackHandler(enabled = state.search != null) { viewModel.onAction(ReaderAction.CloseSearch) }
    BackHandler(enabled = state.annotation != null) { viewModel.onAction(EditAction.Finish) }
    BackHandler(enabled = state.annotation == null && state.hasUnsavedEdits) { viewModel.onAction(EditAction.RequestLeave) }
    LaunchedEffect(state.closeReader) {
        if (state.closeReader) onBack()
    }

    val view = LocalView.current
    LaunchedEffect(state.chapterTicks) {
        if (state.chapterTicks > 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    StatusBar(visible = state.chromeVisible || state.search != null || state.error != null)

    LaunchedEffect(state.shareUri) {
        state.shareUri?.let { uri ->
            viewModel.onAction(ReaderAction.ShareStarted)
            shareFile(context, uri)
        }
    }
    LaunchedEffect(state.printUri) {
        state.printUri?.let { uri ->
            viewModel.onAction(EditAction.PrintStarted)
            print(context, uri, state)
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { notice ->
            viewModel.onAction(ReaderAction.NoticeShown)
            Toast.makeText(context, notice.messageRes, Toast.LENGTH_LONG).show()
        }
    }

    ReaderScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = leave,
        onPrint = { viewModel.onAction(EditAction.Print) },
        onSaveCopy = { createFile(context, saveCopy, state.name) },
        onSaveEdits = { createFile(context, saveEdits, state.name) },
        document = {
            val uri = state.uri
            if (uri != null && state.error == null) {
                key(state.viewRevision) {
                    DocumentView(uri, viewModel.viewer, Modifier.fillMaxSize())
                }
            }
        },
    )
}

/**
 * A tap on the document leaves it alone on the screen, but for the navigation bar: hiding that makes
 * Android show its own "Viewing full screen" popup.
 */
@Composable
private fun StatusBar(visible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(window, visible) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

private val ReaderNotice.messageRes: Int
    get() = when (this) {
        ReaderNotice.SAVED -> R.string.action_saved
        ReaderNotice.SAVE_FAILED -> R.string.action_save_failed
        ReaderNotice.SHARE_FAILED -> R.string.action_share_failed
        ReaderNotice.EDITS_LOST -> R.string.annotate_edits_lost
    }

private fun createFile(context: Context, launcher: ActivityResultLauncher<String>, name: String) {
    try {
        launcher.launch(name)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.action_save_failed, Toast.LENGTH_LONG).show()
    }
}

private fun print(context: Context, uri: String, state: ReaderUiState) {
    val adapter = PdfPrintAdapter(context.applicationContext.contentResolver, uri.toUri(), state.name, state.pages)
    context.getSystemService(PrintManager::class.java).print(state.name, adapter, null)
}

private fun shareFile(context: Context, uri: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(PDF_MIME)
        .putExtra(Intent.EXTRA_STREAM, uri.toUri())
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.action_share_failed, Toast.LENGTH_LONG).show()
    }
}
