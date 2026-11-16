package io.github.martindavinci.lunaris.archives.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.recents.RecentArchive
import io.github.martindavinci.lunaris.archives.ui.AccentTextButton
import io.github.martindavinci.lunaris.archives.ui.SearchField

private val BarMinHeight = 64.dp
private val TitleStart = 16.dp
private val BarEnd = 4.dp
private val SearchStart = 4.dp
private val SearchEnd = 12.dp
private val ListBottomPadding = 96.dp
private val FabMargin = 16.dp
private val EmptyTextPadding = 48.dp

/** Frames 01, 02 and the undrawn search, clear and "turned off" states. Stateless. */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onPickFile: () -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MaterialTheme.colorScheme
    val query = state.query
    BackHandler(enabled = query != null) { onAction(HomeAction.CloseSearch) }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            HomeTopBar(state, onAction, onSettings)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ListBottomPadding)) {
                if (query == null) {
                    item(key = "continue") {
                        val last = state.continueItem
                        if (last != null) ContinueCard(last, onClick = { onAction(HomeAction.OpenRecent(last)) }) else ContinuePlaceholder(state.rememberRecents)
                    }
                    item(key = "create") { CreateCard(onCreate) }
                }
                if (state.entries.isNotEmpty()) {
                    item(key = "title") { SectionTitle(stringResource(if (query == null) R.string.home_recents else R.string.home_results)) }
                    items(state.entries, key = { it.uri }) { item -> RecentRow(item, menuOpen = state.menuFor == item.uri, onAction) }
                } else {
                    item(key = "empty") { EmptyText(state) }
                }
            }
        }
        if (query == null) {
            val open = stringResource(R.string.home_open)
            ExtendedFloatingActionButton(
                onClick = onPickFile,
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                text = { Text(open) },
                modifier = Modifier.align(Alignment.BottomEnd).systemBarsPadding().padding(FabMargin),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).systemBarsPadding())
    }
    if (state.confirmClear) {
        AlertDialog(
            onDismissRequest = { onAction(HomeAction.DismissClear) },
            title = { Text(stringResource(R.string.home_clear_title)) },
            text = { Text(stringResource(R.string.home_clear_message)) },
            dismissButton = { AccentTextButton(stringResource(R.string.cancel), onClick = { onAction(HomeAction.DismissClear) }) },
            confirmButton = { AccentTextButton(stringResource(R.string.home_clear_confirm), onClick = { onAction(HomeAction.ConfirmClear) }) },
        )
    }
}

@Composable
private fun HomeTopBar(state: HomeUiState, onAction: (HomeAction) -> Unit, onSettings: () -> Unit) {
    val query = state.query
    if (query != null) {
        Row(Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(start = SearchStart, end = SearchEnd), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAction(HomeAction.CloseSearch) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            SearchField(query, onChange = { onAction(HomeAction.SetQuery(it)) }, placeholder = stringResource(R.string.home_search), modifier = Modifier.weight(1f))
        }
        return
    }
    Row(Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(start = TitleStart, end = BarEnd), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
        // Frame 02 keeps search enabled on the empty list and dims only "clear".
        IconButton(onClick = { onAction(HomeAction.OpenSearch) }) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.home_search))
        }
        IconButton(onClick = { onAction(HomeAction.AskClear) }, enabled = state.entries.isNotEmpty()) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.home_clear))
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
        }
    }
}

/** What the list says when it has no rows: nothing opened yet, recents turned off, or no match for a search. */
@Composable
private fun EmptyText(state: HomeUiState) {
    val query = state.query
    val text = when {
        query != null && query.isNotBlank() -> stringResource(R.string.home_no_match, query)
        query != null -> ""
        !state.rememberRecents -> stringResource(R.string.home_recents_off)
        else -> stringResource(R.string.home_empty_list)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(EmptyTextPadding),
    )
}
