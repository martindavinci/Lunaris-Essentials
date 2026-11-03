package io.github.martindavinci.lunaris.pdf.feature.recents

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

private const val PDF_MIME = "application/pdf"

@Composable
fun RecentsRoute(onOpenReader: () -> Unit, onOpenSettings: () -> Unit) {
    val viewModel: RecentsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onAction(RecentsAction.OpenPicked(it.toString())) }
    }

    LaunchedEffect(state.openReader) {
        if (state.openReader) {
            viewModel.onAction(RecentsAction.ReaderOpened)
            onOpenReader()
        }
    }

    RecentsScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenSettings = onOpenSettings,
        onPickFile = {
            try {
                picker.launch(arrayOf(PDF_MIME))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.recents_no_picker, Toast.LENGTH_LONG).show()
            }
        },
    )
}
