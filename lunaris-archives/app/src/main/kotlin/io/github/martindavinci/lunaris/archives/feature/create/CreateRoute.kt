package io.github.martindavinci.lunaris.archives.feature.create

import io.github.martindavinci.lunaris.archives.ui.launchOr
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.create.ArchiveNameRules
import io.github.martindavinci.lunaris.archives.create.CreateFailure
import io.github.martindavinci.lunaris.archives.create.CreateState

/** The New archive screen with its pickers, the system "Save as", sharing and opening the result. */
@Composable
fun CreateRoute(onClose: () -> Unit, onOpenArchive: (String) -> Unit, viewModel: CreateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noPicker = { scope.launch { snackbar.showSnackbar(context.getString(R.string.home_no_picker)) }; Unit }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.onAction(CreateAction.AddFiles(uris.map(Uri::toString)))
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onAction(CreateAction.AddFolder(it.toString())) }
    }
    val saveAs = rememberLauncherForActivityResult(SaveAsContract()) { uri ->
        viewModel.onAction(CreateAction.CreateAt(uri?.toString()))
    }

    LaunchedEffect(state.openRequest) {
        state.openRequest?.let { id ->
            viewModel.onAction(CreateAction.Navigated)
            onOpenArchive(id)
        }
    }
    LaunchedEffect(state.state) {
        val failed = state.state as? CreateState.Failed ?: return@LaunchedEffect
        viewModel.onAction(CreateAction.FailureShown)
        // Shown in the screen's scope: marking it shown changes this effect's key, which would cancel the snackbar.
        scope.launch { snackbar.showSnackbar(context.getString(failureText(failed.reason))) }
    }

    CreateScreen(
        state = state,
        onAction = { action ->
            if (action == CreateAction.Share) (state.state as? CreateState.Finished)?.let { context.shareArchive(it.uri, state.format.mime) }
            viewModel.onAction(action)
        },
        onPickFiles = { files.launchOr(arrayOf(ANY_TYPE), noPicker) },
        onPickFolder = { folder.launchOr(null, noPicker) },
        onCreate = { password ->
            viewModel.onAction(CreateAction.HoldPassword(password))
            saveAs.launchOr(SaveAsContract.Input(ArchiveNameRules.fileName(state.name, state.format), state.format.mime)) {
                viewModel.onAction(CreateAction.CreateAt(null))   // wipes the held password
                noPicker()
            }
        },
        onClose = onClose,
        snackbar = snackbar,
    )
}

private fun failureText(reason: CreateFailure): Int = when (reason) {
    CreateFailure.NO_SPACE -> R.string.create_failed_space
    CreateFailure.SOURCE_GONE -> R.string.create_failed_source
    CreateFailure.TARGET_GONE -> R.string.create_failed_target
    CreateFailure.NO_MEMORY -> R.string.create_failed_memory
}

private fun Context.shareArchive(uri: String, mime: String) {
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { startActivity(Intent.createChooser(send, null)) }
}

private const val ANY_TYPE = "*/*"
