package io.github.martindavinci.lunaris.scanner.feature.create

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.scanner.share.markSensitive

private const val PNG_MIME_TYPE = "image/png"
private const val SUGGESTED_FILE_NAME = "lunaris-code.png"

@Composable
fun CreateRoute() {
    val viewModel: CreateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PNG_MIME_TYPE),
    ) { uri ->
        if (uri != null) viewModel.onAction(CreateAction.SaveTo(uri.toString()))
    }

    val message = state.message?.let { stringResource(messageOf(it)) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.onAction(CreateAction.MessageShown)
        }
    }
    LaunchedEffect(state.shareUri) {
        state.shareUri?.let {
            shareImage(context, it)
            viewModel.onAction(CreateAction.ShareHandled)
        }
    }

    CreateScreen(
        state = state,
        snackbar = snackbar,
        onAction = viewModel::onAction,
        onSave = { saveLauncher.launch(SUGGESTED_FILE_NAME) },
        onCopy = {
            (state.result as? PayloadResult.Ready)?.let {
                copyToClipboard(context, it.payload, sensitive = state.form.holdsPassword)
                // Android 13 and later confirm a copy on their own.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    viewModel.onAction(CreateAction.Copied)
                }
            }
        },
    )
}

private fun copyToClipboard(context: Context, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText(null, text)
    if (sensitive) clip.markSensitive()
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

private fun shareImage(context: Context, uri: String) {
    val stream = uri.toUri()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = PNG_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, stream)
        clipData = ClipData.newRawUri(null, stream)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
