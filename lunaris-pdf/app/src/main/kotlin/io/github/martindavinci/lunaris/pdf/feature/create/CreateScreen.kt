package io.github.martindavinci.lunaris.pdf.feature.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.convert.PageFormat
import io.github.martindavinci.lunaris.pdf.feature.work.WorkSheet
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.feature.work.WorkTexts
import kotlinx.coroutines.launch

private val TopBarHeight = 64.dp

/** Stateless Create PDF UI: a list and a button. */
@Composable
internal fun CreateScreen(
    state: CreateUiState,
    thumbnail: suspend (CreateItem) -> ImageBitmap?,
    onAddFiles: () -> Unit,
    onCreate: () -> Unit,
    onAction: (CreateAction) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedText = stringResource(R.string.create_removed)
    val undoText = stringResource(R.string.recents_undo)
    LaunchedEffect(state.removed) {
        val removed = state.removed ?: return@LaunchedEffect
        onAction(CreateAction.RemovedShown)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(removedText, actionLabel = undoText, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                onAction(CreateAction.UndoRemove(removed))
            }
        }
    }
    BackHandler { onAction(CreateAction.RequestLeave) }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = TopBarHeight), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(CreateAction.RequestLeave) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.create_title), style = MaterialTheme.typography.titleLarge)
            }
            // The message stays above the button, which it must not cover.
            Box(Modifier.weight(1f)) {
                if (state.items.isEmpty()) {
                    EmptyList(onAddFiles, Modifier.fillMaxSize())
                } else {
                    CreateList(state, thumbnail, onAddFiles, onAction, Modifier.fillMaxSize())
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
            if (state.items.isNotEmpty()) CreateFooter(state, onCreate)
        }
        if (state.layoutOpen) LayoutPanel(state.layout, onAction)
        if (state.askingToLeave) LeaveListDialog(onAction)
        state.work?.let { work ->
            val failedFile = (work as? WorkState.Failed)?.source?.let { state.items.getOrNull(it) }
            WorkSheet(
                state = work,
                texts = WorkTexts(
                    noteWhileRunning = if (state.hasText) stringResource(R.string.work_note_text, formatName(state.layout.format)) else "",
                    cancelNote = stringResource(R.string.work_cancel_list),
                    stillHere = stringResource(R.string.work_list_here),
                    savedNote = pluralStringResource(R.plurals.work_files_same, state.items.size, state.items.size),
                ),
                sourceName = { state.items.getOrNull(it)?.name },
                onCancel = { onAction(CreateAction.CancelWork) },
                onRetry = { onAction(CreateAction.RetryWork) },
                alternative = if (failedFile != null) stringResource(R.string.work_remove_file) else stringResource(R.string.work_save_elsewhere),
                onAlternative = { if (failedFile != null) onAction(CreateAction.RemoveFailedFile) else onCreate() },
                onCloseFailure = { onAction(CreateAction.CloseWorkFailure) },
                onDone = { onAction(CreateAction.CloseSaved) },
                onOpen = { onAction(CreateAction.OpenSaved) },
            )
        }
    }
}

@Composable
internal fun formatName(format: PageFormat): String = when (format) {
    PageFormat.A4 -> stringResource(R.string.create_format_a4)
    PageFormat.LETTER -> stringResource(R.string.create_format_letter)
}
