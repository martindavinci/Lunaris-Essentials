package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.archives.feature.edits.EditsHost

/**
 * The browser with its side effects: other apps started, panels, messages, leaving.
 * [onExtract] receives the entries to extract (the folder picker comes with the extraction screen).
 */
@Composable
fun BrowserRoute(
    onExit: () -> Unit,
    onNested: (String) -> Unit,
    onExtract: (ExtractArgs) -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val onAction = viewModel::onAction

    LaunchedEffect(state.exit) { if (state.exit) onExit() }
    LaunchedEffect(state.launch) {
        val request = state.launch ?: return@LaunchedEffect
        when (request) {
            is LaunchRequest.Nested -> onNested(request.requestId)
            is LaunchRequest.Extract -> onExtract(request.jobArgs)
            else -> Unit
        }
        onAction(BrowserAction.LaunchHandled(context.launch(request)))
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        val (text, argument) = notice.message()
        onAction(BrowserAction.NoticeShown)
        snackbar.showSnackbar(if (argument == null) context.getString(text) else context.getString(text, argument))
    }

    // No archive yet: no URI matches, so no card shows before the listing.
    val edits = EditsHost(state.archiveUri ?: NONE, snackbar, onReload = { onAction(BrowserAction.Reload) })
    BrowserScreen(state, onAction, snackbar, cards = { edits.Cards() })
    when (val sheet = state.sheet) {
        is BrowserSheet.Row -> EntrySheet(sheet.row, onAction)
        BrowserSheet.Info -> state.header?.let { InfoSheet(it, onAction) }
        BrowserSheet.Sort -> SortSheet(state.sort, onAction)
        is BrowserSheet.NoApp -> NoAppSheet(sheet.row, onAction)
        BrowserSheet.Rar4 -> Rar4Sheet(onAction)
        null -> Unit
    }
}

private const val NONE = ""
