package io.github.martindavinci.lunaris.archives.feature.extract

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.archives.extract.SafTarget

/**
 * The extraction screen with its effects: leaving when done, and the folder picker after "Choose".
 * [openTree] keeps the permission on the picked folder and wraps it as a target.
 */
@Composable
fun ExtractRoute(onExit: () -> Unit, openTree: (Uri) -> SafTarget, viewModel: ExtractViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onFolderPicked(openTree(it)) }
    }
    LaunchedEffect(state.exit) { if (state.exit) onExit() }
    LaunchedEffect(state.pickFolder) {
        if (state.pickFolder) {
            viewModel.onAction(ExtractAction.PickerHandled)
            picker.launch(null)
        }
    }
    ExtractScreen(state, viewModel::onAction)
}
