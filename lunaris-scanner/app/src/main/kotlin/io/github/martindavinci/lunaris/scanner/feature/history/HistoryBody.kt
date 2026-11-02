package io.github.martindavinci.lunaris.scanner.feature.history

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R

private val BodyHorizontalPadding = 16.dp
private val BodyBottomPadding = 8.dp
private val BannerPadding = 12.dp
private val MessagePadding = 32.dp
private val MessageSpacing = 12.dp

@Composable
internal fun HistoryBody(state: HistoryUiState, nowMillis: Long, onAction: (HistoryAction) -> Unit) {
    when {
        state.allItems.isNotEmpty() -> {
            if (!state.keepHistory) HistoryOffBanner()
            SearchField(state.query, onQueryChange = { onAction(HistoryAction.QueryChanged(it)) })
            HistoryEntries(state, nowMillis, onAction)
        }
        state.keepHistory -> HistoryMessage(Icons.Filled.History, R.string.history_empty_title, R.string.history_empty_body)
        else -> HistoryMessage(Icons.Filled.HistoryToggleOff, R.string.history_off_title, R.string.history_off_body)
    }
}

@Composable
private fun HistoryEntries(state: HistoryUiState, nowMillis: Long, onAction: (HistoryAction) -> Unit) {
    if (state.visibleItems.isEmpty()) {
        HistoryMessage(Icons.Filled.SearchOff, R.string.history_no_results, body = null)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.visibleItems, key = { it.id }) { item ->
            HistoryRow(
                item = item,
                nowMillis = nowMillis,
                onOpen = { onAction(HistoryAction.Open(item)) },
                onDelete = { onAction(HistoryAction.Delete(item.id)) },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(start = BodyHorizontalPadding, end = BodyHorizontalPadding, bottom = BodyBottomPadding),
        placeholder = { Text(stringResource(R.string.history_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.history_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun HistoryOffBanner() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(start = BodyHorizontalPadding, end = BodyHorizontalPadding, bottom = BodyBottomPadding),
    ) {
        Text(
            text = stringResource(R.string.history_off_body),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(BannerPadding),
        )
    }
}

@Composable
private fun HistoryMessage(icon: ImageVector, @StringRes title: Int, @StringRes body: Int?) {
    Column(
        Modifier.fillMaxSize().padding(MessagePadding),
        verticalArrangement = Arrangement.spacedBy(MessageSpacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
