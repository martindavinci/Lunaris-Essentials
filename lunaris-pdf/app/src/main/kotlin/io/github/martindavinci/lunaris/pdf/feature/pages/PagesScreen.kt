package io.github.martindavinci.lunaris.pdf.feature.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.feature.work.WorkSheet
import io.github.martindavinci.lunaris.pdf.feature.work.WorkState
import io.github.martindavinci.lunaris.pdf.feature.work.WorkTexts
import io.github.martindavinci.lunaris.pdf.pages.PageSlot
import io.github.martindavinci.lunaris.pdf.ui.theme.iconTile
import kotlinx.coroutines.launch

private val CellMinWidth = 96.dp
private val EdgePadding = 16.dp
private val Spacing = 12.dp
private val InfoIcon = 18.dp
private val PillTop = 8.dp
private val GridBottom = 24.dp
private val CardShape = RoundedCornerShape(18.dp)
private val TileSize = 40.dp
private val TileShape = RoundedCornerShape(12.dp)
private val BigTile = 72.dp
private val BigTileShape = RoundedCornerShape(20.dp)
private val ButtonHeight = 52.dp
private val ButtonShape = RoundedCornerShape(16.dp)
private val MessagePadding = 32.dp

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
                    savedNote = stringResource(R.string.work_original_same),
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

/** Scrolls away with the grid: worth saying, not worth keeping in the way. */
@Composable
private fun GridHeader(state: PagesUiState, dragging: Boolean) {
    val colors = MaterialTheme.colorScheme
    when {
        state.problem == PagesProblem.PROTECTED -> Surface(shape = CardShape, color = colors.surface, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing)) {
            Row(Modifier.padding(EdgePadding), horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                Box(Modifier.size(TileSize).clip(TileShape).background(colors.iconTile), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.primary)
                }
                Column {
                    Text(stringResource(R.string.pages_protected_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.pages_protected_message), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        // Kept in its place, unseen: the pages under the finger must not jump when a drag starts.
        else -> Row(Modifier.padding(bottom = Spacing).alpha(if (dragging) 0f else 1f), horizontalArrangement = Arrangement.spacedBy(Spacing)) {
            Icon(if (state.loading) Icons.Filled.HourglassTop else Icons.Filled.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(InfoIcon))
            Text(
                text = stringResource(if (state.loading) R.string.pages_loading else R.string.pages_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Damaged(state: PagesUiState, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        PagesTopBar(state, state.name, onBack = onBack, onSave = {}, onAction = {})
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(MessagePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EdgePadding, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(BigTile).clip(BigTileShape).background(colors.errorContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = colors.onErrorContainer)
            }
            Text(stringResource(R.string.pages_damaged_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.pages_damaged_message), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(
                onClick = onBack,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight),
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Text(stringResource(R.string.pages_back_to_reading), modifier = Modifier.padding(start = Spacing))
            }
        }
    }
}

/** The pages of another document, in the same grid: a tap chooses or leaves out. */
@Composable
private fun PickPages(pick: PagePick, thumbnail: suspend (PagePick, Int) -> ImageBitmap?, onAction: (PagesAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Surface(color = colors.background) {
            Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(PagesAction.CancelPick) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.pages_pick_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(PagesAction.PickAll) }) { Text(stringResource(R.string.pages_select_all)) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CellMinWidth),
            contentPadding = PaddingValues(EdgePadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing),
            verticalArrangement = Arrangement.spacedBy(Spacing),
            modifier = Modifier.weight(1f),
        ) {
            items(pick.pageCount) { index ->
                val label = stringResource(R.string.pages_page_label, index + 1)
                PageTile(
                    number = index + 1,
                    turn = 0,
                    blankAspect = null,
                    selected = index in pick.chosen,
                    selecting = true,
                    dimmed = false,
                    key = pick.source to index,
                    load = { thumbnail(pick, index) },
                    modifier = Modifier
                        .clickable { onAction(PagesAction.TogglePicked(index)) }
                        .clearAndSetSemantics {
                            contentDescription = label
                            selected = index in pick.chosen
                        },
                )
            }
        }
        Surface(color = colors.background) {
            Button(
                onClick = { onAction(PagesAction.ConfirmPick) },
                enabled = pick.chosen.isNotEmpty(),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing).heightIn(min = ButtonHeight),
            ) {
                Text(pluralStringResource(R.plurals.pages_pick_add, pick.chosen.size, pick.chosen.size))
            }
        }
    }
}

private val PagesNotice.messageRes: Int
    get() = when (this) {
        PagesNotice.LAST_PAGE -> R.string.pages_notice_last
        PagesNotice.OTHER_PROTECTED -> R.string.pages_notice_other_protected
        PagesNotice.OTHER_DAMAGED -> R.string.pages_notice_other_damaged
        PagesNotice.IMAGE_FAILED -> R.string.pages_notice_image_failed
        PagesNotice.FOLDER_FAILED -> R.string.pages_notice_folder_failed
    }
