package io.github.martindavinci.lunaris.scanner.feature.scan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.scanner.R
import io.github.martindavinci.lunaris.scanner.feature.history.icon
import io.github.martindavinci.lunaris.scanner.feature.history.kind
import io.github.martindavinci.lunaris.scanner.feature.history.relativeDate
import io.github.martindavinci.lunaris.scanner.feature.result.ResultAction
import io.github.martindavinci.lunaris.scanner.feature.result.kindRes
import io.github.martindavinci.lunaris.scanner.feature.result.labelRes
import io.github.martindavinci.lunaris.scanner.model.ScannedCode

private val MaxWidth = 480.dp
private val CardPadding = 16.dp
private val CardShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val LineSpacing = 8.dp
private val IconBox = 44.dp
private val IconCorner = 12.dp
private val SmallIcon = 18.dp
private val ChipPadding = 8.dp
private val ChipInner = 12.dp
private val ChipInnerVertical = 6.dp
private const val SUMMARY_LINES = 2

/** What was just read, over the live camera, with the way to all of this round's codes above it. */
@Composable
internal fun CodePanel(
    state: ScanUiState,
    entry: SessionCode,
    onHeld: (Boolean) -> Unit,
    onDetails: (ScannedCode) -> Unit,
    onRun: (ResultAction) -> Unit,
    onOpenList: () -> Unit,
) {
    val card = remember(entry.code) { codeCard(entry.code) }
    val currentOnHeld by rememberUpdatedState(onHeld)
    Column(Modifier.widthIn(max = MaxWidth).fillMaxWidth()) {
        SessionChip(state.session.size, onOpenList)
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.heldByFinger { currentOnHeld(it) }.padding(CardPadding),
                verticalArrangement = Arrangement.spacedBy(LineSpacing),
            ) {
                PauseLine(state.readings, state.pauseRunning, state.cardHeld)
                CodeLine(card, entry.readAtMillis) { onDetails(card.code) }
                CardWarnings(card)
                CardButtons(card, onDetails, onRun)
            }
        }
    }
}

/** Sees a finger come down before the buttons inside do, and whatever becomes of it. */
private fun Modifier.heldByFinger(onHeld: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onHeld(true)
        try {
            waitForUpOrCancellation(PointerEventPass.Initial)
        } finally {
            onHeld(false)
        }
    }
}

@Composable
private fun SessionChip(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.padding(ChipPadding),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ChipInner, vertical = ChipInnerVertical),
            horizontalArrangement = Arrangement.spacedBy(ChipPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(SmallIcon))
            Text(pluralStringResource(R.plurals.scan_session_count, count, count), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(SmallIcon))
        }
    }
}

/** Runs out with the pause, and stays full under a finger. Its room stays when hidden, so that the card does not jump. */
@Composable
private fun PauseLine(readings: Int, running: Boolean, held: Boolean) {
    val left = remember(readings) { Animatable(1f) }
    LaunchedEffect(readings) { left.animateTo(0f, tween(READ_PAUSE_MILLIS.toInt(), easing = LinearEasing)) }
    val shown = if (running || held) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}
    Column(shown, verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
        LinearProgressIndicator(progress = { if (held) 1f else left.value }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.scan_card_pause), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CodeLine(card: CodeCard, readAtMillis: Long, onClick: () -> Unit) {
    val now = remember(readAtMillis) { System.currentTimeMillis() }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(IconBox).clip(RoundedCornerShape(IconCorner)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(card.content.kind().icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = card.summary.ifEmpty { stringResource(card.content.kindRes) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = SUMMARY_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(stringResource(card.content.kindRes), stringResource(card.code.format.labelRes), relativeDate(readAtMillis, now)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.scan_card_details))
    }
}
