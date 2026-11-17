package io.github.martindavinci.lunaris.archives.feature.edits

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.feature.browser.PasswordDialog
import io.github.martindavinci.lunaris.archives.feature.browser.PasswordPrompt
import io.github.martindavinci.lunaris.archives.feature.browser.PasswordTarget
import io.github.martindavinci.lunaris.archives.feature.create.SaveAsContract
import io.github.martindavinci.lunaris.archives.ui.launchOr
import io.github.martindavinci.lunaris.archives.ui.sayNoPicker
import kotlinx.coroutines.launch

/** The system picker for the archive itself, opened where it is: picking it again gives the right to write it. */
private class PickArchiveAgain : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(ANY_TYPE)
        .apply { Uri.parse(input).takeIf { DocumentsContract.isDocumentUri(context, it) }?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) } }
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent.takeIf { resultCode == android.app.Activity.RESULT_OK }?.data
}

/**
 * Changed files for [archiveUri] (the browser) or for every archive (Home, null): the cards, the pickers and the
 * dialogs they lead to. [onReload] runs when the archive shown here was updated.
 */
@Composable
fun EditsHost(
    archiveUri: String?,
    snackbar: SnackbarHostState,
    /** The archive shown here was updated; its URI now (another one when it was picked again). */
    onReload: (String) -> Unit = {},
    viewModel: EditsViewModel = hiltViewModel(),
): EditsCardsSlot {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onAction = viewModel::onAction
    var saving by rememberSaveable { mutableStateOf<String?>(null) }   // the archive whose files are being saved
    var savingOne by rememberSaveable { mutableStateOf<String?>(null) }   // the record, when there is one file
    val saveOne = rememberLauncherForActivityResult(SaveAsContract()) { uri ->
        saving?.let { onAction(EditsAction.SaveTo(it, uri?.toString(), isFolder = false, recordId = savingOne)) }
    }
    val saveAll = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        saving?.let { onAction(EditsAction.SaveTo(it, uri?.toString(), isFolder = true)) }
    }
    val pickAgain = rememberLauncherForActivityResult(PickArchiveAgain()) { uri ->
        state.pick?.let { onAction(EditsAction.Picked(it.archiveUri, uri?.toString())) }
    }

    LaunchedEffect(state.reload) {
        val reload = state.reload ?: return@LaunchedEffect
        onAction(EditsAction.Reloaded)
        if (reload.from == archiveUri) onReload(reload.to)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        // Shown in a scope of its own: marking it shown restarts this effect, which must not take the snackbar away.
        scope.launch { snackbar.showSnackbar(context.text(message)) }
        onAction(EditsAction.MessageShown)
    }

    state.confirmIgnore?.let { uri ->
        val count = state.archives.firstOrNull { it.archiveUri == uri }?.entries?.size ?: 1
        AlertDialog(
            onDismissRequest = { onAction(EditsAction.CancelIgnore) },
            title = { Text(stringResource(R.string.edits_ignore_title)) },
            text = { Text(pluralStringResource(R.plurals.edits_ignore_body, count, count)) },
            confirmButton = { TextButton(onClick = { onAction(EditsAction.ConfirmIgnore) }) { Text(stringResource(R.string.edits_discard)) } },
            dismissButton = { TextButton(onClick = { onAction(EditsAction.CancelIgnore) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.password?.let { target ->
        PasswordDialog(
            prompt = PasswordPrompt(target.archiveName, PasswordTarget.ARCHIVE, wrong = target.wrong),
            onSubmit = { typed, _ -> onAction(EditsAction.SubmitPassword(typed)) },
            onEdited = {},
            onDismiss = { onAction(EditsAction.CancelPassword) },
        )
    }
    state.pick?.let { target ->
        AlertDialog(
            onDismissRequest = { onAction(EditsAction.CancelPick) },
            title = { Text(stringResource(R.string.edits_choose_title)) },
            text = { Text(stringResource(R.string.edits_choose_body, target.archiveName)) },
            confirmButton = {
                TextButton(onClick = { pickAgain.launchOr(target.archiveUri) { onAction(EditsAction.CancelPick); context.sayNoPicker() } }) {
                    Text(stringResource(R.string.edits_choose))
                }
            },
            dismissButton = { TextButton(onClick = { onAction(EditsAction.CancelPick) }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    return EditsCardsSlot { EditsCards(state, archiveUri, onAction) { archive ->
        saving = archive.archiveUri
        if (archive.entries.size == 1) {
            savingOne = archive.entries.single().id
            val name = archive.entries.single().entryPath.substringAfterLast('/')
            saveOne.launchOr(SaveAsContract.Input(name, ANY_TYPE)) { context.sayNoPicker() }
        } else {
            saveAll.launchOr(null) { context.sayNoPicker() }
        }
    } }
}

/** What a screen places where the cards go: above the path in the browser, first on Home. */
fun interface EditsCardsSlot {
    @Composable
    fun Cards()
}

private fun Context.text(message: EditsMessage): String = when (message) {
    is EditsMessage.Updated -> getString(R.string.edits_updated, message.name)
    is EditsMessage.NotUpdated -> getString(R.string.edits_not_updated, message.name)
    is EditsMessage.ArchiveChanged -> getString(R.string.edits_archive_changed, message.name)
    is EditsMessage.CannotUpdate -> getString(R.string.edits_cannot_update, message.name)
    EditsMessage.NoSpace -> getString(R.string.edits_no_space)
    EditsMessage.DifferentFile -> getString(R.string.edits_different_file)
    EditsMessage.Saved -> getString(R.string.edits_saved)
    EditsMessage.SaveFailed -> getString(R.string.edits_save_failed)
    is EditsMessage.Restored -> getString(R.string.edits_restored, message.name)
    is EditsMessage.RestoreFailed -> getString(R.string.edits_restore_failed, message.name)
}

private const val ANY_TYPE = "*/*"
