package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.browse.FolderRow
import io.github.martindavinci.lunaris.archives.ui.Breadcrumb
import io.github.martindavinci.lunaris.archives.ui.CompactExtendedFab
import io.github.martindavinci.lunaris.archives.ui.OpeningBar
import io.github.martindavinci.lunaris.archives.ui.countText
import io.github.martindavinci.lunaris.archives.ui.percentText
import io.github.martindavinci.lunaris.archives.ui.sizeText

private const val SLIDE_MILLIS = 220
private const val SLIDE_PART = 4
private val FabMargin = 16.dp
private val OpeningMargin = 12.dp

/** Stateless browser (frames 03, 04, 06, 07, 08, 09 and the undrawn states). Sheets are shown by the route. */
@Composable
internal fun BrowserScreen(
    state: BrowserUiState,
    onAction: (BrowserAction) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    cards: @Composable () -> Unit = {},
) {
    var fabExpanded by remember { mutableStateOf(true) }
    val inFolder = state.path.isNotEmpty() && state.problem == null
    BackHandler(enabled = state.selecting || state.query != null || inFolder) {
        onAction(if (state.selecting) BrowserAction.ClearSelection else if (state.query != null) BrowserAction.CloseSearch else BrowserAction.Up)
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            BrowserTopBar(state, onAction)
            val problem = state.problem
            when {
                problem != null -> BrowserProblem(problem, state, onAction)
                state.query != null -> SearchResults(state, onAction)
                else -> {
                    cards()
                    Breadcrumb(state.crumbs, onSegment = { onAction(BrowserAction.GoToCrumb(it)) })
                    Folder(state, onAction, onScrollingUp = { fabExpanded = it })
                }
            }
        }
        val listed = state.loading == LoadState.Ready && state.problem == null && state.password == null
        val hasFiles = (state.header?.fileCount ?: 0) > 0
        if (listed && hasFiles && !state.selecting && state.query == null && state.opening == null) {
            CompactExtendedFab(
                icon = Icons.Rounded.Unarchive,
                text = stringResource(R.string.browser_extract_all),
                expanded = fabExpanded,
                onClick = { onAction(BrowserAction.ExtractAll) },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(FabMargin),
            )
        }
        state.opening?.let { opening ->
            OpeningBar(opening.name, opening.fraction, onCancel = { onAction(BrowserAction.CancelOpening) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(OpeningMargin).fillMaxWidth())
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    state.password?.let { prompt ->
        PasswordDialog(
            prompt = prompt,
            onSubmit = { chars, remember -> onAction(BrowserAction.SubmitPassword(chars, remember)) },
            onEdited = { onAction(BrowserAction.PasswordEdited) },
            onDismiss = { onAction(BrowserAction.DismissPassword) },
        )
    }
}

/** What is under the breadcrumb: the reading line, the quiet notes, and the rows of the folder sliding in and out. */
@Composable
private fun Folder(state: BrowserUiState, onAction: (BrowserAction) -> Unit, onScrollingUp: (Boolean) -> Unit) {
    when (val loading = state.loading) {
        LoadState.Reading -> return                       // nothing for the first 300 ms: most archives are there by then
        is LoadState.ReadingSlow -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            val text = if (loading.items > 0) countText(R.plurals.browser_reading, loading.items) else stringResource(R.string.browser_reading_start)
            return CenteredMessage(text)
        }
        is LoadState.Copying -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            val total = loading.total
            val amount = if (total != null && total > 0) percentText(loading.done.toFloat() / total) else sizeText(loading.done)
            return CenteredMessage(stringResource(R.string.browser_copying, amount))
        }
        LoadState.Ready -> Unit
    }
    if (state.partial) InfoStrip(stringResource(R.string.browser_partial))
    if (state.capped) InfoStrip(stringResource(R.string.browser_capped))
    if (state.hiddenUnsafe > 0 && state.path.isEmpty()) InfoStrip(countText(R.plurals.browser_hidden_unsafe, state.hiddenUnsafe))
    if (state.rows.isEmpty()) {
        val wholeArchive = state.path.isEmpty() && (state.header?.fileCount ?: 0) == 0
        return CenteredMessage(stringResource(if (wholeArchive) R.string.browser_empty_archive else R.string.browser_empty_folder))
    }
    // Each page keeps the rows it was shown with, so the folder that leaves does not turn into the one that comes.
    AnimatedContent(
        targetState = FolderPage(state.path, state.rows),
        transitionSpec = {
            val deeper = targetState.path.size >= initialState.path.size
            val enter = slideInHorizontally(tween(SLIDE_MILLIS)) { width -> if (deeper) width / SLIDE_PART else -width / SLIDE_PART } + fadeIn(tween(SLIDE_MILLIS))
            val exit = slideOutHorizontally(tween(SLIDE_MILLIS)) { width -> if (deeper) -width / SLIDE_PART else width / SLIDE_PART } + fadeOut(tween(SLIDE_MILLIS))
            enter togetherWith exit
        },
        contentKey = { it.path },                           // a new sort order is not a new page
        label = "folder",
    ) { page ->
        val listState = rememberLazyListState()
        if (page.path == state.path) {
            val up = listState.isScrollingUp()
            SideEffect { onScrollingUp(up) }
        }
        FolderList(page.rows, state, listState, onAction)
    }
}

private data class FolderPage(val path: List<String>, val rows: List<FolderRow>)

/** True while the list is at rest or moving towards its start: the button shows its text then. */
@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    var lastIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var lastOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            val up = if (lastIndex != firstVisibleItemIndex) lastIndex > firstVisibleItemIndex else lastOffset >= firstVisibleItemScrollOffset
            lastIndex = firstVisibleItemIndex
            lastOffset = firstVisibleItemScrollOffset
            up
        }
    }.value
}
