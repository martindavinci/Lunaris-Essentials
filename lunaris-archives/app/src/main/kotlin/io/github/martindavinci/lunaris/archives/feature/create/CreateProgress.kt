package io.github.martindavinci.lunaris.archives.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.martindavinci.lunaris.archives.R
import io.github.martindavinci.lunaris.archives.create.ArchiveNameRules
import io.github.martindavinci.lunaris.archives.create.CreateState
import io.github.martindavinci.lunaris.archives.ui.AccentTextButton
import io.github.martindavinci.lunaris.archives.ui.DeepButton
import io.github.martindavinci.lunaris.archives.ui.ProgressCard
import io.github.martindavinci.lunaris.archives.ui.SEPARATOR
import io.github.martindavinci.lunaris.archives.ui.SoftErrorBlock
import io.github.martindavinci.lunaris.archives.ui.SoftOutlinedButton
import io.github.martindavinci.lunaris.archives.ui.ThickProgressBar
import io.github.martindavinci.lunaris.archives.ui.countText
import io.github.martindavinci.lunaris.archives.ui.sizeText
import io.github.martindavinci.lunaris.archives.ui.theme.TabularNumbers
import java.text.NumberFormat

private val BarTop = 28.dp
private val CountTop = 12.dp
private val ButtonTop = 24.dp
private val DoneButtonsTop = 32.dp
private val ButtonGap = 8.dp
private const val UNKNOWN = -1f

/** Frame "15 Creating". */
@Composable
internal fun ColumnScope.CreatingCard(running: CreateState.Running, name: String, onAction: (CreateAction) -> Unit) {
    val format = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val adding = pluralStringResource(R.plurals.create_adding, running.total, format.format(running.added), format.format(running.total))
    val fraction = when {
        running.totalBytes > 0 -> running.doneBytes.toFloat() / running.totalBytes
        running.total > 0 -> running.added.toFloat() / running.total
        else -> UNKNOWN
    }
    ProgressCard(Icons.Rounded.FolderZip, AnnotatedString(stringResource(R.string.create_creating, name)), subtitle = null) {
        Spacer(Modifier.height(BarTop))
        ThickProgressBar(fraction, spoken = adding)
        Spacer(Modifier.height(CountTop))
        Text(adding, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
        Text(running.current, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        Spacer(Modifier.height(ButtonTop))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SoftOutlinedButton(stringResource(R.string.cancel), onClick = { onAction(CreateAction.RequestStop) })
        }
    }
}

/** Frame "15 Created": the name with its size, where it is, and what to do with it now. */
@Composable
internal fun ColumnScope.CreatedCard(done: CreateState.Finished, onAction: (CreateAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val created = stringResource(R.string.create_done, done.name)
    val size = sizeText(done.sizeBytes)
    val title = buildAnnotatedString {
        append(created)
        withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(SEPARATOR + size) }
    }
    ProgressCard(Icons.Rounded.CheckCircle, title, done.location?.let { stringResource(R.string.create_in, it) }) {
        if (done.skipped.isNotEmpty()) {
            Spacer(Modifier.height(ButtonTop))
            // Files without content of their own (a document that lives only in a cloud app, for one).
            SoftErrorBlock(countText(R.plurals.create_skipped, done.skipped.size), done.skipped, onShowAll = {})
        }
        Spacer(Modifier.height(DoneButtonsTop))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGap, Alignment.End)) {
            SoftOutlinedButton(stringResource(R.string.action_share), onClick = { onAction(CreateAction.Share) }, icon = Icons.Rounded.Share)
            DeepButton(stringResource(R.string.open), onClick = { onAction(CreateAction.OpenCreated) })
        }
    }
}

@Composable
internal fun CreateStopDialog(onKeepGoing: () -> Unit, onStop: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text(stringResource(R.string.create_stop_title)) },
        text = { Text(stringResource(R.string.create_stop_body)) },
        dismissButton = { AccentTextButton(stringResource(R.string.extract_keep_going), onKeepGoing) },
        confirmButton = { AccentTextButton(stringResource(R.string.extract_stop), onStop) },
    )
}
