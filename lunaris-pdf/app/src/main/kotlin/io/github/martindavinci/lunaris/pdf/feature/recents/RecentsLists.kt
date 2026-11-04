package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry

/** The document in the card is in the list too, which keys its rows by uri. */
private const val RESUME_KEY = "resume"
private val ListBottomPadding = 96.dp
private val EmptyPadding = 32.dp
private val EmptySpacing = 16.dp
private val EmptyIconSize = 96.dp
private val EmptyIconShape = RoundedCornerShape(22.dp)
private val EmptyButtonHeight = 56.dp
private val EmptyButtonShape = RoundedCornerShape(16.dp)
private val EmptyButtonSpacing = 12.dp

/** With nothing to show, the one thing to do takes the whole screen. */
@Composable
internal fun RecentsEmpty(onPickFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EmptyPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EmptySpacing, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(R.drawable.lunaris_icon),
            contentDescription = null,
            modifier = Modifier.size(EmptyIconSize).clip(EmptyIconShape),
        )
        Text(
            text = stringResource(R.string.recents_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.recents_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onPickFile,
            shape = EmptyButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = EmptyButtonHeight),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EmptyButtonSpacing)) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Text(stringResource(R.string.recents_open))
            }
        }
    }
}

@Composable
internal fun RecentsList(
    state: RecentsUiState,
    thumbnail: suspend (String) -> ImageBitmap?,
    onAction: (RecentsAction) -> Unit,
    onMore: (RecentEntry) -> Unit,
) {
    val home = state.home
    val section: LazyListScope.(String, List<RecentEntry>) -> Unit = { title, entries ->
        item(key = title) { SectionTitle(title) }
        items(entries, key = { it.uri }) { entry ->
            SwipeToRemove(onRemove = { onAction(RecentsAction.RemoveEntry(entry)) }, modifier = Modifier.animateItem()) {
                RecentRow(entry, thumbnail, onClick = { onAction(RecentsAction.OpenEntry(entry)) }, onMore = { onMore(entry) })
            }
        }
    }
    val pinned = stringResource(R.string.recents_pinned)
    val all = stringResource(R.string.recents_all)
    val results = if (state.query.isNullOrBlank()) all else stringResource(R.string.recents_results)
    val nothing = home.results?.isEmpty() == true
    if (nothing) {
        Text(
            text = stringResource(R.string.recents_no_match, state.query.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(EmptyPadding),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = ListBottomPadding)) {
        home.results?.let { section(results, it) }
        if (home.pinned.isNotEmpty()) section(pinned, home.pinned)
        home.resume?.let { entry ->
            item(key = RESUME_KEY) {
                // The next document takes this place when one slides away, and must not arrive slid away too.
                key(entry.uri) {
                    SwipeToRemove(onRemove = { onAction(RecentsAction.RemoveEntry(entry)) }, modifier = Modifier.animateItem()) {
                        ResumeCard(entry, thumbnail, onClick = { onAction(RecentsAction.OpenEntry(entry)) })
                    }
                }
            }
        }
        if (home.all.isNotEmpty()) section(all, home.all)
    }
}
