package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry
import kotlinx.coroutines.launch

private val FabPadding = 16.dp

/** Stateless Recents UI, the first screen of the app. */
@Composable
internal fun RecentsScreen(
    state: RecentsUiState,
    onAction: (RecentsAction) -> Unit,
    onOpenSettings: () -> Unit,
    onPickFile: () -> Unit,
    thumbnail: suspend (String) -> ImageBitmap?,
    onCreatePdf: () -> Unit = {},
) {
    var confirmClear by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RecentEntry?>(null) }
    val snackbar = remember { SnackbarHostState() }
    RecentsMessages(state, snackbar, onAction)
    BackHandler(enabled = state.query != null) { onAction(RecentsAction.CloseSearch) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            RecentsTopBar(
                query = state.query,
                hasEntries = state.entries.isNotEmpty(),
                onClear = { confirmClear = true },
                onOpenSettings = onOpenSettings,
                onAction = onAction,
            )
            if (state.entries.isEmpty()) {
                RecentsEmpty(onPickFile, onCreatePdf)
            } else {
                RecentsList(state, thumbnail, onAction, onMore = { selected = it }, onCreatePdf = onCreatePdf)
            }
        }
        if (state.entries.isNotEmpty()) {
            // The button keeps its text out of the accessibility tree, as it would while collapsing.
            val openLabel = stringResource(R.string.recents_open)
            ExtendedFloatingActionButton(
                onClick = onPickFile,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                text = { Text(stringResource(R.string.recents_open)) },
                modifier = Modifier.semantics { contentDescription = openLabel }.align(Alignment.BottomEnd).systemBarsPadding().padding(FabPadding),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).systemBarsPadding())
        selected?.let { entry ->
            RecentPanel(entry, onAction, onDismiss = { selected = null })
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.recents_clear_title)) },
            text = { Text(stringResource(R.string.recents_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onAction(RecentsAction.ClearAll)
                }) { Text(stringResource(R.string.recents_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RecentsMessages(state: RecentsUiState, snackbar: SnackbarHostState, onAction: (RecentsAction) -> Unit) {
    val gone = stringResource(R.string.recents_entry_gone)
    val pinLimit = stringResource(R.string.recents_pin_limit)
    val shareFailed = stringResource(R.string.action_share_failed)
    val removed = stringResource(R.string.recents_removed)
    val undo = stringResource(R.string.recents_undo)
    // Not in the effects themselves: marking a message as shown restarts them, which would take it away.
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.entryGone) {
        if (state.entryGone) {
            onAction(RecentsAction.MessageShown)
            scope.launch { snackbar.showSnackbar(gone) }
        }
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        onAction(RecentsAction.NoticeShown)
        scope.launch {
            when (notice) {
                RecentsNotice.PinLimit -> snackbar.showSnackbar(pinLimit)
                RecentsNotice.ShareFailed -> snackbar.showSnackbar(shareFailed)
                is RecentsNotice.Removed -> {
                    // Several in a row must not queue up: each would wait for its turn on screen.
                    snackbar.currentSnackbarData?.dismiss()
                    // With an action it would otherwise stay until something else takes its place.
                    if (snackbar.showSnackbar(removed, actionLabel = undo, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        onAction(RecentsAction.UndoRemove(notice.entry))
                    }
                }
            }
        }
    }
}
