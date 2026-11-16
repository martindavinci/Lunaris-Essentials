package io.github.martindavinci.lunaris.archives.feature.extract

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.extract.ConflictChoice
import io.github.martindavinci.lunaris.archives.extract.ExtractionState
import io.github.martindavinci.lunaris.archives.ui.ArchiveTopBar
import io.github.martindavinci.lunaris.archives.ui.BottomPanel

private val PanelPadding = 24.dp
private val PanelLineSpacing = 6.dp
private val PanelListBottom = 12.dp

/** What the screen adds to the runner's state. [replaceable] and [formatLabel] are additions to the plan's table. */
/** Frames 10, 11, 12: the conflicts, the work, the result. Stateless; the runner lives outside the screen. */
@Composable
internal fun ExtractScreen(state: ExtractUiState, onAction: (ExtractAction) -> Unit, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    val phase = state.state
    val working = phase is ExtractionState.Running || phase is ExtractionState.AskLarge
    val back = {
        onAction(
            when {
                working -> ExtractAction.RequestStop
                phase is ExtractionState.Conflicts -> ExtractAction.CancelConflicts
                else -> ExtractAction.Done
            },
        )
    }
    BackHandler(onBack = back)
    KeepScreenOn(working)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            ArchiveTopBar(state.archiveName, state.formatLabel, onBack = back)
            // Scrolls: with a large font or in landscape the card is taller than the screen.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                when (phase) {
                    is ExtractionState.Running -> RunningCard(phase, state, onAction)
                    is ExtractionState.AskLarge -> PausedCard(state)
                    is ExtractionState.Finished -> DoneCard(phase, state, onAction)
                    else -> Unit
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        if (state.showSkipped && phase is ExtractionState.Finished) {
            NamesPanel(skippedLines(phase.skipped) + phase.damaged, onDismiss = { onAction(ExtractAction.HideSkipped) })
        }
    }
    when (phase) {
        is ExtractionState.Conflicts -> phase.pending.getOrNull(phase.current)?.let { ConflictDialog(it, phase.pending.size, state, onAction) }
        is ExtractionState.AskLarge -> LargeFileDialog(onContinue = { onAction(ExtractAction.ContinueLarge) }, onStop = { onAction(ExtractAction.StopLarge) })
        is ExtractionState.Failed -> FailureDialog(phase.reason, onAction)
        else -> Unit
    }
    if (state.confirmStop && working) {
        StopDialog(onKeepGoing = { onAction(ExtractAction.KeepGoing) }, onStop = { onAction(ExtractAction.Stop) })
    }
}

/** No service and no notification keep this work alive: the screen staying on does. */
@Composable
internal fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** Every name that was not written, in full. */
@Composable
private fun NamesPanel(lines: List<String>, onDismiss: () -> Unit) {
    BottomPanel(onDismiss = onDismiss) {
        Column(
            Modifier.padding(start = PanelPadding, end = PanelPadding, bottom = PanelListBottom).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PanelLineSpacing),
        ) {
            Text(stringResource(R.string.extract_not_written), style = MaterialTheme.typography.titleMedium)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        }
    }
}
