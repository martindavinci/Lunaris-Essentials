package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.ui.ArchiveTopBar
import io.github.martindavinci.lunaris.archives.ui.SearchField
import io.github.martindavinci.lunaris.archives.ui.countText
import io.github.martindavinci.lunaris.archives.ui.theme.BarTitleStyle

private val BarMinHeight = 64.dp
private val BarPadding = 4.dp
private val TitleStart = 4.dp
private val SearchEnd = 12.dp
private val MenuShape = RoundedCornerShape(16.dp)
private val MenuWidth = 212.dp
private val MenuItemPadding = PaddingValues(horizontal = 16.dp)
private val PillPadding = PaddingValues(start = 12.dp, end = 16.dp)
private val PillIconSize = 20.dp
private val PillIconSpacing = 6.dp
private val PillStart = 4.dp
private val PillEnd = 8.dp

/** The bar of the browser in its three shapes: browsing, selecting (frame 06), searching (frame 07). */
@Composable
internal fun BrowserTopBar(state: BrowserUiState, onAction: (BrowserAction) -> Unit) {
    val query = state.query
    when {
        state.selecting -> SelectionBar(state, onAction)
        query != null -> SearchBar(query, onAction)
        else -> BrowsingBar(state, onAction)
    }
}

@Composable
private fun BrowsingBar(state: BrowserUiState, onAction: (BrowserAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    // Nothing to search or extract until the list is there (frames 08 and 09 show the bare bar).
    val ready = state.loading == LoadState.Ready && state.problem == null && state.password == null
    ArchiveTopBar(state.title, state.badge, onBack = { onAction(BrowserAction.Up) }, modifier = Modifier.statusBarsPadding()) {
        if (!ready) return@ArchiveTopBar
        IconButton(onClick = { onAction(BrowserAction.OpenSearch) }) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.browser_search))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.width(MenuWidth), shape = MenuShape, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                MenuItem(R.string.browser_menu_extract_all) { menu = false; onAction(BrowserAction.ExtractAll) }
                MenuItem(R.string.browser_menu_sort) { menu = false; onAction(BrowserAction.ShowSort) }
                MenuItem(R.string.browser_menu_info) { menu = false; onAction(BrowserAction.ShowInfo) }
                MenuItem(R.string.browser_menu_share) { menu = false; onAction(BrowserAction.ShareArchive) }
            }
        }
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label), style = MaterialTheme.typography.bodyLarge) }, onClick = onClick, contentPadding = MenuItemPadding)
}

/** Deep blue up to the top of the screen, also in the light theme: the status bar area is part of it. */
@Composable
private fun SelectionBar(state: BrowserUiState, onAction: (BrowserAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides colors.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().background(colors.primaryContainer).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(horizontal = BarPadding), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(BrowserAction.ClearSelection) }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.selection_cancel))
                }
                Text(countText(R.plurals.selection_count, state.selectedCount), style = BarTitleStyle, maxLines = 1, modifier = Modifier.weight(1f).padding(start = TitleStart))
                IconButton(onClick = { onAction(BrowserAction.SelectAll) }) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.selection_all))
                }
                // Hidden, not greyed, when a folder is selected: a folder cannot be shared as it is.
                if (state.canShareSelection) {
                    IconButton(onClick = { onAction(BrowserAction.ShareSelection) }) {
                        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
                    }
                }
                Button(
                    onClick = { onAction(BrowserAction.ExtractSelection) },
                    enabled = state.selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.onPrimaryContainer, contentColor = colors.primaryContainer),
                    contentPadding = PillPadding,
                    modifier = Modifier.padding(start = PillStart, end = PillEnd),
                ) {
                    Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(PillIconSize))
                    Text(stringResource(R.string.selection_extract), modifier = Modifier.padding(start = PillIconSpacing))
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onAction: (BrowserAction) -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = BarMinHeight).padding(start = BarPadding, end = SearchEnd), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAction(BrowserAction.CloseSearch) }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        SearchField(
            query = query,
            onChange = { onAction(BrowserAction.SetQuery(it)) },
            placeholder = stringResource(R.string.browser_search_hint),
            modifier = Modifier.weight(1f).padding(start = TitleStart),
        )
    }
}
