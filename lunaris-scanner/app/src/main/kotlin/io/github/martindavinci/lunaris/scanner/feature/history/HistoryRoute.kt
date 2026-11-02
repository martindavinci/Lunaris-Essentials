package io.github.martindavinci.lunaris.scanner.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.model.CodeFormat
import io.github.martindavinci.lunaris.scanner.model.ScannedCode
import io.github.martindavinci.lunaris.scanner.ui.theme.LunarisScannerTheme

private val HeaderMinHeight = 56.dp
private val HeaderStartPadding = 16.dp
private val HeaderEndPadding = 4.dp

@Composable
fun HistoryRoute(onOpenEntry: () -> Unit) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        nowMillis = remember(state.allItems) { System.currentTimeMillis() },
        onAction = { action ->
            viewModel.onAction(action)
            if (action is HistoryAction.Open) onOpenEntry()
        },
    )
}

@Composable
private fun HistoryScreen(state: HistoryUiState, nowMillis: Long, onAction: (HistoryAction) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HistoryHeader(showClear = state.allItems.isNotEmpty(), onClear = { onAction(HistoryAction.RequestClear) })
        if (state.loaded) HistoryBody(state, nowMillis, onAction)
    }
    if (state.confirmingClear) {
        ClearHistoryDialog(
            onConfirm = { onAction(HistoryAction.ConfirmClear) },
            onDismiss = { onAction(HistoryAction.DismissClear) },
        )
    }
}

@Composable
private fun HistoryHeader(showClear: Boolean, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = HeaderMinHeight).padding(start = HeaderStartPadding, end = HeaderEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.nav_history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        if (showClear) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.history_clear_all))
            }
        }
    }
}

@Composable
private fun ClearHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.history_clear_title)) },
        text = { Text(stringResource(R.string.history_clear_message)) },
    )
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    val now = 1_800_000_000_000L
    val items = listOf(
        HistoryItem(2, ScannedCode("WIFI:S:Home;;", CodeFormat.QR_CODE), HistoryKind.WIFI, "Home", "Home", now - 300_000L),
        HistoryItem(1, ScannedCode("4006381333931", CodeFormat.EAN_13), HistoryKind.PRODUCT, "4006381333931", "4006381333931", now - 7_200_000L),
    )
    LunarisScannerTheme {
        HistoryScreen(HistoryUiState(loaded = true, allItems = items), nowMillis = now, onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryOffPreview() {
    LunarisScannerTheme {
        HistoryScreen(HistoryUiState(loaded = true, keepHistory = false), nowMillis = 0L, onAction = {})
    }
}
