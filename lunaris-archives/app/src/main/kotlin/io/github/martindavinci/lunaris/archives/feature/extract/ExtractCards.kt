package io.github.martindavinci.lunaris.archives.feature.extract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.extract.ExtractionState
import io.github.martindavinci.lunaris.archives.extract.SkippedEntry
import io.github.martindavinci.lunaris.archives.safety.SkipReason
import io.github.martindavinci.lunaris.archives.ui.DeepButton
import io.github.martindavinci.lunaris.archives.ui.ProgressCard
import io.github.martindavinci.lunaris.archives.ui.SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.SoftErrorBlock
import io.github.martindavinci.lunaris.archives.ui.SoftOutlinedButton
import io.github.martindavinci.lunaris.archives.ui.ThickProgressBar
import io.github.martindavinci.lunaris.archives.ui.UNSAFE_PREVIEW_LINES
import io.github.martindavinci.lunaris.archives.ui.UnsafeEntriesBlock
import io.github.martindavinci.lunaris.archives.ui.countText
import io.github.martindavinci.lunaris.archives.ui.sizeText
import io.github.martindavinci.lunaris.archives.ui.theme.TabularNumbers
import java.text.NumberFormat

private val BarTop = 28.dp
private val CountTop = 12.dp
private val ButtonTop = 24.dp
private val BlockTop = 24.dp
private val BlockGap = 12.dp
private val HintPadding = 16.dp
private const val UNKNOWN = -1f

/** Frame 11. */
@Composable
internal fun ColumnScope.RunningCard(running: ExtractionState.Running, state: ExtractUiState, onAction: (ExtractAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val format = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val files = pluralStringResource(R.plurals.extract_files_of, running.totalFiles, format.format(running.doneFiles), format.format(running.totalFiles))
    val bytes = stringResource(R.string.extract_bytes_of, sizeText(running.doneBytes), sizeText(running.totalBytes))
    val fraction = when {
        running.totalBytes > 0 -> running.doneBytes.toFloat() / running.totalBytes
        running.totalFiles > 0 -> running.doneFiles.toFloat() / running.totalFiles
        else -> UNKNOWN
    }
    ProgressCard(Icons.Rounded.Unarchive, AnnotatedString(stringResource(R.string.extract_title, state.archiveName)), state.location?.let { stringResource(R.string.extract_to, it) }) {
        Spacer(Modifier.height(BarTop))
        ThickProgressBar(fraction, spoken = files)
        Spacer(Modifier.height(CountTop))
        Text(files + SEPARATOR + bytes, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
        Text(running.current, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        Spacer(Modifier.height(ButtonTop))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SoftOutlinedButton(stringResource(R.string.cancel), onClick = { onAction(ExtractAction.RequestStop) })
        }
    }
    Text(
        text = stringResource(R.string.extract_keep_open),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = HintPadding),
    )
}

/** While the user answers "this file is unusually large": the work waits, the card says so. */
@Composable
internal fun ColumnScope.PausedCard(state: ExtractUiState) {
    ProgressCard(Icons.Rounded.Unarchive, AnnotatedString(stringResource(R.string.extract_title, state.archiveName)), state.location?.let { stringResource(R.string.extract_to, it) }) {
        Spacer(Modifier.height(BarTop))
        ThickProgressBar(UNKNOWN, spoken = stringResource(R.string.extract_waiting))
    }
}

/** Frame 12: what was written, then what was not: unsafe names in the one red block, damaged ones below it. */
@Composable
internal fun ColumnScope.DoneCard(done: ExtractionState.Finished, state: ExtractUiState, onAction: (ExtractAction) -> Unit) {
    val whole = done.written == done.total && done.damaged.isEmpty() && !done.stoppedEarly
    val title = if (whole) {
        countText(R.plurals.extract_done, done.written)
    } else {
        val format = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
        pluralStringResource(R.plurals.extract_done_partial, done.total, format.format(done.written), format.format(done.total))
    }
    ProgressCard(Icons.Rounded.CheckCircle, AnnotatedString(title), (done.location ?: state.location)?.let { stringResource(R.string.extract_to, it) }) {
        if (done.skipped.isNotEmpty()) {
            Spacer(Modifier.height(BlockTop))
            val outside = done.skipped.all { it.reason == SkipReason.OUTSIDE_FOLDER }
            UnsafeEntriesBlock(
                title = countText(if (outside) R.plurals.extract_skipped else R.plurals.extract_skipped_unsafe, done.skipped.size),
                lines = skipReasons().let { reasons -> done.skipped.take(UNSAFE_PREVIEW_LINES).map(reasons::line) },
                onShowAll = { onAction(ExtractAction.ShowSkipped) },
            )
        }
        if (done.damaged.isNotEmpty()) {
            Spacer(Modifier.height(if (done.skipped.isEmpty()) BlockTop else BlockGap))
            SoftErrorBlock(countText(R.plurals.extract_damaged, done.damaged.size), done.damaged, onShowAll = { onAction(ExtractAction.ShowSkipped) })
        }
        Spacer(Modifier.height(ButtonTop))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DeepButton(stringResource(R.string.done), onClick = { onAction(ExtractAction.Done) })
        }
    }
}

/**
 * The original name of each skipped entry, with why when it is not "points outside the folder".
 * The words are read once; [line] is then plain string work, fit for a lazy list of 200 000 names.
 */
internal class SkipReasons(private val link: String, private val device: String, private val invalid: String, private val pattern: String) {
    fun line(entry: SkippedEntry): String = when (entry.reason) {
        SkipReason.OUTSIDE_FOLDER -> entry.displayPath
        SkipReason.LINK -> pattern.format(entry.displayPath, link)
        SkipReason.DEVICE_FILE -> pattern.format(entry.displayPath, device)
        SkipReason.INVALID_NAME -> pattern.format(entry.displayPath, invalid)
    }
}

@Composable
internal fun skipReasons() = SkipReasons(
    stringResource(R.string.skip_reason_link), stringResource(R.string.skip_reason_device),
    stringResource(R.string.skip_reason_invalid), stringResource(R.string.skip_line),
)
