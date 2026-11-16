package io.github.martindavinci.lunaris.archives.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.archive.EntryNames
import io.github.martindavinci.lunaris.archives.archive.EntryType
import io.github.martindavinci.lunaris.archives.browse.EntryKind
import io.github.martindavinci.lunaris.archives.browse.EntryKinds
import io.github.martindavinci.lunaris.archives.browse.FolderRow
import io.github.martindavinci.lunaris.archives.browse.SearchHit
import io.github.martindavinci.lunaris.archives.ui.EntryRow
import io.github.martindavinci.lunaris.archives.ui.PATH_SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.RESULT_SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.countText
import io.github.martindavinci.lunaris.archives.ui.entryDate
import io.github.martindavinci.lunaris.archives.ui.sizeText
import io.github.martindavinci.lunaris.archives.ui.theme.accentText
import java.util.Locale

private val ListBottomPadding = 96.dp
private val StripPaddingHorizontal = 16.dp
private val StripPaddingVertical = 8.dp
private val StripIconSize = 18.dp
private val StripSpacing = 10.dp
private val HeaderPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
private val MessagePadding = 32.dp

/**
 * Stable and unique among the rows of one list. Not the path: an archive may hold two entries of
 * the same name, or a file and a folder of the same name, and a LazyColumn with a repeated key throws.
 */
internal fun FolderRow.rowKey(): String = entry?.let { "e" + it.index } ?: ("d" + path.joinToString("/"))

@Composable
internal fun FolderList(rows: List<FolderRow>, state: BrowserUiState, listState: LazyListState, onAction: (BrowserAction) -> Unit) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ListBottomPadding)) {
        items(rows, key = { it.rowKey() }) { row ->
            Entry(row, AnnotatedString(EntryNames.display(row.name)), rowMeta(row), state, onAction)
        }
    }
}

/** Frame 07: hits from every folder, with where each one is. */
@Composable
internal fun SearchResults(state: BrowserUiState, onAction: (BrowserAction) -> Unit) {
    val query = state.query.orEmpty()
    if (query.isNotBlank() && state.hits.isEmpty()) {
        CenteredMessage(stringResource(R.string.search_no_match, query))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ListBottomPadding)) {
        if (state.hits.isNotEmpty()) {
            item(key = "header") {
                Text(countText(R.plurals.search_results, state.hits.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(HeaderPadding))
            }
        }
        items(state.hits, key = { it.row.rowKey() }) { hit ->
            val folder = hit.row.path.dropLast(1)
            val where = if (folder.isEmpty()) state.title else state.title + RESULT_SEPARATOR + folder.joinToString(PATH_SEPARATOR) { EntryNames.display(it) }
            Entry(hit.row, highlighted(hit), where, state, onAction)
        }
    }
}

@Composable
private fun Entry(row: FolderRow, name: AnnotatedString, meta: String, state: BrowserUiState, onAction: (BrowserAction) -> Unit) {
    val selecting = state.selecting
    EntryRow(
        name = name,
        meta = meta,
        kind = kindOf(row),
        locked = row.entry?.encrypted == true,
        selected = if (selecting) state.isSelected(row) else null,
        onClick = { onAction(if (selecting) BrowserAction.Toggle(row) else BrowserAction.OpenRow(row)) },
        onLongClick = if (selecting) null else { { onAction(BrowserAction.LongPress(row)) } },
        onMore = if (selecting) null else { { onAction(BrowserAction.ShowRowSheet(row)) } },
    )
}

private fun kindOf(row: FolderRow): EntryKind = if (row.isFolder) EntryKind.FOLDER else EntryKinds.of(row.name, EntryType.FILE)

/** "5 items" for a folder, "ZIP · 2.1 MB" for an archive inside the archive, "18 KB · 2 Nov 2026" for a file. */
@Composable
private fun rowMeta(row: FolderRow): String {
    if (row.isFolder) return countText(R.plurals.count_items, row.childCount)
    val size = sizeText(row.totalSize)
    if (kindOf(row) == EntryKind.ARCHIVE) {
        return stringResource(R.string.browser_nested_meta, row.name.substringAfterLast('.').uppercase(Locale.ROOT), size)
    }
    val modified = row.entry?.modifiedMillis ?: return size
    return size + SEPARATOR + entryDate(modified)
}

/** The part of the name that matched, in the accent colour. Only when showing the name does not change it. */
@Composable
private fun highlighted(hit: SearchHit): AnnotatedString {
    val name = hit.row.name
    val shown = EntryNames.display(name)
    val match = hit.match
    if (shown != name || match.first < 0 || match.last >= name.length) return AnnotatedString(shown)
    val style = SpanStyle(color = MaterialTheme.colorScheme.accentText, fontWeight = FontWeight.Medium)
    return buildAnnotatedString {
        append(name.substring(0, match.first))
        withStyle(style) { append(name.substring(match.first, match.last + 1)) }
        append(name.substring(match.last + 1))
    }
}

/** A quiet line under the breadcrumb: hidden unsafe names, the 200 000 cap, a partial listing. Never red. */
@Composable
internal fun InfoStrip(text: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = StripPaddingHorizontal, vertical = StripPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StripSpacing),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = color, modifier = Modifier.size(StripIconSize))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
internal fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(MessagePadding), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
