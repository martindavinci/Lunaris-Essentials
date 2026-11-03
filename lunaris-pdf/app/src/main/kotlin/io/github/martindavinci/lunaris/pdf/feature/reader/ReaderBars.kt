package io.github.martindavinci.lunaris.pdf.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R

private val BarHeight = 56.dp
private val BarElevation = 3.dp
private val PagePadding = 16.dp

@Composable
internal fun ReaderTopBar(
    name: String,
    night: Boolean,
    onBack: () -> Unit,
    canPrint: Boolean,
    onAction: (ReaderAction) -> Unit,
    onGoToPage: () -> Unit,
    onPrint: () -> Unit,
    onSaveCopy: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val menuItem: @Composable (Int, () -> Unit) -> Unit = { label, action ->
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            onClick = {
                menuOpen = false
                action()
            },
        )
    }
    Surface(tonalElevation = BarElevation) {
        Row(Modifier.fillMaxWidth().heightIn(min = BarHeight), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onAction(ReaderAction.OpenSearch) }) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.reader_search))
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_more))
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menuItem(R.string.reader_go_to_page, onGoToPage)
                    menuItem(R.string.read_aloud) { onAction(ReaderAction.StartReadAloud) }
                    if (canPrint) menuItem(R.string.action_print, onPrint)
                    menuItem(R.string.action_share) { onAction(ReaderAction.Share) }
                    menuItem(R.string.action_save_copy, onSaveCopy)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reader_night_pages)) },
                        trailingIcon = { Checkbox(checked = night, onCheckedChange = null) },
                        onClick = {
                            menuOpen = false
                            onAction(ReaderAction.ToggleNight)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReaderBottomBar(
    page: Int,
    pages: Int,
    hasOutline: Boolean,
    onGoToPage: () -> Unit,
    onOutline: () -> Unit,
) {
    Surface(tonalElevation = BarElevation) {
        Row(Modifier.fillMaxWidth().heightIn(min = BarHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.reader_page_of, page, pages),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onGoToPage).padding(PagePadding),
            )
            Spacer(Modifier.weight(1f))
            if (hasOutline) {
                IconButton(onClick = onOutline) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.reader_outline))
                }
            }
        }
    }
}

@Composable
internal fun ReaderSearchBar(search: SearchState, onAction: (ReaderAction) -> Unit) {
    var query by rememberSaveable { mutableStateOf(search.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(tonalElevation = BarElevation) {
        Row(Modifier.fillMaxWidth().heightIn(min = BarHeight), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAction(ReaderAction.CloseSearch) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.reader_close_search))
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.reader_search)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onAction(ReaderAction.SubmitSearch(query)) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (search.query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.reader_search_count, search.current, search.total),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = PagePadding / 2),
                )
            }
            IconButton(onClick = { onAction(ReaderAction.FindPrevious) }) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.reader_find_previous))
            }
            IconButton(onClick = { onAction(ReaderAction.FindNext) }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.reader_find_next))
            }
        }
    }
}
