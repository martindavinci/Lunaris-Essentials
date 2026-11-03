package io.github.martindavinci.lunaris.pdf.feature.recents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.pdf.R
import io.github.martindavinci.lunaris.pdf.recents.RecentEntry

private val BarHeight = 56.dp
private val BarElevation = 3.dp
private val RowPadding = 16.dp
private val RowSpacing = 16.dp
private val FabPadding = 16.dp
private val ListBottomPadding = 88.dp

/** Stateless Recents UI, the first screen of the app. */
@Composable
internal fun RecentsScreen(
    state: RecentsUiState,
    onAction: (RecentsAction) -> Unit,
    onOpenSettings: () -> Unit,
    onPickFile: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val goneMessage = stringResource(R.string.recents_entry_gone)
    LaunchedEffect(state.entryGone) {
        if (state.entryGone) {
            onAction(RecentsAction.MessageShown)
            snackbar.showSnackbar(goneMessage)
        }
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            RecentsTopBar(
                showClear = state.entries.isNotEmpty(),
                onClear = { confirmClear = true },
                onOpenSettings = onOpenSettings,
            )
            if (state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.recents_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = ListBottomPadding)) {
                    items(state.entries, key = { it.uri }) { entry ->
                        RecentRow(entry, onClick = { onAction(RecentsAction.OpenEntry(entry)) })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onPickFile,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
            text = { Text(stringResource(R.string.recents_open)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(FabPadding),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.recents_clear_title)) },
            text = { Text(stringResource(R.string.recents_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onAction(RecentsAction.ClearAll)
                }) { Text(stringResource(R.string.recents_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RecentsTopBar(showClear: Boolean, onClear: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(tonalElevation = BarElevation) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = BarHeight).padding(start = RowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (showClear) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.recents_clear_title))
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
    }
}

@Composable
private fun RecentRow(entry: RecentEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.recents_page, entry.page),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
