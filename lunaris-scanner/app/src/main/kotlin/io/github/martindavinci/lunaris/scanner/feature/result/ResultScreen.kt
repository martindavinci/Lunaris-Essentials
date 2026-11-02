package io.github.martindavinci.lunaris.scanner.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.model.ScannedCode

private val ResultScreenPadding = 16.dp
internal val ResultSectionSpacing = 12.dp
internal val ResultLineSpacing = 4.dp
private val TopBarMinHeight = 56.dp

/** Insets were consumed by the app scaffold around the routes, hence the empty ones here. */
@Composable
internal fun ResultScreen(
    state: ResultUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onUiAction: (ResultUiAction) -> Unit,
    onRun: (ResultAction) -> Unit,
) {
    val code = state.code ?: return
    Scaffold(
        topBar = { ResultTopBar(state, code, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ResultScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ResultSectionSpacing),
        ) {
            UrlNoticeCard(state.urlWarnings, state.urlBlocked)
            ResultFieldsCard(
                fields = state.fields,
                secretRevealed = state.secretRevealed,
                onToggleSecret = { onUiAction(ResultUiAction.ToggleSecret) },
            )
            ResultActionButtons(state.actions, onRun)
            RawTextCard(code.text, hidden = state.rawTextHidden)
        }
    }
}

@Composable
private fun ResultTopBar(state: ResultUiState, code: ScannedCode, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TopBarMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Column {
            state.content?.let { Text(stringResource(it.kindRes), style = MaterialTheme.typography.titleLarge) }
            Text(
                text = stringResource(code.format.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
