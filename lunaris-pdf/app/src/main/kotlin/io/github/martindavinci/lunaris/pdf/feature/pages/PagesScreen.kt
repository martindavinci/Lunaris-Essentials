package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.feature.work.WorkSheet
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.feature.work.WorkTexts
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import kotlinx.coroutines.launch

private val PillTop = 8.dp
private val GridBottom = 24.dp

/** Stateless Pages UI. */
@Composable
internal fun PagesScreen(
    state: PagesUiState,
    thumbnail: suspend (PageSlot) -> ImageBitmap?,
    pickThumbnail: suspend (PagePick, Int) -> ImageBitmap?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onExtract: () -> Unit,
    onSplit: () -> Unit,
    onOtherPdf: () -> Unit,
    onImages: () -> Unit,
    onAction: (PagesAction) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice?.let { stringResource(it.messageRes) }
    // Not in the effect itself: marking the message as shown restarts it, which would take it away.
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.notice) {
        if (notice != null) {
            onAction(PagesAction.NoticeShown)
            scope.launch { snackbar.showSnackbar(notice) }
        }
    }
    BackHandler(enabled = state.pick != null) { onAction(PagesAction.CancelPick) }
    BackHandler(enabled = state.pick == null && state.selecting) { onAction(PagesAction.ClearSelection) }
    BackHandler(enabled = state.pick == null && !state.selecting && state.askingToLeave) { onAction(PagesAction.KeepEditing) }
    BackHandler(enabled = state.pick == null && !state.selecting && !state.askingToLeave) { onAction(PagesAction.RequestLeave) }

    var drag by remember { mutableStateOf<PageDrag?>(null) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))) {
        val pick = state.pick
        when {
            pick != null -> PickPages(pick, pickThumbnail, onAction)
            state.problem == PagesProblem.DAMAGED -> Damaged(state, onBack)
            else -> Column(Modifier.fillMaxSize()) {
                PagesTopBar(state, subtitle(state), onBack = { onAction(PagesAction.RequestLeave) }, onSave = onSave, onAction = onAction)
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f)) {
                    PagesGrid(
                        state = state,
                        thumbnail = thumbnail,
                        onAction = onAction,
                        onDrag = { drag = it },
                        header = { GridHeader(state, drag != null) },
                        bottomSpace = GridBottom,
                    )
                    val place = drag?.place
                    if (place != null) {
                        MovePill(drag?.ids?.size ?: 0, place, Modifier.align(Alignment.TopCenter).padding(top = PillTop))
                    }
                }
                when {
                    state.problem == PagesProblem.PROTECTED -> PagesBackBar(onBack)
                    state.selecting && drag == null -> PagesSelectionBar(state.selected.size, onExtract, onAction)
                    drag == null -> PagesDefaultBar(onAction)
                }
            }
        }
        when (val panel = state.panel) {
            is PagesPanel.Add -> AddPagesPanel(panel, state.slots.size, { onAction(PagesAction.AddBlank) }, onOtherPdf, onImages, onAction)
            is PagesPanel.Split -> SplitPanel(panel.plan, state.slots.size, onSplit, onAction)
            null -> Unit
        }
        if (state.askingToLeave) LeaveDialog(state.changes, onSave, onAction)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        state.work?.let { work ->
            WorkSheet(
                state = work,
                texts = WorkTexts(
                    noteWhileRunning = stringResource(R.string.work_note_pages),
                    cancelNote = stringResource(R.string.work_cancel_edits),
                    stillHere = stringResource(R.string.work_edits_here),
                    savedNote = if (state.originalReplaced) "" else stringResource(R.string.work_original_same),
                ),
                sourceName = { source -> if (source == 0) state.name else null },
                onCancel = { onAction(PagesAction.CancelWork) },
                onRetry = { onAction(PagesAction.RetryWork) },
                alternative = if (work is WorkState.Failed) stringResource(R.string.work_save_elsewhere) else null,
                onAlternative = onSave,
                onCloseFailure = { onAction(PagesAction.CloseWorkFailure) },
                onDone = { onAction(PagesAction.CloseSaved) },
                onOpen = { onAction(PagesAction.OpenSaved) },
            )
        }
    }
}

@Composable
private fun subtitle(state: PagesUiState): String {
    if (!state.canEdit) return state.name
    val changes = if (state.changes == 0) stringResource(R.string.pages_no_changes) else pluralStringResource(R.plurals.pages_changes, state.changes, state.changes)
    return "$changes · ${state.name}"
}

private val PagesNotice.messageRes: Int
    get() = when (this) {
        PagesNotice.LAST_PAGE -> R.string.pages_notice_last
        PagesNotice.OTHER_PROTECTED -> R.string.pages_notice_other_protected
        PagesNotice.OTHER_DAMAGED -> R.string.pages_notice_other_damaged
        PagesNotice.IMAGE_FAILED -> R.string.pages_notice_image_failed
        PagesNotice.FOLDER_FAILED -> R.string.pages_notice_folder_failed
    }
